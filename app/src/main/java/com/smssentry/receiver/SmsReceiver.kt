package com.smssentry.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.smssentry.MainActivity
import com.smssentry.ml.SmsClassifierModel
import kotlinx.coroutines.*

class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group parts of the same message together
        val grouped = mutableMapOf<String, StringBuilder>()
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: "Unknown"
            grouped.getOrPut(sender) { StringBuilder() }.append(sms.messageBody)
        }

        val pendingResult = goAsync()

        scope.launch {
            try {
                val classifier = SmsClassifierModel(context)
                classifier.initialize()

                for ((sender, bodyBuilder) in grouped) {
                    val body = bodyBuilder.toString()
                    val result = classifier.classify(body)
                    showNotification(context, sender, body, result.label, result.riskScore)
                }

                classifier.close()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        sender: String,
        body: String,
        label: String,
        riskScore: Int
    ) {
        val channelId = "smsentry_scan"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel once
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = when (label) {
                "SCAM" -> NotificationManager.IMPORTANCE_HIGH
                "SUSPICIOUS" -> NotificationManager.IMPORTANCE_DEFAULT
                else -> NotificationManager.IMPORTANCE_LOW
            }
            val channel = NotificationChannel(
                channelId,
                "SMS Sentry Alerts",
                importance
            ).apply {
                description = "Real-time SMS threat detection"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap to open app
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (emoji, title, color) = when (label) {
            "SCAM" -> Triple(
                "🛑",
                "SCAM DETECTED — Risk $riskScore/100",
                android.graphics.Color.parseColor("#FF4757")
            )
            "SUSPICIOUS" -> Triple(
                "⚠️",
                "Suspicious message — Risk $riskScore/100",
                android.graphics.Color.parseColor("#FFa502")
            )
            else -> Triple(
                "✅",
                "Safe message — Risk $riskScore/100",
                android.graphics.Color.parseColor("#2ED573")
            )
        }

        val preview = if (body.length > 80) body.take(80) + "…" else body

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("$emoji $title")
            .setContentText("From $sender: $preview")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("From: $sender\n\n$body")
                    .setBigContentTitle("$emoji $title")
            )
            .setColor(color)
            .setColorized(true)
            .setAutoCancel(true)
            .setPriority(
                when (label) {
                    "SCAM" -> NotificationCompat.PRIORITY_HIGH
                    "SUSPICIOUS" -> NotificationCompat.PRIORITY_DEFAULT
                    else -> NotificationCompat.PRIORITY_LOW
                }
            )
            .setContentIntent(pendingIntent)
            .build()

        // Use sender hashcode as notification ID so each sender gets its own notification
        notificationManager.notify(sender.hashCode(), notification)
    }
}