package com.smssentry.learning.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalLearningDao {

    // ── User Feedback ────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(entry: UserFeedbackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedbackBatch(entries: List<UserFeedbackEntity>)

    @Query("SELECT * FROM user_feedback WHERE address = :address ORDER BY labeled_at DESC")
    suspend fun getFeedbackForSender(address: String): List<UserFeedbackEntity>

    @Query("SELECT COUNT(*) FROM user_feedback WHERE address = :address AND user_label = :label")
    suspend fun countFeedbackForSender(address: String, label: String): Int

    @Query("SELECT * FROM user_feedback ORDER BY labeled_at DESC LIMIT :limit")
    suspend fun getRecentFeedback(limit: Int = 50): List<UserFeedbackEntity>

    @Query("SELECT COUNT(*) FROM user_feedback")
    suspend fun totalFeedbackCount(): Int

    @Query("SELECT COUNT(*) FROM user_feedback WHERE was_corrected = 1")
    suspend fun totalCorrections(): Int

    @Query("SELECT COUNT(DISTINCT address) FROM user_feedback WHERE user_label = 'SCAM'")
    suspend fun scamSenderCount(): Int

    @Query("DELETE FROM user_feedback")
    suspend fun clearAllFeedback()

    @Query("DELETE FROM user_feedback WHERE id = :id")
    suspend fun deleteFeedback(id: Long)

    // ── Sender Trust ─────────────────────────────────────────────────────

    @Query("SELECT * FROM sender_trust WHERE address = :address")
    suspend fun getSenderTrust(address: String): SenderTrustEntity?

    @Query("SELECT trust_score FROM sender_trust WHERE address = :address")
    suspend fun getTrustScore(address: String): Float?

    @Upsert
    suspend fun upsertSenderTrust(entry: SenderTrustEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSenderTrustBatch(entries: List<SenderTrustEntity>)

    @Query("SELECT * FROM sender_trust WHERE trust_score >= :minScore ORDER BY trust_score DESC")
    suspend fun getTrustedSenders(minScore: Float = 0.85f): List<SenderTrustEntity>

    @Query("SELECT * FROM sender_trust WHERE trust_score <= :maxScore ORDER BY trust_score ASC")
    suspend fun getUntrustedSenders(maxScore: Float = 0.15f): List<SenderTrustEntity>

    @Query("SELECT COUNT(*) FROM sender_trust WHERE trust_score >= 0.85")
    suspend fun trustedSenderCount(): Int

    @Query("SELECT COUNT(*) FROM sender_trust")
    suspend fun totalSenderCount(): Int

    @Query("SELECT * FROM sender_trust ORDER BY last_updated DESC")
    fun allSenderTrustFlow(): Flow<List<SenderTrustEntity>>

    @Query("DELETE FROM sender_trust")
    suspend fun clearAllSenderTrust()

    // ── Combined Stats ───────────────────────────────────────────────────

    @Transaction
    suspend fun clearAll() {
        clearAllFeedback()
        clearAllSenderTrust()
    }
}
