package com.smssentry.deepcheck.prefilter

import android.content.Context
import com.smssentry.R
import com.smssentry.deepcheck.data.AllowlistDao
import com.smssentry.deepcheck.data.HistoryDao
import com.smssentry.deepcheck.util.DomainMatchUtil
import com.smssentry.deepcheck.util.HashUtil
import com.smssentry.learning.data.PersonalLearningDao

data class PreFilterResult(
    val verdict: String?,
    val confidence: Float,
    val reason: String?
)

object FastPathFilter {

    private val SUSPICIOUS_TLDS = setOf(
        ".tk", ".ml", ".ga", ".cf", ".xyz", ".top", ".club"
    )

    private val IP_URL_REGEX = Regex("""https?://\d{1,3}(\.\d{1,3}){3}""")

    private val URL_REGEX = Regex(
        """https?://[^\s<>"'()]+|[a-zA-Z0-9][-a-zA-Z0-9]*\.[a-zA-Z]{2,}(?:/[^\s<>"'()]*)?"""
    )

    suspend fun filter(
        context: Context,
        smsBody: String,
        sender: String,
        allowlistDao: AllowlistDao,
        historyDao: HistoryDao,
        personalLearningDao: PersonalLearningDao? = null
    ): PreFilterResult {
        // Step 1: Allowlist sender
        if (allowlistDao.containsSender(sender)) {
            return PreFilterResult("SAFE", 0.99f, context.getString(R.string.reason_allowlist_sender))
        }

        // Step 1b: Personal learning — trusted sender
        if (personalLearningDao != null) {
            val trust = personalLearningDao.getSenderTrust(sender)
            if (trust != null && trust.trustScore >= 0.85f &&
                (trust.safeCount + trust.suspiciousCount) >= 3) {
                return PreFilterResult("SAFE", 0.90f, context.getString(R.string.reason_user_trusted_sender))
            }
        }

        val urls = extractUrls(smsBody)
        val domains = extractDomains(urls)

        for (domain in domains) {
            if (allowlistDao.containsDomain(domain)) {
                return PreFilterResult("SAFE", 0.99f, context.getString(R.string.reason_allowlist_domain, domain))
            }
        }

        for (url in urls) {
            val lowerUrl = url.lowercase()
            for (tld in SUSPICIOUS_TLDS) {
                if (lowerUrl.endsWith(tld) || lowerUrl.contains("$tld/") || lowerUrl.contains("$tld?")) {
                    return PreFilterResult("SCAM", 0.95f, context.getString(R.string.reason_suspicious_tld, tld))
                }
            }
        }

        for (url in urls) {
            if (IP_URL_REGEX.containsMatchIn(url)) {
                return PreFilterResult("SCAM", 0.95f, context.getString(R.string.reason_ip_address))
            }
        }

        val lowerBody = smsBody.lowercase()
        if (lowerBody.contains("otp") && urls.isEmpty()) {
            return PreFilterResult("SAFE", 0.90f, context.getString(R.string.reason_otp_safe))
        }

        val prefix = smsBody.take(10)
        val hash = HashUtil.hashSms(sender, prefix)
        val historyEntry = historyDao.get(hash)
        if (historyEntry != null && historyEntry.verdict == "SCAM") {
            return PreFilterResult("SCAM", 0.98f, context.getString(R.string.reason_previous_scam))
        }

        // Step 7: Personal learning — flagged sender
        if (personalLearningDao != null) {
            val trust = personalLearningDao.getSenderTrust(sender)
            if (trust != null && trust.trustScore <= 0.15f && trust.scamCount >= 2) {
                return PreFilterResult("SCAM", 0.90f, context.getString(R.string.reason_user_flagged_sender))
            }
        }

        return PreFilterResult(null, 0.0f, null)
    }

    fun extractUrls(text: String): List<String> {
        return URL_REGEX.findAll(text).map { match ->
            val url = match.value
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
        }.toList()
    }

    fun extractDomains(urls: List<String>): List<String> {
        return urls.mapNotNull { url ->
            try {
                val host = url.removePrefix("https://").removePrefix("http://")
                    .substringBefore("/").substringBefore("?").substringBefore(":")
                DomainMatchUtil.extractEtldPlus1(host)
            } catch (e: Exception) {
                null
            }
        }.distinct()
    }
}
