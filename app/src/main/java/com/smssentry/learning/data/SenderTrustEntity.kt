package com.smssentry.learning.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Aggregated per-sender reputation derived from user feedback.
 * Trust score is recomputed every time feedback is submitted for a sender.
 *
 * Trust score uses Bayesian smoothing: (safe + 0.5*suspicious + 1) / (safe + scam + suspicious + 2)
 * This avoids extreme scores from single data points:
 *  - 0 feedback → 0.50 (neutral)
 *  - 1 safe     → 0.67 (leaning safe)
 *  - 3 safe     → 0.80 (trusted)
 *  - 5 safe     → 0.86 (highly trusted)
 *  - 1 scam     → 0.33 (suspicious)
 *  - 3 scam     → 0.20 (untrusted)
 */
@Entity(tableName = "sender_trust")
data class SenderTrustEntity(
    /** Sender phone number or shortcode */
    @PrimaryKey
    val address: String,

    /** Contact display name if known */
    @ColumnInfo(name = "display_name")
    val displayName: String? = null,

    /** Count of messages the user marked as SAFE */
    @ColumnInfo(name = "safe_count")
    val safeCount: Int = 0,

    /** Count of messages the user marked as SCAM */
    @ColumnInfo(name = "scam_count")
    val scamCount: Int = 0,

    /** Count of messages the user marked as SUSPICIOUS */
    @ColumnInfo(name = "suspicious_count")
    val suspiciousCount: Int = 0,

    /**
     * Bayesian trust score: 0.0 (definitely scam) → 1.0 (trusted).
     * Formula: (safe + 0.5*suspicious + 1) / (safe + scam + suspicious + 2)
     */
    @ColumnInfo(name = "trust_score")
    val trustScore: Float = 0.5f,

    /** Whether the sender matches a known contact */
    @ColumnInfo(name = "is_known_contact")
    val isKnownContact: Boolean = false,

    /** Total messages seen from this sender */
    @ColumnInfo(name = "total_messages")
    val totalMessages: Int = 0,

    /** Last time this entry was updated */
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Computes the Bayesian trust score from feedback counts.
         * Uses a prior of 1 safe + 1 scam to smooth the estimate.
         */
        fun computeTrustScore(safe: Int, scam: Int, suspicious: Int): Float {
            val numerator = safe.toFloat() + 0.5f * suspicious + 1f
            val denominator = safe.toFloat() + scam.toFloat() + suspicious.toFloat() + 2f
            return (numerator / denominator).coerceIn(0f, 1f)
        }
    }
}
