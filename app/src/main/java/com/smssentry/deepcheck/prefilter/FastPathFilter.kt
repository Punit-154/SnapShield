package com.smssentry.deepcheck.prefilter

import com.smssentry.deepcheck.data.AllowlistDao
import com.smssentry.deepcheck.data.HistoryDao
import com.smssentry.deepcheck.util.DomainMatchUtil
import com.smssentry.deepcheck.util.HashUtil

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
        smsBody: String,
        sender: String,
        allowlistDao: AllowlistDao,
        historyDao: HistoryDao
    ): PreFilterResult {
        if (allowlistDao.containsSender(sender)) {
            return PreFilterResult("SAFE", 0.99f, "Sender is on the allowlist.")
        }

        val urls = extractUrls(smsBody)
        val domains = extractDomains(urls)

        for (domain in domains) {
            if (allowlistDao.containsDomain(domain)) {
                return PreFilterResult("SAFE", 0.99f, "Domain '$domain' is on the allowlist.")
            }
        }

        for (url in urls) {
            val lowerUrl = url.lowercase()
            for (tld in SUSPICIOUS_TLDS) {
                if (lowerUrl.endsWith(tld) || lowerUrl.contains("$tld/") || lowerUrl.contains("$tld?")) {
                    return PreFilterResult("SCAM", 0.95f, "URL uses suspicious TLD: $tld")
                }
            }
        }

        for (url in urls) {
            if (IP_URL_REGEX.containsMatchIn(url)) {
                return PreFilterResult("SCAM", 0.95f, "URL contains raw IP address.")
            }
        }

        val lowerBody = smsBody.lowercase()
        if (lowerBody.contains("otp") && urls.isEmpty()) {
            return PreFilterResult("SAFE", 0.90f, "OTP message with no links — likely a genuine verification code.")
        }

        val prefix = smsBody.take(10)
        val hash = HashUtil.hashSms(sender, prefix)
        val historyEntry = historyDao.get(hash)
        if (historyEntry != null && historyEntry.verdict == "SCAM") {
            return PreFilterResult("SCAM", 0.98f, "This exact SMS was previously flagged as a scam.")
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
