package com.smssentry.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smssentry.deepcheck.data.DeepCheckDatabase
import com.smssentry.deepcheck.prefilter.FastPathFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "SmsReceiver"
        const val ACTION_SMS_RECEIVED = "com.smssentry.SMS_RECEIVED"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        private const val CHANNEL_ID = "scam_alerts"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_DELIVER_ACTION &&
            action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        ) return

        // Must call goAsync() inside onReceive before launching coroutine
        val pendingResult = goAsync()
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        // Combine multi-part SMS segments by sender
        val smsByAddress = mutableMapOf<String, StringBuilder>()
        var latestTimestamp = 0L

        for (smsMessage in messages) {
            val sender = smsMessage.displayOriginatingAddress ?: "Unknown"
            val body = smsMessage.displayMessageBody ?: ""
            val timestamp = smsMessage.timestampMillis
            smsByAddress.getOrPut(sender) { StringBuilder() }.append(body)
            if (timestamp > latestTimestamp) latestTimestamp = timestamp
        }

        val finalTimestamp = latestTimestamp

        receiverScope.launch {
            try {
                for ((sender, bodyBuilder) in smsByAddress) {
                    val body = bodyBuilder.toString()
                    Log.d(TAG, "SMS received from $sender: ${body.take(50)}")

                    // CRITICAL: Write to SMS content provider so the message
                    // persists and shows in the inbox. As the default SMS app,
                    // it is our responsibility to store the message.
                    writeToSmsProvider(context, sender, body, finalTimestamp)

                    // Run scam detection
                    processSms(context, sender, body)

                    // Broadcast internally for UI updates
                    val broadcastIntent = Intent(ACTION_SMS_RECEIVED).apply {
                        putExtra(EXTRA_SENDER, sender)
                        putExtra(EXTRA_BODY, body)
                        putExtra(EXTRA_TIMESTAMP, finalTimestamp)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(broadcastIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling received SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun writeToSmsProvider(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long
    ) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.DATE_SENT, timestamp)
                put(Telephony.Sms.READ, 0)       // Mark as unread
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            Log.d(TAG, "SMS written to provider: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write SMS to content provider", e)
        }
    }

    private fun processSms(context: Context, sender: String, body: String) {
        try {
            val db = DeepCheckDatabase.getInstance(context)
            val result = kotlinx.coroutines.runBlocking {
                FastPathFilter.filter(
                    context,
                    body,
                    sender,
                    db.allowlistDao(),
                    db.historyDao(),
                )
            }

            if (result.verdict == "SCAM") {
                showScamWarning(context, sender, body, result.reason ?: "Suspicious content detected")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
        }
    }

    private fun showScamWarning(context: Context, sender: String, message: String, reason: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Security Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for detected scam messages"
        }
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Scam Detected!")
            .setContentText("Message from $sender: $reason")
            .setStyle(NotificationCompat.BigTextStyle().bigText("From: $sender\nReason: $reason\n\nMessage: $message"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
