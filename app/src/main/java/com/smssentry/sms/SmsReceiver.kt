package com.smssentry.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        const val ACTION_SMS_RECEIVED = "com.smssentry.SMS_RECEIVED"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (smsMessage in messages) {
                val sender = smsMessage.displayOriginatingAddress ?: "Unknown"
                val body = smsMessage.displayMessageBody ?: ""
                val timestamp = smsMessage.timestampMillis

                Log.d(TAG, "SMS received from $sender: ${body.take(50)}")

                val broadcastIntent = Intent(ACTION_SMS_RECEIVED).apply {
                    putExtra(EXTRA_SENDER, sender)
                    putExtra(EXTRA_BODY, body)
                    putExtra(EXTRA_TIMESTAMP, timestamp)
                    setPackage(context.packageName)
                }
                context.sendBroadcast(broadcastIntent)
            }
        }
    }
}
