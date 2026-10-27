package com.personal.mymail.mail

import android.text.Html
import com.personal.mymail.data.Account
import com.personal.mymail.data.MailMessage
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.pop3.POP3Folder
import java.util.Date
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Store
import javax.mail.Transport
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Talks to mail servers. All functions block, so call them from a background dispatcher.
 */
object MailEngine {

    private const val CONNECT_TIMEOUT_MS = "15000"
    private const val READ_TIMEOUT_MS = "30000"

    private fun protocolName(a: Account): String = when {
        a.protocol == "IMAP" && a.incomingSecurity == "SSL" -> "imaps"
        a.protocol == "IMAP" -> "imap"
        a.incomingSecurity == "SSL" -> "pop3s"
        else -> "pop3"
    }

    private fun openStore(a: Account, password: String): Store {
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

    /**
     * Downloads the newest [limit] messages from the inbox, skipping any whose
     * UID is already in [known]. Messages are returned newest first.
     */
    fun fetchInbox(a: Account, password: String, known: Set<String>, limit: Int = 40): List<MailMessage> {
        val store = openStore(a, password)
        try {
            val folder = store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            try {
                val count = folder.messageCount
                if (count <= 0) return emptyList()

                val start = maxOf(1, count - limit + 1)
                val messages = folder.getMessages(start, count)

                val profile = FetchProfile()
                profile.add(FetchProfile.Item.ENVELOPE)
                if (folder is IMAPFolder) profile.add(UIDFolder.FetchProfileItem.UID)
                folder.fetch(messages, profile)

                val result = ArrayList<MailMessage>()
                for (m in messages.reversed()) {
                    val uid = uidOf(folder, m) ?: continue
                    if (uid in known) continue
                    result += toMailMessage(a.id, uid, m)
                }
                return result
            } finally {
                try {
                    folder.close(false)
                } catch (_: Exception) {
                }
            }
        } finally {
            try {
                store.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun uidOf(folder: Folder, m: Message): String? = when (folder) {
        is IMAPFolder -> folder.getUID(m).toString()
        is POP3Folder -> folder.getUID(m)
        else -> null
    }

    private fun toMailMessage(accountId: Long, uid: String, m: Message): MailMessage {
        val from = m.from?.firstOrNull() as? InternetAddress
        val fromAddress = from?.address ?: ""
        val fromName = from?.personal?.takeIf { it.isNotBlank() } ?: fromAddress
        val body = try {
            extractBody(m)
        } catch (e: Exception) {
            "(Could not read this message: ${e.message})"
        }
        val date = m.sentDate?.time ?: m.receivedDate?.time ?: 0L
        return MailMessage(
            accountId = accountId,
            uid = uid,
            fromName = fromName,
            fromAddress = fromAddress,
            subject = m.subject?.takeIf { it.isNotBlank() } ?: "(no subject)",
            date = date,
            snippet = body.replace(Regex("\\s+"), " ").trim().take(140),
            body = body
        )
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
        val cleaned = html
            .replace(Regex("(?is)<(style|script|head)[^>]*>.*?</\\1>"), "")
        return Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    /** Sends a plain-text message through the account's SMTP server. */
    fun send(a: Account, password: String, to: String, subject: String, body: String) {
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
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
        message.setSubject(subject, "UTF-8")
        message.setText(body, "UTF-8")
        message.setSentDate(Date())
        Transport.send(message)
    }
}
