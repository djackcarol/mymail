package com.personal.mymail.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A mail account. The password is NOT stored here; see [Secrets]. */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val email: String,
    /** "IMAP" or "POP3" */
    val protocol: String,
    val incomingHost: String,
    val incomingPort: Int,
    /** "SSL", "STARTTLS" or "NONE" */
    val incomingSecurity: String,
    val username: String,
    val smtpHost: String,
    val smtpPort: Int,
    /** "SSL", "STARTTLS" or "NONE" */
    val smtpSecurity: String
)

/** A mailbox folder on the server. POP3 accounts only ever have INBOX. */
@Entity(
    tableName = "folders",
    indices = [Index(value = ["accountId", "fullName"], unique = true)]
)
data class MailFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** Full server path, e.g. "INBOX" or "[Gmail]/Sent Mail". */
    val fullName: String,
    val displayName: String,
    /** INBOX, SENT, DRAFTS, TRASH, JUNK, ARCHIVE or OTHER */
    val role: String,
    val syncEnabled: Boolean,
    /** 0 until the first successful sync; used so old mail doesn't trigger notifications. */
    val lastSync: Long = 0,
    /** The server's UIDVALIDITY for this folder; if it changes, the stored UIDs are meaningless. */
    val uidValidity: Long = 0
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["accountId", "folder", "uid"], unique = true)]
)
data class MailMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** Full folder path the message lives in. */
    val folder: String,
    /** IMAP UID or POP3 UIDL, unique per account and folder. */
    val uid: String,
    val fromName: String,
    val fromAddress: String,
    val toAddresses: String,
    val ccAddresses: String,
    /** The Message-ID header, used to thread replies. */
    val messageIdHeader: String,
    val subject: String,
    val date: Long,
    val snippet: String,
    val body: String,
    val isRead: Boolean = false,
    val hasAttachments: Boolean = false,
    /** Read on this phone, but the server has not been told yet. */
    val readPending: Boolean = false,
    /** The formatted (HTML) version of the message, or empty if it has none. */
    val htmlBody: String = "",
    /** False for messages saved before HTML was kept; they fetch it from the server when opened. */
    val htmlChecked: Boolean = false
)

@Entity(
    tableName = "attachments",
    indices = [Index(value = ["messageId"])]
)
data class AttachmentInfo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    /** Position among the message's attachments, in the order the engine walks them. */
    val partIndex: Int,
    val name: String,
    val mimeType: String,
    val size: Long
)

/** Lightweight row for list screens (no full body). */
data class MessageRow(
    val id: Long,
    val accountId: Long,
    val fromName: String,
    val fromAddress: String,
    val subject: String,
    val date: Long,
    val snippet: String,
    val isRead: Boolean,
    val hasAttachments: Boolean
)

/** What we remember about a downloaded message, used to compare with the server. */
data class LocalState(
    val id: Long,
    val uid: String,
    val isRead: Boolean,
    val readPending: Boolean
)

/** A freshly downloaded message, used to build notifications. */
data class NewMail(
    val messageId: Long,
    val accountEmail: String,
    val fromName: String,
    val subject: String
)
