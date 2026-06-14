package com.smssentry.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.smssentry.data.util.ContactResolver
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
                val contactResolver = ContactResolver(context.applicationContext)

                for ((sender, bodyBuilder) in smsByAddress) {
                    val body = bodyBuilder.toString()
                    Log.d(TAG, "SMS received from $sender: ${body.take(50)}")

                    // CRITICAL: Write to SMS content provider so the message
                    // persists and shows in the inbox. As the default SMS app,
                    // it is our responsibility to store the message.
                    writeToSmsProvider(context, sender, body, finalTimestamp)

                    // Resolve contact display name for notifications
                    val displayName = contactResolver.getDisplayName(sender)

                    // Resolve thread ID for notification grouping
                    val threadId = try {
                        Telephony.Threads.getOrCreateThreadId(context, sender)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not resolve thread ID for $sender", e)
                        sender.hashCode().toLong()
                    }

                    // Run scam detection — show scam warning OR regular notification
                    val isScam = processSms(context, sender, displayName, body)
                    if (!isScam) {
                        NotificationHelper.showNewMessageNotification(
                            context = context,
                            sender = sender,
                            displayName = displayName,
                            body = body,
                            threadId = threadId
                        )
                    }

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

    /**
     * Runs scam detection on the message. Returns true if the message was
     * identified as SCAM (and a scam warning notification was shown).
     */
    private suspend fun processSms(
        context: Context,
        sender: String,
        displayName: String,
        body: String
    ): Boolean {
        return try {
            val db = DeepCheckDatabase.getInstance(context)
            val result = FastPathFilter.filter(
                context,
                body,
                sender,
                db.allowlistDao(),
                db.historyDao(),
            )

            if (result.verdict == "SCAM") {
                NotificationHelper.showScamWarning(
                    context = context,
                    sender = sender,
                    displayName = displayName,
                    body = body,
                    reason = result.reason ?: "Suspicious content detected"
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
            false
        }
    }
}
