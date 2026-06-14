package com.smssentry.learning.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores every user correction / label — the ground truth for personal learning.
 * Each row represents a single user action on a specific message.
 */
@Entity(
    tableName = "user_feedback",
    indices = [
        Index(value = ["address"]),
        Index(value = ["user_label"]),
        Index(value = ["source"])
    ]
)
data class UserFeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Sender phone number or shortcode */
    val address: String,

    /** Truncated message preview (first 50 chars) — NOT full body */
    @ColumnInfo(name = "body_preview")
    val bodyPreview: String,

    /** SHA-256 hash of the full body for deduplication */
    @ColumnInfo(name = "body_hash")
    val bodyHash: String,

    /** Original message timestamp */
    @ColumnInfo(name = "sms_timestamp")
    val smsTimestamp: Long,

    /** User-assigned label: SAFE, SCAM, SUSPICIOUS */
    @ColumnInfo(name = "user_label")
    val userLabel: String,

    /** What the AI originally predicted (null for imports) */
    @ColumnInfo(name = "ai_prediction")
    val aiPrediction: String? = null,

    /** AI's original confidence (null for imports) */
    @ColumnInfo(name = "ai_confidence")
    val aiConfidence: Float? = null,

    /** True if user changed the AI's verdict */
    @ColumnInfo(name = "was_corrected")
    val wasCorrected: Boolean = false,

    /** When the user labeled this message */
    @ColumnInfo(name = "labeled_at")
    val labeledAt: Long = System.currentTimeMillis(),

    /** Origin: USER_FEEDBACK, BULK_IMPORT, AUTO_CONTACT */
    val source: String = "USER_FEEDBACK"
)
