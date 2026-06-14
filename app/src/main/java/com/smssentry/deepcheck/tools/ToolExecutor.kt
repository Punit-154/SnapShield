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
        return executeByName(toolCall.name, toolCall.arguments)
    }

    suspend fun executeByName(toolName: String, arguments: String): String {
        return try {
            when (toolName) {
                "whois", "whois_lookup" -> executeWhoisLookup(arguments)
                "search_scam_db", "offline_reputation_check" -> executeOfflineReputationCheck(arguments)
                "official_site", "compare_official_site" -> executeCompareOfficialSite(arguments)
                "brand_mismatch", "brand_mismatch_check" -> executeBrandMismatchCheck(arguments)
                "fetch_page" -> FetchPageTool(proxyClient).fetch(arguments.trim())
                "lookup_allowlist" -> executeLookupAllowlist(arguments)
                "search_personal_db" -> executeSearchPersonalDb(arguments)
                else -> "Unknown tool: $toolName"
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

    private fun getParam(arguments: String, key: String): String {
        val trimmed = arguments.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val args = parseJsonObject(trimmed)
            return getString(args, key) ?: ""
        }
        return trimmed
    }

    private suspend fun executeLookupAllowlist(arguments: String): String {
        val trimmed = arguments.trim()
        val (sender, domain) = if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val args = parseJsonObject(trimmed)
            Pair(getString(args, "sender"), getString(args, "domain"))
        } else {
            Pair(trimmed, trimmed)
        }

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
        val trimmed = arguments.trim()
        val (sender, smsPrefix) = if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val args = parseJsonObject(trimmed)
            Pair(getString(args, "sender") ?: "", getString(args, "sms_prefix") ?: "")
        } else {
            val parts = trimmed.split('|', limit = 2)
            if (parts.size == 2) Pair(parts[0].trim(), parts[1].trim()) else Pair("", trimmed)
        }
        if (sender.isBlank() || smsPrefix.isBlank()) return "Missing sender or sms_prefix parameter."
        val hash = HashUtil.hashSms(sender, smsPrefix)
        val entry = historyDao.get(hash)
        return if (entry != null) {
            "evidence: Previously seen SMS with verdict '${entry.verdict}' (confidence: ${entry.confidence})."
        } else {
            "No match found in personal database."
        }
    }

    private suspend fun executeOfflineReputationCheck(arguments: String): String {
        val trimmed = arguments.trim()
        val urls = if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val args = parseJsonObject(trimmed)
            getStringArray(args, "urls")
        } else {
            listOf(trimmed)
        }
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
        val trimmed = arguments.trim()
        val (smsText, urls) = if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val args = parseJsonObject(trimmed)
            Pair(getString(args, "sms_text") ?: "", getStringArray(args, "urls"))
        } else {
            Pair(trimmed, FastPathFilter.extractUrls(trimmed))
        }

        val mismatch = BrandMismatchHeuristic.check(smsText, urls, officialSites)
        return if (mismatch != null) {
            "evidence: $mismatch"
        } else {
            "No brand mismatch detected."
        }
    }

    private suspend fun executeWhoisLookup(arguments: String): String {
        val domain = getParam(arguments, "domain")
        if (domain.isBlank()) return "Missing domain parameter."

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
        val trimmed = arguments.trim()
        val (claimedEntity, linkedDomain) = if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val args = parseJsonObject(trimmed)
            Pair(getString(args, "claimed_entity") ?: "", getString(args, "linked_domain") ?: "")
        } else {
            val parts = trimmed.split('|', limit = 2)
            if (parts.size == 2) Pair(parts[0].trim(), parts[1].trim()) else Pair(trimmed, "")
        }
        if (claimedEntity.isBlank() || linkedDomain.isBlank()) return "Missing claimed_entity or linked_domain parameter."

        val officialDomain = officialSites.lookupOfficialDomain(claimedEntity)
            ?: return "Unknown entity: $claimedEntity — cannot verify."

        return if (DomainMatchUtil.domainMatchesOfficial(linkedDomain, officialDomain)) {
            "Domain seems to match official site."
        } else {
            "evidence: Link domain $linkedDomain does not match official site $officialDomain for $claimedEntity."
        }
    }
}
