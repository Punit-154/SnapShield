package com.smssentry.deepcheck.tools

import com.smssentry.deepcheck.data.AllowlistDao
import com.smssentry.deepcheck.data.HistoryDao
import com.smssentry.deepcheck.data.OfficialSitesRepository
import com.smssentry.deepcheck.data.ReputationDb
import com.smssentry.deepcheck.model.LlmResponse
import com.smssentry.deepcheck.prefilter.FastPathFilter
import com.smssentry.deepcheck.proxy.PrivacyProxyClient
import com.smssentry.deepcheck.util.DomainMatchUtil
import com.smssentry.deepcheck.util.HashUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class ToolExecutor(
    private val allowlistDao: AllowlistDao,
    private val historyDao: HistoryDao,
    private val reputationDb: ReputationDb?,
    private val officialSites: OfficialSitesRepository,
    private val proxyClient: PrivacyProxyClient?
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun execute(toolCall: LlmResponse.ToolCall): String {
        return try {
            when (toolCall.name) {
                "lookup_allowlist" -> executeLookupAllowlist(toolCall.arguments)
                "search_personal_db" -> executeSearchPersonalDb(toolCall.arguments)
                "offline_reputation_check" -> executeOfflineReputationCheck(toolCall.arguments)
                "brand_mismatch_check" -> executeBrandMismatchCheck(toolCall.arguments)
                "whois_lookup" -> executeWhoisLookup(toolCall.arguments)
                "compare_official_site" -> executeCompareOfficialSite(toolCall.arguments)
                else -> "Unknown tool: ${toolCall.name}"
            }
        } catch (e: Exception) {
            "Tool execution error: ${e.message}"
        }
    }

    private fun parseJsonObject(arguments: String): JsonObject {
        return try {
            json.decodeFromString<JsonObject>(arguments)
        } catch (e: Exception) {
            JsonObject(emptyMap())
        }
    }

    private fun getString(args: JsonObject, key: String): String? {
        return try {
            args[key]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    private fun getStringArray(args: JsonObject, key: String): List<String> {
        return try {
            args[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun executeLookupAllowlist(arguments: String): String {
        val args = parseJsonObject(arguments)
        val sender = getString(args, "sender")
        val domain = getString(args, "domain")

        var found = false
        if (!sender.isNullOrBlank() && allowlistDao.containsSender(sender)) {
            found = true
        }
        if (!domain.isNullOrBlank() && allowlistDao.containsDomain(domain)) {
            found = true
        }

        return if (found) "Allowlist match found. SAFE." else "Not in allowlist."
    }

    private suspend fun executeSearchPersonalDb(arguments: String): String {
        val args = parseJsonObject(arguments)
        val sender = getString(args, "sender") ?: return "Missing sender parameter."
        val smsPrefix = getString(args, "sms_prefix") ?: return "Missing sms_prefix parameter."
        val hash = HashUtil.hashSms(sender, smsPrefix)
        val entry = historyDao.get(hash)
        return if (entry != null) {
            "evidence: Previously seen SMS with verdict '${entry.verdict}' (confidence: ${entry.confidence})."
        } else {
            "No match found in personal database."
        }
    }

    private suspend fun executeOfflineReputationCheck(arguments: String): String {
        val args = parseJsonObject(arguments)
        val urls = getStringArray(args, "urls")
        if (urls.isEmpty()) return "No URLs provided."

        val domains = FastPathFilter.extractDomains(urls)
        val badDomains = domains.filter { domain ->
            reputationDb?.isScam(domain) == true
        }

        return if (badDomains.isNotEmpty()) {
            "evidence: URLs found in scam database: ${badDomains.joinToString(", ")}"
        } else {
            "No known bad URLs."
        }
    }

    private fun executeBrandMismatchCheck(arguments: String): String {
        val args = parseJsonObject(arguments)
        val smsText = getString(args, "sms_text") ?: return "Missing sms_text parameter."
        val urls = getStringArray(args, "urls")

        val mismatch = BrandMismatchHeuristic.check(smsText, urls, officialSites)
        return if (mismatch != null) {
            "evidence: $mismatch"
        } else {
            "No brand mismatch detected."
        }
    }

    private suspend fun executeWhoisLookup(arguments: String): String {
        val args = parseJsonObject(arguments)
        val domain = getString(args, "domain") ?: return "Missing domain parameter."

        if (proxyClient == null || !proxyClient.isAvailable()) {
            return "WHOIS unavailable (offline)."
        }

        return try {
            val result = proxyClient.whois(domain)
            if (result.creationDate != null) {
                val daysSince = java.time.temporal.ChronoUnit.DAYS.between(
                    result.creationDate, java.time.LocalDate.now()
                )
                if (daysSince <= 30) {
                    "evidence: Domain registered very recently (${result.creationDate}, $daysSince days ago)."
                } else {
                    "Domain age: older than 30 days."
                }
            } else {
                "WHOIS data incomplete — creation date unknown."
            }
        } catch (e: Exception) {
            "WHOIS failed: ${e.message}"
        }
    }

    private fun executeCompareOfficialSite(arguments: String): String {
        val args = parseJsonObject(arguments)
        val claimedEntity = getString(args, "claimed_entity") ?: return "Missing claimed_entity parameter."
        val linkedDomain = getString(args, "linked_domain") ?: return "Missing linked_domain parameter."

        val officialDomain = officialSites.lookupOfficialDomain(claimedEntity)
            ?: return "Unknown entity: $claimedEntity — cannot verify."

        return if (DomainMatchUtil.domainMatchesOfficial(linkedDomain, officialDomain)) {
            "Domain seems to match official site."
        } else {
            "evidence: Link domain $linkedDomain does not match official site $officialDomain for $claimedEntity."
        }
    }
}
