package com.smssentry.deepcheck.session

import com.smssentry.data.model.DeepCheckUpdate
import com.smssentry.data.model.DeepCheckVerdict
import com.smssentry.data.model.EvidenceItem
import com.smssentry.deepcheck.data.AllowlistDao
import com.smssentry.deepcheck.data.HistoryDao
import com.smssentry.deepcheck.data.OfficialSitesRepository
import com.smssentry.deepcheck.data.ReputationDb
import com.smssentry.deepcheck.data.HistoryEntry
import com.smssentry.deepcheck.model.LlmInferenceEngine
import com.smssentry.deepcheck.model.VerdictParser
import com.smssentry.deepcheck.prefilter.FastPathFilter
import com.smssentry.deepcheck.proxy.PrivacyProxyClient
import com.smssentry.deepcheck.tools.ToolDefinitions
import com.smssentry.deepcheck.tools.ToolExecutor
import com.smssentry.deepcheck.util.HashUtil
import com.smssentry.domain.service.DeepCheckSession as DeepCheckSessionInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DeepCheckSession(
    private val engine: LlmInferenceEngine?,
    private val allowlistDao: AllowlistDao,
    private val historyDao: HistoryDao,
    private val reputationDb: ReputationDb?,
    private val officialSites: OfficialSitesRepository,
    private val proxyClient: PrivacyProxyClient?,
    private val smsText: String,
    private val smsSender: String,
    private val listener: com.smssentry.domain.service.DeepCheckListener
) : DeepCheckSessionInterface {

    private val evidenceList = mutableListOf<String>()
    @Volatile private var isCancelled = false
    @Volatile private var _isActive = false
    override val isActive: Boolean get() = _isActive
    private var stepIndex = 0
    private val totalSteps = 8

    override fun cancel() {
        isCancelled = true
        _isActive = false
    }

    suspend fun run() {
        _isActive = true
        evidenceList.clear()
        isCancelled = false
        stepIndex = 0

        try {
            val pre = FastPathFilter.filter(smsText, smsSender, allowlistDao, historyDao)
            if (pre.verdict != null) {
                emitVerdict(
                    DeepCheckVerdict(
                        isScam = pre.verdict == "SCAM",
                        summary = pre.reason ?: "Determined by fast-path analysis.",
                        threatType = null,
                        evidence = emptyList(),
                        recommendedActions = if (pre.verdict == "SCAM") listOf("Do not interact with this message.") else emptyList()
                    )
                )
                return
            }

            emitStep("Analyzing SMS content...")

            if (engine == null) {
                emitStep("Model unavailable — running rule-based analysis.")
                runRuleBasedAnalysis()
                return
            }

            emitStep("Loading AI model...")
            try {
                withTimeoutOrNull(30_000L) {
                    engine.load()
                } ?: run {
                    emitStep("Model loading timed out — running rule-based analysis.")
                    runRuleBasedAnalysis()
                    return
                }
            } catch (e: Exception) {
                emitStep("Model load failed: ${e.message} — running rule-based analysis.")
                runRuleBasedAnalysis()
                return
            }

            val conversation = StringBuilder()
            conversation.appendLine("System: $SYSTEM_PROMPT")
            conversation.appendLine("User: SMS from $smsSender: \"$smsText\"")

            var turn = 0
            val maxTurns = 4
            val seenToolCalls = mutableSetOf<String>()

            while (turn < maxTurns && !isCancelled) {
                val prompt = conversation.toString()
                val response = withTimeoutOrNull(8_000L) {
                    engine.generate(prompt)
                }

                if (response == null) {
                    emitStep("Model timed out — running rule-based analysis.")
                    runRuleBasedAnalysis()
                    return
                }

                val json = VerdictParser.extractJson(response)
                if (json != null) {
                    val verdict = VerdictParser.parseVerdict(json)
                    if (verdict != null) {
                        emitVerdict(mapVerdict(verdict))
                        return
                    }
                }

                val toolCall = parseToolCall(response)
                if (toolCall != null) {
                    val callKey = "${toolCall.first}:${toolCall.second}"
                    if (!seenToolCalls.add(callKey)) {
                        conversation.appendLine("Assistant: $response")
                        conversation.appendLine("Tool: You already called this tool with these arguments. Use the prior result or call a different tool.")
                        turn++
                        continue
                    }

                    emitStep(describeToolCall(toolCall.first))

                    val toolExecutor = ToolExecutor(
                        allowlistDao, historyDao, reputationDb, officialSites, proxyClient
                    )
                    val toolResult = withTimeoutOrNull(5_000L) {
                        toolExecutor.executeByName(toolCall.first, toolCall.second)
                    } ?: "Tool timed out."

                    val truncated = toolResult.take(200)
                    if (toolResult.startsWith("evidence:")) {
                        val evidence = toolResult.removePrefix("evidence:").trim()
                        evidenceList.add(evidence)
                        emitEvidence(evidence)
                    }
                    conversation.appendLine("Assistant: $response")
                    conversation.appendLine("Tool: $truncated")
                    turn++
                } else {
                    conversation.appendLine("Assistant: $response")
                    conversation.appendLine("User: $RETRY_JSON_PROMPT")
                    turn++
                }
            }
            runRuleBasedAnalysis()
        } finally {
            engine?.close()
            _isActive = false
        }
    }

    private fun parseToolCall(response: String): Pair<String, String>? {
        val jsonMatch = Regex("""\{[^}]+\}""").find(response) ?: return null
        val json = jsonMatch.value

        return try {
            val toolNameMatch = Regex(""""tool_name"\s*:\s*"(\w+)"""").find(json)
            val argumentsMatch = Regex(""""arguments"\s*:\s*(\{[^}]+\})""").find(json)
            if (toolNameMatch != null && argumentsMatch != null) {
                Pair(toolNameMatch.groupValues[1], argumentsMatch.groupValues[1])
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun emitStep(description: String) {
        stepIndex++
        val progress = ((stepIndex.toFloat() / totalSteps) * 100).toInt().coerceAtMost(99)
        listener.onUpdate(DeepCheckUpdate.Step(description, progress))
    }

    private fun emitEvidence(detail: String) {
        listener.onUpdate(
            DeepCheckUpdate.FoundEvidence(
                EvidenceItem(
                    source = "Deep Check",
                    detail = detail,
                    severity = "HIGH"
                )
            )
        )
    }

    private fun emitVerdict(verdict: DeepCheckVerdict) {
        listener.onUpdate(DeepCheckUpdate.FinalVerdict(verdict))
        recordHistory(verdict)
    }

    private fun recordHistory(verdict: DeepCheckVerdict) {
        try {
            val hash = HashUtil.hashSms(smsSender, smsText.take(10))
            val verdictStr = when {
                verdict.isScam -> "SCAM"
                verdict.evidence.size >= 2 -> "SCAM"
                verdict.evidence.isNotEmpty() -> "SUSPICIOUS"
                else -> "SAFE"
            }
            val entry = HistoryEntry(
                hash = hash,
                verdict = verdictStr,
                confidence = if (verdict.isScam) 0.85f else 0.5f,
                timestamp = System.currentTimeMillis(),
                evidenceCount = verdict.evidence.size
            )
            CoroutineScope(Dispatchers.IO).launch {
                try { historyDao.insert(entry) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun emitFallbackVerdict() {
        val verdict = if (evidenceList.size >= 2) "SCAM" else "SUSPICIOUS"
        emitVerdict(
            DeepCheckVerdict(
                isScam = verdict == "SCAM",
                summary = "Based on available evidence, this message appears $verdict.",
                threatType = null,
                evidence = evidenceList.map {
                    EvidenceItem(source = "Rule-Based", detail = it, severity = "MEDIUM")
                },
                recommendedActions = if (verdict == "SCAM") {
                    listOf("Do not click any links.", "Block the sender.", "Report as spam.")
                } else {
                    listOf("Exercise caution.", "Verify with the claimed organization directly.")
                }
            )
        )
    }

    private suspend fun runRuleBasedAnalysis() {
        val toolExecutor = ToolExecutor(
            allowlistDao, historyDao, reputationDb, officialSites, proxyClient
        )
        val urls = com.smssentry.deepcheck.prefilter.FastPathFilter.extractUrls(smsText)
        val domains = com.smssentry.deepcheck.prefilter.FastPathFilter.extractDomains(urls)
        val urlsJson = urls.joinToString(",") { "\"$it\"" }

        emitStep("Checking URLs against scam reputation database...")
        val repResult = withTimeoutOrNull(5_000L) {
            toolExecutor.executeByName("offline_reputation_check", """{"urls":[$urlsJson]}""")
        }
        if (repResult != null && repResult.startsWith("evidence:")) {
            val evidence = repResult.removePrefix("evidence:").trim()
            evidenceList.add(evidence)
            emitEvidence(evidence)
        }

        if (domains.isNotEmpty()) {
            emitStep("Performing WHOIS domain lookup...")
            for (domain in domains.take(2)) {
                if (isCancelled) return
                val whoisResult = withTimeoutOrNull(5_000L) {
                    toolExecutor.executeByName("whois_lookup", """{"domain":"$domain"}""")
                }
                if (whoisResult != null && whoisResult.startsWith("evidence:")) {
                    val evidence = whoisResult.removePrefix("evidence:").trim()
                    evidenceList.add(evidence)
                    emitEvidence(evidence)
                }
            }
        }

        emitStep("Checking for brand impersonation...")
        val mismatchResult = withTimeoutOrNull(5_000L) {
            toolExecutor.executeByName("brand_mismatch_check", """{"sms_text":"$smsText","urls":[$urlsJson]}""")
        }
        if (mismatchResult != null && mismatchResult.startsWith("evidence:")) {
            val evidence = mismatchResult.removePrefix("evidence:").trim()
            evidenceList.add(evidence)
            emitEvidence(evidence)
        }

        for (domain in domains.take(2)) {
            if (isCancelled) return
            emitStep("Comparing $domain with official sites...")
            val claimedEntity = officialSites.findMatchingBrand(smsText)
            if (claimedEntity != null) {
                val compareResult = withTimeoutOrNull(5_000L) {
                    toolExecutor.executeByName("compare_official_site", """{"claimed_entity":"$claimedEntity","linked_domain":"$domain"}""")
                }
                if (compareResult != null && compareResult.startsWith("evidence:")) {
                    val evidence = compareResult.removePrefix("evidence:").trim()
                    evidenceList.add(evidence)
                    emitEvidence(evidence)
                }
            }
        }

        emitFallbackVerdict()
    }

    private fun mapVerdict(v: com.smssentry.deepcheck.model.VerdictJson): DeepCheckVerdict {
        val isScam = v.verdict == "SCAM"
        return DeepCheckVerdict(
            isScam = isScam,
            summary = v.reasoning,
            threatType = if (isScam) "unknown" else null,
            evidence = v.evidence.map { detail ->
                EvidenceItem(source = "AI Analysis", detail = detail, severity = if (isScam) "HIGH" else "LOW")
            },
            recommendedActions = when (v.verdict) {
                "SCAM" -> listOf("Do not click any links.", "Block the sender.", "Report as spam.")
                "SUSPICIOUS" -> listOf("Exercise caution.", "Verify with the claimed organization directly.")
                else -> emptyList()
            }
        )
    }

    companion object {
        fun describeToolCall(toolName: String): String = when (toolName) {
            "lookup_allowlist" -> "Checking if sender or domain is on the allowlist..."
            "search_personal_db" -> "Searching personal scam history database..."
            "offline_reputation_check" -> "Checking URLs against scam reputation database..."
            "brand_mismatch_check" -> "Checking for brand impersonation..."
            "whois_lookup" -> "Performing WHOIS domain lookup..."
            "compare_official_site" -> "Comparing with official website..."
            else -> "Running $toolName..."
        }
    }
}