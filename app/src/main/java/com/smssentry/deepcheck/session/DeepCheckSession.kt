package com.smssentry.deepcheck.session

import com.smssentry.data.model.DeepCheckUpdate
import com.smssentry.data.model.DeepCheckVerdict
import com.smssentry.data.model.EvidenceItem
import com.smssentry.deepcheck.data.AllowlistDao
import com.smssentry.deepcheck.data.HistoryDao
import com.smssentry.deepcheck.data.OfficialSitesRepository
import com.smssentry.deepcheck.data.ReputationDb
import com.smssentry.deepcheck.data.HistoryEntry
import com.smssentry.deepcheck.model.ChatMessage
import com.smssentry.deepcheck.model.LlmInferenceEngine
import com.smssentry.deepcheck.model.LlmResponse
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
            emitStep("Model unavailable — using rule-based analysis.")
            emitFallbackVerdict()
            return
        }

        val messages = mutableListOf(
            ChatMessage(role = "system", content = SYSTEM_PROMPT),
            ChatMessage(role = "user", content = "SMS from $smsSender: \"$smsText\"")
        )

        var turn = 0
        val maxTurns = 4
        val seenToolCalls = mutableSetOf<String>()

        while (turn < maxTurns && !isCancelled) {
            val response = withTimeoutOrNull(8_000L) {
                engine.generateResponseAsync(messages, tools = ToolDefinitions.toolList)
            }

            if (response == null) {
                emitStep("Model timed out — using rule-based analysis.")
                emitFallbackVerdict()
                return
            }

            when (response) {
                is LlmResponse.Text -> {
                    val json = VerdictParser.extractJson(response.text)
                    if (json != null) {
                        val verdict = VerdictParser.parseVerdict(json)
                        if (verdict != null) {
                            emitVerdict(mapVerdict(verdict))
                            return
                        }
                    }
                    messages.add(ChatMessage(role = "assistant", content = response.text))
                    messages.add(ChatMessage(role = "user", content = RETRY_JSON_PROMPT))
                    turn++
                }
                is LlmResponse.ToolCall -> {
                    val callKey = "${response.name}:${response.arguments}"
                    if (!seenToolCalls.add(callKey)) {
                        messages.add(ChatMessage(role = "assistant", toolCall = response))
                        messages.add(ChatMessage(
                            role = "tool",
                            content = "You already called this tool with these arguments. Use the prior result or call a different tool."
                        ))
                        turn++
                        continue
                    }

                    emitStep(describeToolCall(response.name))

                    val toolExecutor = ToolExecutor(
                        allowlistDao, historyDao, reputationDb, officialSites, proxyClient
                    )
                    val toolResult = withTimeoutOrNull(5_000L) {
                        toolExecutor.execute(response)
                    } ?: "Tool timed out."

                    val truncated = toolResult.take(200)
                    if (toolResult.startsWith("evidence:")) {
                        val evidence = toolResult.removePrefix("evidence:").trim()
                        evidenceList.add(evidence)
                        emitEvidence(evidence)
                    }
                    messages.add(ChatMessage(role = "assistant", toolCall = response))
                    messages.add(ChatMessage(role = "tool", content = truncated))
                    turn++
                }
                is LlmResponse.Error -> {
                    emitStep("Model error: ${response.error}")
                    emitFallbackVerdict()
                    return
                }
            }
        }
        emitFallbackVerdict()
        } finally {
            _isActive = false
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
                summary = "Limited analysis due to model constraints. Based on available evidence, this message appears $verdict.",
                threatType = null,
                evidence = evidenceList.map {
                    EvidenceItem(source = "Fallback", detail = it, severity = "MEDIUM")
                },
                recommendedActions = if (verdict == "SCAM") {
                    listOf("Do not click any links.", "Block the sender.", "Report as spam.")
                } else {
                    listOf("Exercise caution.", "Verify with the claimed organization directly.")
                }
            )
        )
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
