package com.smssentry.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.smssentry.MainActivity
import com.smssentry.R

/**
 * Centralized helper for creating and managing all SMSentry notifications.
 */
object NotificationHelper {

    const val CHANNEL_NEW_SMS = "new_sms"
    const val CHANNEL_SCAM_WARNING = "scam_warning"

    private const val GROUP_KEY_PREFIX = "com.smssentry.SMS_GROUP_"

    /** Key used by RemoteInput for direct reply text. */
    const val KEY_TEXT_REPLY = "key_text_reply"

    /** Intent extras for notification actions. */
    const val EXTRA_THREAD_ID = "extra_thread_id"
    const val EXTRA_NOTIFICATION_SENDER = "extra_notification_sender"
    const val EXTRA_MARK_READ = "extra_mark_read"

    /**
     * Creates the required notification channels. Call from [Application.onCreate].
     */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // New Messages channel
        val newSmsChannel = NotificationChannel(
            CHANNEL_NEW_SMS,
            "New Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for incoming SMS messages"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 100, 250)
        }

        // Scam Warnings channel
        val scamChannel = NotificationChannel(
            CHANNEL_SCAM_WARNING,
            "Scam Warnings",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High-priority alerts for detected scam messages"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            enableLights(true)
            lightColor = Color.RED
        }

        manager.createNotificationChannels(listOf(newSmsChannel, scamChannel))
    }

    /**
     * Shows a notification for a new incoming SMS message.
     *
     * @param context     Application or receiver context
     * @param sender      The originating phone number / address
     * @param displayName Contact name or formatted number
     * @param body        The message body
     * @param threadId    The SMS thread ID for grouping and navigation
     */
    fun showNewMessageNotification(
        context: Context,
        sender: String,
        displayName: String,
        body: String,
        threadId: Long
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = sender.hashCode()
        val groupKey = "$GROUP_KEY_PREFIX${sender}"

        // Content intent — open conversation in MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_NOTIFICATION_SENDER, sender)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Direct reply action
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()

        val replyIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_NOTIFICATION_SENDER, sender)
        }
        val replyPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        // Mark-as-read action
        val markReadIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_NOTIFICATION_SENDER, sender)
            putExtra(EXTRA_MARK_READ, true)
        }
        val markReadPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 2,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markReadAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            "Mark as Read",
            markReadPendingIntent
        ).build()

        // Build the notification
        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_SMS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(displayName)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(groupKey)
            .setContentIntent(contentPendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        manager.notify(notificationId, notification)
    }

    /**
     * Shows a high-priority scam warning notification.
     *
     * @param context     Application or receiver context
     * @param sender      The originating phone number / address
     * @param displayName Contact name or formatted number
     * @param body        The message body
     * @param reason      AI analysis reason explaining why this is flagged
     */
    fun showScamWarning(
        context: Context,
        sender: String,
        displayName: String,
        body: String,
        reason: String
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Use a distinct notification ID range to avoid colliding with regular message notifs
        val notificationId = "scam_${sender}".hashCode()

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_SENDER, sender)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = "From: $displayName ($sender)\nReason: $reason\n\nMessage: $body"

        val notification = NotificationCompat.Builder(context, CHANNEL_SCAM_WARNING)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠\uFE0F Scam Warning")
            .setContentText("$displayName: $reason")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setColor(Color.RED)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        manager.notify(notificationId, notification)
    }

    /**
     * Cancels notifications for a given sender address.
     *
     * @param context  Application context
     * @param sender   The sender address whose notification should be dismissed
     */
    fun cancelNotification(context: Context, sender: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Match the notification ID used in showNewMessageNotification
        manager.cancel(sender.hashCode())
    }
}
