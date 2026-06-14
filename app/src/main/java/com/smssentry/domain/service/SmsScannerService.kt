package com.smssentry.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.smssentry.MainActivity
import com.smssentry.ml.SmsClassifierModel
import kotlinx.coroutines.*

class SmsScannerService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var classifier: SmsClassifierModel
    private val FOREGROUND_CHANNEL_ID = "smsentry_service"
    private val ALERT_CHANNEL_ID = "smsentry_alerts"

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            // Group multipart messages
            val grouped = mutableMapOf<String, StringBuilder>()
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: "Unknown"
                grouped.getOrPut(sender) { StringBuilder() }.append(sms.messageBody)
            }

            scope.launch {
                for ((sender, bodyBuilder) in grouped) {
                    val body = bodyBuilder.toString()
                    val result = classifier.classify(body)
                    showAlertNotification(sender, body, result.label, result.riskScore)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        classifier = SmsClassifierModel(this)
        classifier.initialize()
        createChannels()
        startForeground(1, buildForegroundNotification())

        // Register receiver inside the service — works on all phones
        val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            priority = 999
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(smsReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
        classifier.close()
        scope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restart if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Foreground service channel (silent)
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    FOREGROUND_CHANNEL_ID,
                    "SMS Sentry Scanner",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Keeps SMS scanning active"
                    setShowBadge(false)
                }
            )

            // Alert channel (loud for scams)
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "SMS Sentry Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Real-time SMS threat alerts"
                }
            )
        }
    }

    private fun buildForegroundNotification() =
        NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("SMSentry is active")
            .setContentText("Scanning incoming messages for threats")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

    private fun showAlertNotification(
        sender: String,
        body: String,
        label: String,
        riskScore: Int
    ) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
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
                "Suspicious — Risk $riskScore/100",
                android.graphics.Color.parseColor("#FFa502")
            )
            else -> Triple(
                "✅",
                "Safe message — Risk $riskScore/100",
                android.graphics.Color.parseColor("#2ED573")
            )
        }

        val preview = if (body.length > 80) body.take(80) + "…" else body

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
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

        notificationManager.notify(sender.hashCode(), notification)
    }
}