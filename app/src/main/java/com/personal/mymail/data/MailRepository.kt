package com.personal.mymail.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.personal.mymail.mail.FetchedMessage
import com.personal.mymail.mail.MailEngine
import com.personal.mymail.mail.OutAttachment
import com.personal.mymail.mail.OutgoingMail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** What one account's refresh found. */
data class RefreshOutcome(val newMail: List<NewMail>, val error: String?)

/** What refreshing every account found. */
data class RefreshAllResult(val errors: Map<String, String>, val newMail: List<NewMail>)

class MailRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val secrets = Secrets(appContext)

    // ------------------------------------------------------------------
    // Reading from the local database
    // ------------------------------------------------------------------

    val accounts: Flow<List<Account>> = db.accounts().observeAll()

    fun inboxes(): Flow<List<MessageRow>> = db.messages().observeInboxes()

    fun folderMessages(accountId: Long, folder: String): Flow<List<MessageRow>> =
        db.messages().observeFolder(accountId, folder)

    fun enabledFolders(accountId: Long): Flow<List<MailFolder>> =
        db.folders().observeEnabled(accountId)

    fun allFolders(accountId: Long): Flow<List<MailFolder>> = db.folders().observeAll(accountId)

    suspend fun setFolderEnabled(folderId: Long, enabled: Boolean) =
        db.folders().setEnabled(folderId, enabled)

    suspend fun getAccount(id: Long): Account? = db.accounts().get(id)

    suspend fun getMessage(id: Long): MailMessage? = db.messages().get(id)

    suspend fun attachments(messageId: Long): List<AttachmentInfo> =
        db.attachments().forMessage(messageId)

    suspend fun markRead(id: Long) = db.messages().markRead(id)

    /** All of the user's own addresses, lower-cased (used to build "reply all"). */
    suspend fun ownAddresses(): Set<String> =
        db.accounts().getAll().map { it.email.trim().lowercase() }.toSet()

    // ------------------------------------------------------------------
    // Accounts
    // ------------------------------------------------------------------

    /** Checks the login against the incoming server. A blank password on an existing account uses the stored one. */
    suspend fun testConnection(account: Account, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pw = password.ifEmpty { secrets.getPassword(account.id) }
                MailEngine.testIncoming(account, pw)
            }
        }

    /** Inserts or updates the account. A blank password on an existing account keeps the old one. */
    suspend fun saveAccount(account: Account, password: String): Long =
        withContext(Dispatchers.IO) {
            val id = if (account.id == 0L) {
                db.accounts().insert(account)
            } else {
                db.accounts().update(account)
                account.id
            }
            if (password.isNotEmpty()) secrets.setPassword(id, password)
            id
        }

    suspend fun deleteAccount(account: Account) = withContext(Dispatchers.IO) {
        db.attachments().deleteForAccount(account.id)
        db.messages().deleteForAccount(account.id)
        db.folders().deleteForAccount(account.id)
        db.accounts().delete(account)
        secrets.remove(account.id)
    }

    // ------------------------------------------------------------------
    // Syncing
    // ------------------------------------------------------------------

    /** Saves downloaded messages and their attachment lists. Returns the ones that were actually new. */
    private suspend fun saveFetched(fetched: List<FetchedMessage>): List<Pair<MailMessage, Long>> {
        val inserted = ArrayList<Pair<MailMessage, Long>>()
        if (fetched.isEmpty()) return inserted
        db.withTransaction {
            val ids = db.messages().insertAll(fetched.map { it.message })
            fetched.forEachIndexed { index, item ->
                val id = ids[index]
                if (id != -1L) {
                    inserted += item.message to id
                    if (item.attachments.isNotEmpty()) {
                        db.attachments().insertAll(item.attachments.map { it.copy(messageId = id) })
                    }
                }
            }
        }
        return inserted
    }

    /**
     * Downloads new messages for every enabled folder of one account.
     * A folder that fails does not stop the others; the first error is reported.
     */
    suspend fun refresh(account: Account): RefreshOutcome = withContext(Dispatchers.IO) {
        val newMail = ArrayList<NewMail>()
        var firstError: String? = null
        try {
            val store = MailEngine.openStore(account, secrets.getPassword(account.id))
            try {
                // Make sure we know about the folders on the server.
                val serverFolders = MailEngine.listFolders(store, account)
                val existing = db.folders().getAll(account.id).map { it.fullName }.toSet()
                val created = serverFolders.filter { it.fullName !in existing }.map {
                    MailFolder(
                        accountId = account.id,
                        fullName = it.fullName,
                        displayName = it.displayName,
                        role = it.role,
                        syncEnabled = it.role == "INBOX" || it.role == "SENT"
                    )
                }
                if (created.isNotEmpty()) db.folders().insertAll(created)

                val serverNames = serverFolders.map { it.fullName }.toSet()
                val toSync = db.folders().getAll(account.id)
                    .filter { it.syncEnabled && it.fullName in serverNames }

                for (folder in toSync) {
                    try {
                        val known = db.messages().knownUids(account.id, folder.fullName).toSet()
                        val fetched =
                            MailEngine.fetchFolder(store, folder.fullName, account.id, known)
                        val inserted = saveFetched(fetched)
                        // Only announce mail that arrived after the first sync of the inbox.
                        if (folder.role == "INBOX" && folder.lastSync > 0L) {
                            inserted.forEach { (message, id) ->
                                newMail += NewMail(id, account.email, message.fromName, message.subject)
                            }
                        }
                        db.folders().setLastSync(folder.id, System.currentTimeMillis())
                    } catch (e: Exception) {
                        if (firstError == null) {
                            firstError = "${folder.displayName}: ${e.message ?: e.javaClass.simpleName}"
                        }
                    }
                }
            } finally {
                try {
                    store.close()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            firstError = e.message ?: e.javaClass.simpleName
        }
        RefreshOutcome(newMail, firstError)
    }

    /** Refreshes every account. */
    suspend fun refreshAll(): RefreshAllResult {
        val errors = LinkedHashMap<String, String>()
        val newMail = ArrayList<NewMail>()
        for (account in withContext(Dispatchers.IO) { db.accounts().getAll() }) {
            val outcome = refresh(account)
            newMail += outcome.newMail
            outcome.error?.let { errors[account.email] = it }
        }
        return RefreshAllResult(errors, newMail)
    }

    // ------------------------------------------------------------------
    // Attachments
    // ------------------------------------------------------------------

    private fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "attachment" }

    /** Downloads a received attachment into the cache (or returns the cached copy). */
    suspend fun downloadAttachment(attachment: AttachmentInfo): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val message = db.messages().get(attachment.messageId)
                    ?: throw IOException("Message not found.")
                val account = db.accounts().get(message.accountId)
                    ?: throw IOException("Account not found.")
                val dir = File(
                    appContext.cacheDir,
                    "attachments/${attachment.messageId}-${attachment.partIndex}"
                )
                dir.mkdirs()
                val dest = File(dir, safeName(attachment.name))
                if (dest.exists() && dest.length() > 0L) return@runCatching dest

                val store = MailEngine.openStore(account, secrets.getPassword(account.id))
                try {
                    MailEngine.downloadAttachment(
                        store, message.folder, message.uid, attachment.partIndex, dest
                    )
                } finally {
                    try {
                        store.close()
                    } catch (_: Exception) {
                    }
                }
                dest
            }
        }

    /** Copies a file the user picked into the app's cache so it can be attached. */
    suspend fun importAttachment(uri: Uri): OutAttachment = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        var name = "attachment"
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0) name = cursor.getString(column) ?: name
            }
        }
        val dir = File(appContext.cacheDir, "outgoing/${UUID.randomUUID()}")
        dir.mkdirs()
        val file = File(dir, safeName(name))
        val input = resolver.openInputStream(uri) ?: throw IOException("Cannot read that file.")
        input.use { stream -> file.outputStream().use { stream.copyTo(it) } }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        OutAttachment(file, name, mime, file.length())
    }

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    suspend fun send(account: Account, mail: OutgoingMail): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val password = secrets.getPassword(account.id)
                val message = MailEngine.send(account, password, mail)

                // Best effort: keep a copy in the Sent folder. Gmail does this by itself.
                try {
                    val sent = db.folders().getAll(account.id).firstOrNull { it.role == "SENT" }
                    val isGmail = account.smtpHost.contains("gmail", ignoreCase = true)
                    if (account.protocol == "IMAP" && sent != null && !isGmail) {
                        val store = MailEngine.openStore(account, password)
                        try {
                            MailEngine.appendToFolder(store, sent.fullName, message)
                        } finally {
                            try {
                                store.close()
                            } catch (_: Exception) {
                            }
                        }
                    }
                } catch (_: Exception) {
                }
                Unit
            }
        }
}
