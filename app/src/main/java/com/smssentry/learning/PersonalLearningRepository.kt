package com.smssentry.learning

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.smssentry.learning.data.PersonalLearningDao
import com.smssentry.learning.data.SenderTrustEntity
import com.smssentry.learning.data.UserFeedbackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stats snapshot for display in Settings.
 */
data class LearningStats(
    val totalLabeled: Int,
    val totalCorrected: Int,
    val trustedSenders: Int,
    val flaggedSenders: Int,
    val totalSenders: Int
)

/**
 * Central repository for the personal learning system.
 *
 * Responsibilities:
 * - Process user feedback and update sender trust
 * - Build personal context for LLM prompt enrichment
 * - Handle bulk SMS import with contact-based auto-labeling
 * - Provide learning statistics
 */
@Singleton
class PersonalLearningRepository @Inject constructor(
    private val dao: PersonalLearningDao,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PersonalLearning"

        // Trust thresholds for FastPathFilter integration
        const val TRUSTED_THRESHOLD = 0.85f
        const val UNTRUSTED_THRESHOLD = 0.15f
        const val MIN_FEEDBACK_FOR_TRUST = 3
        const val MIN_FEEDBACK_FOR_DISTRUST = 2
    }

    // ── User Feedback ────────────────────────────────────────────────────

    /**
     * Submit user feedback on a message. Updates both the feedback record
     * and the sender's aggregated trust score.
     */
    suspend fun submitFeedback(
        address: String,
        body: String,
        smsTimestamp: Long,
        userLabel: String,
        aiPrediction: String? = null,
        aiConfidence: Float? = null
    ) {
        val wasCorrected = aiPrediction != null && aiPrediction != userLabel

        val entry = UserFeedbackEntity(
            address = address,
            body = body,
            smsTimestamp = smsTimestamp,
            userLabel = userLabel,
            aiPrediction = aiPrediction,
            aiConfidence = aiConfidence,
            wasCorrected = wasCorrected,
            source = "USER_FEEDBACK"
        )

        dao.insertFeedback(entry)
        recalculateSenderTrust(address)

        Log.d(TAG, "Feedback submitted: $address → $userLabel" +
                if (wasCorrected) " (corrected from $aiPrediction)" else "")
    }

    // ── Sender Trust ─────────────────────────────────────────────────────

    /**
     * Returns the trust score for a sender, or null if no data exists.
     */
    suspend fun getSenderTrustScore(address: String): Float? {
        return dao.getTrustScore(address)
    }

    /**
     * Returns the full trust entity for a sender.
     */
    suspend fun getSenderTrust(address: String): SenderTrustEntity? {
        return dao.getSenderTrust(address)
    }

    /**
     * Checks if a sender is trusted enough to skip AI analysis.
     * Requires both a high trust score AND minimum feedback count.
     */
    suspend fun isSenderTrusted(address: String): Boolean {
        val trust = dao.getSenderTrust(address) ?: return false
        return trust.trustScore >= TRUSTED_THRESHOLD &&
                (trust.safeCount + trust.suspiciousCount) >= MIN_FEEDBACK_FOR_TRUST
    }

    /**
     * Checks if a sender is flagged as untrustworthy.
     */
    suspend fun isSenderFlagged(address: String): Boolean {
        val trust = dao.getSenderTrust(address) ?: return false
        return trust.trustScore <= UNTRUSTED_THRESHOLD &&
                trust.scamCount >= MIN_FEEDBACK_FOR_DISTRUST
    }

    /**
     * Recalculates trust score from all feedback for a sender.
     */
    private suspend fun recalculateSenderTrust(address: String) {
        val safeCount = dao.countFeedbackForSender(address, "SAFE")
        val scamCount = dao.countFeedbackForSender(address, "SCAM")
        val suspiciousCount = dao.countFeedbackForSender(address, "SUSPICIOUS")
        val trustScore = SenderTrustEntity.computeTrustScore(safeCount, scamCount, suspiciousCount)

        val existing = dao.getSenderTrust(address)
        val updated = (existing ?: SenderTrustEntity(address = address)).copy(
            safeCount = safeCount,
            scamCount = scamCount,
            suspiciousCount = suspiciousCount,
            trustScore = trustScore,
            totalMessages = safeCount + scamCount + suspiciousCount,
            lastUpdated = System.currentTimeMillis()
        )
        dao.upsertSenderTrust(updated)
    }

    // ── Personal Context for LLM ─────────────────────────────────────────

    /**
     * Builds a personal context string to inject into the LLM prompt.
     * Returns empty string if no relevant personal data exists.
     */
    suspend fun buildPersonalContext(sender: String, messageText: String): String {
        val trust = dao.getSenderTrust(sender) ?: return ""
        val totalLabeled = dao.totalFeedbackCount()

        if (trust.totalMessages == 0 && totalLabeled == 0) return ""

        return buildString {
            appendLine("Personal memory context:")

            // Sender-specific history
            if (trust.totalMessages > 0) {
                val parts = mutableListOf<String>()
                if (trust.safeCount > 0) parts.add("${trust.safeCount} safe")
                if (trust.scamCount > 0) parts.add("${trust.scamCount} scam")
                if (trust.suspiciousCount > 0) parts.add("${trust.suspiciousCount} suspicious")
                appendLine("- User has labeled ${trust.totalMessages} messages from this sender: ${parts.joinToString(", ")}")

                val trustLabel = when {
                    trust.trustScore >= TRUSTED_THRESHOLD -> "high trust"
                    trust.trustScore <= UNTRUSTED_THRESHOLD -> "low trust — user considers this sender suspicious"
                    trust.trustScore >= 0.6f -> "moderate trust"
                    else -> "uncertain"
                }
                appendLine("- Sender trust score: %.2f ($trustLabel)".format(trust.trustScore))
            }

            if (trust.isKnownContact && trust.displayName != null) {
                appendLine("- This sender is in the user's contacts as \"${trust.displayName}\"")
            }

            // Overall user profile
            if (totalLabeled > 5) {
                val scamSenders = dao.scamSenderCount()
                appendLine("- User has labeled $totalLabeled messages total across $scamSenders flagged senders")
            }
        }.trimEnd()
    }

    // ── Bulk Import ──────────────────────────────────────────────────────

    /**
     * Imports all existing SMS messages from the system content provider.
     * Known contacts are auto-labeled as SAFE. Unknown senders are imported
     * without a label (they build sender profiles without affecting trust).
     *
     * @param onProgress callback with (processed, total) counts
     * @return total number of messages imported
     */
    suspend fun importExistingSms(
        onProgress: (processed: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Import skipped: READ_SMS permission not granted")
            return@withContext 0
        }

        Log.i(TAG, "Starting bulk SMS import...")
        val knownContacts = loadKnownContacts()
        Log.i(TAG, "Loaded ${knownContacts.size} known contacts")

        // Query all SMS messages grouped by sender
        val senderMessages = mutableMapOf<String, MutableList<Pair<String, Long>>>() // address → list of (body, timestamp)
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null, null,
            "${Telephony.Sms.DATE} ASC"
        )

        var totalMessages = 0
        cursor?.use {
            val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

            totalMessages = it.count

            while (it.moveToNext()) {
                val address = it.getString(addressIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val date = it.getLong(dateIdx)
                senderMessages.getOrPut(address) { mutableListOf() }.add(body to date)
            }
        }

        if (totalMessages == 0) {
            Log.i(TAG, "No SMS messages found to import")
            return@withContext 0
        }

        Log.i(TAG, "Found $totalMessages messages from ${senderMessages.size} senders")

        var processed = 0
        val feedbackBatch = mutableListOf<UserFeedbackEntity>()
        val trustBatch = mutableListOf<SenderTrustEntity>()

        for ((address, messages) in senderMessages) {
            val isContact = knownContacts.containsKey(normalizeNumber(address))
            val contactName = if (isContact) knownContacts[normalizeNumber(address)] else null

            for ((body, timestamp) in messages) {
                if (isContact) {
                    // Known contact → auto-label as SAFE
                    feedbackBatch.add(
                        UserFeedbackEntity(
                            address = address,
                            body = body,
                            smsTimestamp = timestamp,
                            userLabel = "SAFE",
                            source = "AUTO_CONTACT"
                        )
                    )
                } else {
                    // Unknown sender → import without label (builds profile)
                    feedbackBatch.add(
                        UserFeedbackEntity(
                            address = address,
                            body = body,
                            smsTimestamp = timestamp,
                            userLabel = "UNLABELED",
                            source = "BULK_IMPORT"
                        )
                    )
                }

                processed++
                if (processed % 100 == 0) {
                    // Flush batch periodically to avoid memory pressure
                    dao.insertFeedbackBatch(feedbackBatch)
                    feedbackBatch.clear()
                    onProgress(processed, totalMessages)
                }
            }

            // Build sender trust entry
            val safeCount = if (isContact) messages.size else 0
            val trustScore = SenderTrustEntity.computeTrustScore(safeCount, 0, 0)
            trustBatch.add(
                SenderTrustEntity(
                    address = address,
                    displayName = contactName,
                    safeCount = safeCount,
                    trustScore = trustScore,
                    isKnownContact = isContact,
                    totalMessages = messages.size
                )
            )
        }

        // Final flush
        if (feedbackBatch.isNotEmpty()) {
            dao.insertFeedbackBatch(feedbackBatch)
        }
        dao.insertSenderTrustBatch(trustBatch)
        onProgress(processed, totalMessages)

        val trustedCount = trustBatch.count { it.isKnownContact }
        Log.i(TAG, "Import complete: $processed messages, ${trustBatch.size} senders, $trustedCount trusted from contacts")

        processed
    }

    /**
     * Loads all phone numbers from the user's contacts.
     * Returns a map of normalized number → display name.
     */
    private fun loadKnownContacts(): Map<String, String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return emptyMap()
        }

        val contacts = mutableMapOf<String, String>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null, null, null
        )

        cursor?.use {
            val numberIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val nameIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

            while (it.moveToNext()) {
                val number = it.getString(numberIdx) ?: continue
                val name = it.getString(nameIdx) ?: continue
                contacts[normalizeNumber(number)] = name
            }
        }

        return contacts
    }

    /**
     * Normalize a phone number for comparison by stripping non-digits
     * and keeping the last 10 digits.
     */
    private fun normalizeNumber(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    // ── Stats ────────────────────────────────────────────────────────────

    /**
     * Returns learning statistics for the Settings screen.
     */
    suspend fun getStats(): LearningStats {
        return LearningStats(
            totalLabeled = dao.totalFeedbackCount(),
            totalCorrected = dao.totalCorrections(),
            trustedSenders = dao.trustedSenderCount(),
            flaggedSenders = dao.scamSenderCount(),
            totalSenders = dao.totalSenderCount()
        )
    }

    /**
     * Clears all personal learning data. Irreversible.
     */
    suspend fun clearAll() {
        dao.clearAll()
        Log.i(TAG, "All personal learning data cleared")
    }
}
