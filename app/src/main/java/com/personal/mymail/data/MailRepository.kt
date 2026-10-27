package com.personal.mymail.data

import android.content.Context
import com.personal.mymail.mail.MailEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MailRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val secrets = Secrets(context)

    val accounts: Flow<List<Account>> = db.accounts().observeAll()

    fun messages(accountId: Long?): Flow<List<MessageRow>> =
        if (accountId == null) db.messages().observeAll()
        else db.messages().observeForAccount(accountId)

    suspend fun getAccount(id: Long): Account? = db.accounts().get(id)

    suspend fun getMessage(id: Long): MailMessage? = db.messages().get(id)

    suspend fun markRead(id: Long) = db.messages().markRead(id)

    fun hasPassword(accountId: Long): Boolean = secrets.getPassword(accountId).isNotEmpty()

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
        db.messages().deleteForAccount(account.id)
        db.accounts().delete(account)
        secrets.remove(account.id)
    }

    /** Downloads new messages for one account. Returns how many were new. */
    suspend fun refresh(account: Account): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val known = db.messages().knownUids(account.id).toSet()
            val fresh = MailEngine.fetchInbox(account, secrets.getPassword(account.id), known)
            db.messages().insertAll(fresh)
            fresh.size
        }
    }

    /** Refreshes every account. Returns error text keyed by account email for the ones that failed. */
    suspend fun refreshAll(): Map<String, String> {
        val errors = LinkedHashMap<String, String>()
        for (account in withContext(Dispatchers.IO) { db.accounts().getAll() }) {
            refresh(account).onFailure {
                errors[account.email] = it.message ?: it.javaClass.simpleName
            }
        }
        return errors
    }

    suspend fun send(account: Account, to: String, subject: String, body: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                MailEngine.send(account, secrets.getPassword(account.id), to, subject, body)
            }
        }
}
