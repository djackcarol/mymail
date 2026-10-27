package com.personal.mymail.mail

import android.text.Html
import com.personal.mymail.data.Account
import com.personal.mymail.data.AttachmentInfo
import com.personal.mymail.data.MailMessage
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.pop3.POP3Folder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.mail.Address
import javax.mail.Authenticator
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Store
import javax.mail.Transport
import javax.mail.UIDFolder
import javax.mail.internet.ContentType
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility

/** A file the user is attaching to an outgoing message. */
data class OutAttachment(
    val file: File,
    val name: String,
    val mime: String,
    val size: Long
)

/** Everything needed to send one message. Address fields are comma-separated. */
data class OutgoingMail(
    val to: String,
    val cc: String,
    val bcc: String,
    val subject: String,
    val body: String,
    val attachments: List<OutAttachment>,
    /** Message-ID of the message being replied to, if any. */
    val inReplyTo: String?
)

/** A folder found on the server. */
data class FolderInfo(val fullName: String, val displayName: String, val role: String)

/** A downloaded message plus its attachment list (messageId is filled in when saved). */
class FetchedMessage(val message: MailMessage, val attachments: List<AttachmentInfo>)

/**
 * Talks to mail servers. All functions block, so call them from a background dispatcher.
 */
object MailEngine {

    private const val CONNECT_TIMEOUT_MS = "15000"
    private const val READ_TIMEOUT_MS = "30000"

    init {
        // Encode non-ASCII attachment file names correctly when sending.
        System.setProperty("mail.mime.encodefilename", "true")
        System.setProperty("mail.mime.decodefilename", "true")
    }

    // ------------------------------------------------------------------
    // Connecting
    // ------------------------------------------------------------------

    private fun protocolName(a: Account): String = when {
        a.protocol == "IMAP" && a.incomingSecurity == "SSL" -> "imaps"
        a.protocol == "IMAP" -> "imap"
        a.incomingSecurity == "SSL" -> "pop3s"
        else -> "pop3"
    }

    /** Opens a connection to the incoming server. The caller must close it. */
    fun openStore(a: Account, password: String): Store {
        val proto = protocolName(a)
        val props = Properties().apply {
            put("mail.$proto.connectiontimeout", CONNECT_TIMEOUT_MS)
            put("mail.$proto.timeout", READ_TIMEOUT_MS)
            if (a.incomingSecurity == "STARTTLS") {
                put("mail.$proto.starttls.enable", "true")
                put("mail.$proto.starttls.required", "true")
            }
        }
        val store = Session.getInstance(props).getStore(proto)
        store.connect(a.incomingHost, a.incomingPort, a.username, password)
        return store
    }

    /** Throws if the server cannot be reached or the login is rejected. */
    fun testIncoming(a: Account, password: String) {
        openStore(a, password).close()
    }

    // ------------------------------------------------------------------
    // Folders
    // ------------------------------------------------------------------

    fun listFolders(store: Store, a: Account): List<FolderInfo> {
        val inbox = FolderInfo("INBOX", "Inbox", "INBOX")
        if (a.protocol != "IMAP") return listOf(inbox)

        val result = ArrayList<FolderInfo>()
        for (f in store.defaultFolder.list("*")) {
            if ((f.type and Folder.HOLDS_MESSAGES) == 0) continue
            val attributes = (f as? IMAPFolder)?.attributes?.toList().orEmpty()
            val full = if (f.fullName.equals("INBOX", ignoreCase = true)) "INBOX" else f.fullName
            val role = roleOf(full, f.name, attributes)
            result += FolderInfo(full, if (role == "INBOX") "Inbox" else f.name, role)
        }
        if (result.none { it.role == "INBOX" }) result.add(0, inbox)
        return result
    }

