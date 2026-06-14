package com.smssentry.data.model

data class SmsMessage(
    val id: String,
    val threadId: Long = -1,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val isSent: Boolean = false,
    val isRead: Boolean = true,
    val classification: ClassificationResult? = null
)
