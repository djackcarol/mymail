package com.personal.mymail.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.personal.mymail.MainActivity
import com.personal.mymail.data.NewMail

object Notifier {
    private const val CHANNEL_ID = "new_mail"
    const val EXTRA_MESSAGE_ID = "messageId"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "New mail", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openIntent(context: Context, messageId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        return PendingIntent.getActivity(
            context,
            messageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Shows one notification per message for a few messages, or a single summary for many. */
    fun notifyNew(context: Context, items: List<NewMail>) {
        if (items.isEmpty() || !canNotify(context)) return
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        try {
            if (items.size <= 3) {
                for (item in items) {
                    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_email)
                        .setContentTitle(item.fromName)
                        .setContentText(item.subject)
                        .setSubText(item.accountEmail)
                        .setContentIntent(openIntent(context, item.messageId))
                        .setAutoCancel(true)
                        .build()
                    manager.notify(item.messageId.toInt(), notification)
                }
            } else {
                val style = NotificationCompat.InboxStyle()
                items.take(5).forEach { style.addLine("${it.fromName}: ${it.subject}") }
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle("${items.size} new messages")
                    .setContentText(items.first().subject)
                    .setStyle(style)
                    .setContentIntent(openIntent(context, -1L))
                    .setAutoCancel(true)
                    .build()
                manager.notify(0, notification)
            }
        } catch (_: SecurityException) {
            // Notification permission was revoked; nothing to do.
        }
    }
}
