package com.smssentry.data.model

data class SmsMessage(
    val id: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val isSent: Boolean = false,
    val classification: ClassificationResult? = null
)

