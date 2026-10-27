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

@Entity(
    tableName = "messages",
    indices = [Index(value = ["accountId", "uid"], unique = true)]
)
data class MailMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** IMAP UID or POP3 UIDL, unique per account. */
    val uid: String,
    val fromName: String,
    val fromAddress: String,
    val subject: String,
    val date: Long,
    val snippet: String,
    val body: String,
    val isRead: Boolean = false
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
    val isRead: Boolean
)
