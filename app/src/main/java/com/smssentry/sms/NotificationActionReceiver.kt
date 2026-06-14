package com.smssentry.sms

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.RemoteInput

/**
 * Handles notification direct reply and mark-as-read actions.
 *
 * Actions:
 * - REPLY: Sends the reply SMS via SmsManager and persists to the SMS content provider
 * - MARK_READ: Marks all messages in the thread as read
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotifActionReceiver"

        const val ACTION_REPLY = "com.smssentry.ACTION_REPLY"
        const val ACTION_MARK_READ = "com.smssentry.ACTION_MARK_READ"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REPLY -> handleReply(context, intent)
            ACTION_MARK_READ -> handleMarkRead(context, intent)
        }
    }

    private fun handleReply(context: Context, intent: Intent) {
        val sender = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_SENDER)
        val threadId = intent.getLongExtra(NotificationHelper.EXTRA_THREAD_ID, -1)

        if (sender.isNullOrBlank()) {
            Log.e(TAG, "Reply action: missing sender address")
            return
        }

        // Extract reply text from RemoteInput
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()

        if (replyText.isNullOrBlank()) {
            Log.e(TAG, "Reply action: empty reply text")
            return
        }

        Log.d(TAG, "Sending reply to $sender: ${replyText.take(50)}...")

        try {
            // Send the SMS
            val smsManager = context.getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(replyText)
            if (parts.size == 1) {
                smsManager.sendTextMessage(sender, null, replyText, null, null)
            } else {
                smsManager.sendMultipartTextMessage(sender, null, parts, null, null)
            }

            // Persist the sent message to the SMS content provider
            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, sender)
                    put(Telephony.Sms.BODY, replyText)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                    if (threadId > 0) {
                        put(Telephony.Sms.THREAD_ID, threadId)
                    }
                }
                context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
                Log.d(TAG, "Reply persisted to SMS provider")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist reply (sent but not saved): ${e.message}")
            }

            // Update the notification to show reply was sent
            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = sender.hashCode()

            // Create a "reply sent" notification to replace the existing one
            val repliedNotification = androidx.core.app.NotificationCompat.Builder(
                context, NotificationHelper.CHANNEL_NEW_SMS
            )
                .setSmallIcon(com.smssentry.R.drawable.ic_launcher_foreground)
                .setContentText("Reply sent")
                .build()

            notifManager.notify(notificationId, repliedNotification)

            // Auto-dismiss after a brief delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                notifManager.cancel(notificationId)
            }, 2000)

            Log.d(TAG, "Reply sent successfully to $sender")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send reply to $sender", e)
        }
    }

    private fun handleMarkRead(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(NotificationHelper.EXTRA_THREAD_ID, -1)
        val sender = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_SENDER)

        if (threadId <= 0) {
            Log.e(TAG, "Mark-read action: invalid threadId=$threadId")
            return
        }

        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            val updated = context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
            Log.d(TAG, "Marked $updated messages as read in thread $threadId")

            // Dismiss the notification
            if (!sender.isNullOrBlank()) {
                NotificationHelper.cancelNotification(context, sender)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark thread $threadId as read", e)
        }
    }
}
