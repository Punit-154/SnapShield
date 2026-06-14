package com.smssentry.data.model

/**
 * Represents a conversation thread — a group of SMS messages with the same contact.
 * Maps to Android's SMS thread_id concept.
 */
data class Conversation(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val photoUri: String? = null,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0,
    val messageCount: Int = 0,
    val isFlagged: Boolean = false,  // true if any message in thread was flagged by AI
    val snippet: String = lastMessage.take(80)
)