    private fun roleOf(fullName: String, name: String, attributes: List<String>): String {
        val attrs = attributes.map { it.lowercase() }
        if (fullName == "INBOX") return "INBOX"
        when {
            "\\sent" in attrs -> return "SENT"
            "\\drafts" in attrs -> return "DRAFTS"
            "\\trash" in attrs -> return "TRASH"
            "\\junk" in attrs -> return "JUNK"
            "\\archive" in attrs || "\\all" in attrs -> return "ARCHIVE"
        }
        val n = name.lowercase()
        return when {
            n.startsWith("sent") -> "SENT"
            n.startsWith("draft") -> "DRAFTS"
            n == "trash" || n.startsWith("deleted") -> "TRASH"
            n == "junk" || n == "spam" || n.startsWith("bulk") -> "JUNK"
            n.startsWith("archive") -> "ARCHIVE"
            else -> "OTHER"
        }
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /**
     * Downloads the newest [limit] messages from [folderName], skipping any whose
     * UID is already in [known]. Messages are returned newest first.
     */
    fun fetchFolder(
        store: Store,
        folderName: String,
        accountId: Long,
        known: Set<String>,
        limit: Int = 40
    ): List<FetchedMessage> {
        val folder = store.getFolder(folderName)
        folder.open(Folder.READ_ONLY)
        val result = ArrayList<FetchedMessage>()
        try {
            val count = folder.messageCount
            if (count > 0) {
                val start = maxOf(1, count - limit + 1)
                val messages = folder.getMessages(start, count)

                val profile = FetchProfile()
                profile.add(FetchProfile.Item.ENVELOPE)
                if (folder is IMAPFolder) profile.add(UIDFolder.FetchProfileItem.UID)
                folder.fetch(messages, profile)

                for (m in messages.reversed()) {
                    val uid = uidOf(folder, m) ?: continue
                    if (uid in known) continue
                    result += toFetched(accountId, folderName, uid, m)
                }
            }
        } finally {
            try {
                folder.close(false)
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun uidOf(folder: Folder, m: Message): String? = when (folder) {
        is IMAPFolder -> folder.getUID(m).toString()
        is POP3Folder -> folder.getUID(m)
        else -> null
    }

    private fun addressList(addresses: Array<out Address>?): String =
        addresses?.joinToString(", ") { (it as? InternetAddress)?.toUnicodeString() ?: it.toString() }
            ?: ""

    private fun toFetched(accountId: Long, folderName: String, uid: String, m: Message): FetchedMessage {
        val from = m.from?.firstOrNull() as? InternetAddress
        val fromAddress = from?.address ?: ""
        val fromName = from?.personal?.takeIf { it.isNotBlank() } ?: fromAddress
        val body = try {
            extractBody(m)
        } catch (e: Exception) {
            "(Could not read this message: ${e.message})"
        }
        val attachmentParts = ArrayList<Part>()
        try {
            walkAttachments(m, attachmentParts)
        } catch (_: Exception) {
        }
        val attachments = attachmentParts.mapIndexed { index, part ->
            AttachmentInfo(
                messageId = 0,
                partIndex = index,
                name = attachmentName(part, index),
                mimeType = mimeOf(part),
                size = part.size.toLong().coerceAtLeast(0L)
            )
        }
        val message = MailMessage(
            accountId = accountId,
            folder = folderName,
            uid = uid,
            fromName = fromName,
            fromAddress = fromAddress,
            toAddresses = addressList(m.getRecipients(Message.RecipientType.TO)),
            ccAddresses = addressList(m.getRecipients(Message.RecipientType.CC)),
            messageIdHeader = (m as? MimeMessage)?.messageID ?: "",
            subject = m.subject?.takeIf { it.isNotBlank() } ?: "(no subject)",
            date = m.sentDate?.time ?: m.receivedDate?.time ?: 0L,
            snippet = body.replace(Regex("\\s+"), " ").trim().take(140),
            body = body,
            hasAttachments = attachments.isNotEmpty()
        )
        return FetchedMessage(message, attachments)
    }

    private class Texts {
        var plain: String? = null
        var html: String? = null
    }

    private fun extractBody(part: Part): String {
        val texts = Texts()
        collect(part, texts)
        val plain = texts.plain?.trim()
        if (!plain.isNullOrEmpty()) return plain
        val html = texts.html
        if (!html.isNullOrBlank()) return htmlToText(html)
        return "(no text content)"
    }

    private fun collect(part: Part, out: Texts) {
        if (Part.ATTACHMENT.equals(part.disposition, ignoreCase = true)) return
        when {
            part.isMimeType("text/plain") -> {
                if (out.plain == null) out.plain = part.content as? String
            }
            part.isMimeType("text/html") -> {
                if (out.html == null) out.html = part.content as? String
            }
            part.isMimeType("multipart/*") -> {
                val mp = part.content as Multipart
                for (i in 0 until mp.count) collect(mp.getBodyPart(i), out)
            }
        }
    }

    private fun htmlToText(html: String): String {
        val cleaned = html.replace(Regex("(?is)<(style|script|head)[^>]*>.*?</\\1>"), "")
        return Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    // ------------------------------------------------------------------
    // Attachments
    // ------------------------------------------------------------------

    /** Collects attachment parts in a stable order (the same order is used when downloading). */
    private fun walkAttachments(part: Part, out: MutableList<Part>) {
        if (part.isMimeType("multipart/*")) {
            val mp = part.content as Multipart
            for (i in 0 until mp.count) walkAttachments(mp.getBodyPart(i), out)
            return
        }
        val disposition = part.disposition
        val isAttachment = Part.ATTACHMENT.equals(disposition, ignoreCase = true)
        val hasName = !part.fileName.isNullOrBlank()
        val inlineImage = Part.INLINE.equals(disposition, ignoreCase = true) &&
            part.isMimeType("image/*")
        if ((isAttachment || hasName) && !inlineImage) out += part
    }

    private fun attachmentName(part: Part, index: Int): String {
        val raw = part.fileName
        if (raw.isNullOrBlank()) return "attachment-${index + 1}"
        return try {
            MimeUtility.decodeText(raw)
        } catch (_: Exception) {
            raw
        }
    }

    private fun mimeOf(part: Part): String = try {
        ContentType(part.contentType).baseType?.lowercase() ?: "application/octet-stream"
    } catch (_: Exception) {
        "application/octet-stream"
    }

    private fun findByUid(folder: Folder, uid: String): Message? = when (folder) {
        is IMAPFolder -> folder.getMessageByUID(uid.toLong())
        is POP3Folder -> folder.messages.firstOrNull { folder.getUID(it) == uid }
        else -> null
    }

    /** Downloads one attachment of one message into [dest]. */
    fun downloadAttachment(store: Store, folderName: String, uid: String, partIndex: Int, dest: File) {
        val folder = store.getFolder(folderName)
        folder.open(Folder.READ_ONLY)
        try {
            val message = findByUid(folder, uid)
                ?: throw IOException("That message is no longer on the server.")
            val parts = ArrayList<Part>()
            walkAttachments(message, parts)
            val part = parts.getOrNull(partIndex)
                ?: throw IOException("Attachment not found in the message.")
            try {
                part.inputStream.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                dest.delete()
                throw e
            }
        } finally {
            try {
                folder.close(false)
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    private fun parseAddresses(text: String): Array<InternetAddress> =
        InternetAddress.parse(text.replace(';', ','), false)

    private class FileSource(private val attachment: OutAttachment) : DataSource {
        override fun getInputStream(): InputStream = attachment.file.inputStream()
        override fun getOutputStream(): OutputStream = throw IOException("Read-only")
        override fun getContentType(): String = attachment.mime
        override fun getName(): String = attachment.name
    }

    /** Sends a message through the account's SMTP server and returns it (for copying to Sent). */
    fun send(a: Account, password: String, mail: OutgoingMail): MimeMessage {
        val props = Properties().apply {
            put("mail.smtp.host", a.smtpHost)
            put("mail.smtp.port", a.smtpPort.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", CONNECT_TIMEOUT_MS)
            put("mail.smtp.timeout", READ_TIMEOUT_MS)
            when (a.smtpSecurity) {
                "SSL" -> put("mail.smtp.ssl.enable", "true")
                "STARTTLS" -> {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                }
            }
        }
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(a.username, password)
        })

        val message = MimeMessage(session)
        message.setFrom(InternetAddress(a.email, a.displayName))
        message.setRecipients(Message.RecipientType.TO, parseAddresses(mail.to))
        if (mail.cc.isNotBlank()) {
            message.setRecipients(Message.RecipientType.CC, parseAddresses(mail.cc))
        }
        if (mail.bcc.isNotBlank()) {
            message.setRecipients(Message.RecipientType.BCC, parseAddresses(mail.bcc))
        }
        message.setSubject(mail.subject, "UTF-8")
        message.setSentDate(Date())
        if (!mail.inReplyTo.isNullOrBlank()) {
            message.setHeader("In-Reply-To", mail.inReplyTo)
            message.setHeader("References", mail.inReplyTo)
        }

        if (mail.attachments.isEmpty()) {
            message.setText(mail.body, "UTF-8")
        } else {
            val multipart = MimeMultipart("mixed")
            val textPart = MimeBodyPart()
            textPart.setText(mail.body, "UTF-8")
            multipart.addBodyPart(textPart)
            for (attachment in mail.attachments) {
                val part = MimeBodyPart()
                part.dataHandler = DataHandler(FileSource(attachment))
                part.fileName = attachment.name
                multipart.addBodyPart(part)
            }
            message.setContent(multipart)
        }
        message.saveChanges()
        Transport.send(message)
        return message
    }

    /** Saves a copy of a sent message into an IMAP folder (normally "Sent"). */
    fun appendToFolder(store: Store, fullName: String, message: MimeMessage) {
        message.setFlag(Flags.Flag.SEEN, true)
        store.getFolder(fullName).appendMessages(arrayOf<Message>(message))
    }
}
