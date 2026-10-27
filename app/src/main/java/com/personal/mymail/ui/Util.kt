package com.personal.mymail.ui

import com.personal.mymail.data.MailMessage
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
}

internal fun formatFullDate(millis: Long): String {
    if (millis <= 0L) return ""
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
}

/** Pulls plain email addresses out of text like "Jo <jo@x.com>, amy@y.org". */
internal fun extractEmails(text: String): List<String> =
    Regex("[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        .findAll(text)
        .map { it.value }
        .toList()

/** "Re: hello" stays as is; "hello" becomes "Re: hello". */
internal fun withPrefix(prefix: String, subject: String): String =
    if (subject.startsWith(prefix, ignoreCase = true)) subject else "$prefix $subject"

/** The original message quoted for a reply. */
internal fun quotedReply(m: MailMessage): String {
    val header = "On ${formatFullDate(m.date)}, ${m.fromName} <${m.fromAddress}> wrote:"
    val quoted = m.body.lines().joinToString("\n") { "> $it" }
    return "\n\n$header\n$quoted"
}

/** The original message included for a forward. */
internal fun forwardedBody(m: MailMessage): String =
    "\n\n---------- Forwarded message ----------\n" +
        "From: ${m.fromName} <${m.fromAddress}>\n" +
        "Date: ${formatFullDate(m.date)}\n" +
        "Subject: ${m.subject}\n" +
        "To: ${m.toAddresses}\n\n" +
        m.body
