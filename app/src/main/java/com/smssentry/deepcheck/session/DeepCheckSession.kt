package com.smssentry.deepcheck.session

import android.content.Context
import com.smssentry.R
import com.smssentry.data.model.DeepCheckUpdate
import com.smssentry.data.model.DeepCheckVerdict
import com.smssentry.data.model.EvidenceItem
import com.smssentry.deepcheck.DeepCheckConfig
import com.smssentry.deepcheck.data.AllowlistDao
import com.smssentry.deepcheck.data.HistoryDao
import com.smssentry.deepcheck.data.OfficialSitesRepository
import com.smssentry.deepcheck.data.ReputationDb
import com.smssentry.deepcheck.data.HistoryEntry
import com.smssentry.deepcheck.model.LlmInferenceEngine
import com.smssentry.deepcheck.model.VerdictParser
import com.smssentry.deepcheck.model.ParsedEducationalVerdict
import com.smssentry.deepcheck.model.EducationalVerdictParser
import com.smssentry.deepcheck.prefilter.FastPathFilter
import com.smssentry.deepcheck.proxy.PrivacyProxyClient
import com.smssentry.deepcheck.tools.ToolExecutor
import com.smssentry.deepcheck.tools.ToolResult
import com.smssentry.deepcheck.util.HashUtil
import com.smssentry.domain.service.DeepCheckSession as DeepCheckSessionInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DeepCheckSession(
    private val context: Context,
    private val engine: LlmInferenceEngine?,
    private val allowlistDao: AllowlistDao,
    private val historyDao: HistoryDao,
    private val reputationDb: ReputationDb?,
    private val officialSites: OfficialSitesRepository,
    private val proxyClient: PrivacyProxyClient?,
    private val smsText: String,
    private val smsSender: String,
    private val listener: com.smssentry.domain.service.DeepCheckListener,
    private val applicationScope: CoroutineScope
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
            val pre = FastPathFilter.filter(context, smsText, smsSender, allowlistDao, historyDao)
            if (pre.verdict != null) {
                emitVerdict(
                    DeepCheckVerdict(
                        isScam = pre.verdict == "SCAM",
                        summary = pre.reason ?: context.getString(R.string.reason_fast_path),
                        threatType = null,
                        evidence = emptyList(),
                        recommendedActions = if (pre.verdict == "SCAM") {
                            listOf(context.getString(R.string.action_no_interact))
                        } else emptyList()
                    )
                )
                return
            }

            emitStep(context.getString(R.string.step_analyzing))

            if (engine == null) {
                emitStep(context.getString(R.string.step_rule_based))
                runRuleBasedAnalysis()
                return
            }

            emitStep(context.getString(R.string.step_loading_model))
            try {
                withTimeoutOrNull(DeepCheckConfig.MODEL_LOAD_TIMEOUT_MS) {
                    engine.load()
                } ?: run {
                    emitStep(context.getString(R.string.step_timeout))
                    runRuleBasedAnalysis()
                    return
                }
            } catch (e: Exception) {
                emitStep(context.getString(R.string.model_unavailable) + ": ${e.message}")
                runRuleBasedAnalysis()
                return
            }

            val session = engine.createSession(SYSTEM_PROMPT)
            try {
                val toolExecutor = ToolExecutor(allowlistDao, historyDao, reputationDb, officialSites, proxyClient)
                val seenToolCalls = mutableSetOf<String>()
                var turn = 0
                
                var response = withTimeoutOrNull(DeepCheckConfig.LLM_TURN_TIMEOUT_MS) {
                    session.sendTurn("Analyze this SMS from $smsSender:\n\"$smsText\"")
                }

                while (turn < DeepCheckConfig.MAX_AGENT_TURNS && !isCancelled) {
                    if (response == null) {
                        emitStep(context.getString(R.string.step_timeout))
                        runRuleBasedAnalysis()
                        return
                    }

                    // 1. Educational verdict tag check
                    val eduVerdict = EducationalVerdictParser.parse(response)
                    if (eduVerdict != null) {
                        emitVerdict(mapEducationalVerdict(eduVerdict))
                        return
                    }

                    // 2. Legacy JSON fallback check
                    val json = VerdictParser.extractJson(response)
                    val verdict = json?.let { VerdictParser.parseVerdict(it) }
                    if (verdict != null) {
                        emitVerdict(mapLegacyVerdict(verdict))
                        return
                    }

                    // 3. Tool call check
                    val toolCall = parseToolCall(response)
                    if (toolCall != null) {
                        val callKey = "${toolCall.first}:${toolCall.second}"
                        if (!seenToolCalls.add(callKey)) {
                            response = withTimeoutOrNull(DeepCheckConfig.LLM_TURN_TIMEOUT_MS) {
                                session.sendTurn("OBSERVATION: Already called. Use prior result or different tool.")
                            }
                            turn++
                            continue
                        }

                        emitStep(describeToolCall(toolCall.first, context))

                        val toolResult = withTimeoutOrNull(DeepCheckConfig.TOOL_EXECUTION_TIMEOUT_MS) {
                            toolExecutor.executeByName(toolCall.first, toolCall.second)
                        } ?: ToolResult.Error("Tool timed out.")

                        if (toolResult is ToolResult.Evidence) {
                            evidenceList.add(toolResult.message)
                            emitEvidence(toolResult.message)
                        }

                        response = withTimeoutOrNull(DeepCheckConfig.LLM_TURN_TIMEOUT_MS) {
                            session.sendTurn("OBSERVATION: ${toolResult.message.take(200)}")
                        }
                        turn++
                    } else {
                        response = withTimeoutOrNull(DeepCheckConfig.LLM_TURN_TIMEOUT_MS) {
                            session.sendTurn(RETRY_VERDICT_PROMPT)
                        }
                        turn++
                    }
                }
                runRuleBasedAnalysis()
            } finally {
                session.close()
            }
        } finally {
            _isActive = false
        }
    }

    private fun parseToolCall(response: String): Pair<String, String>? {
        val actionLine = response.lines().firstOrNull { it.trimStart().startsWith("ACTION:") }
        if (actionLine != null) {
            val content = actionLine.removePrefix("ACTION:").trim()
            val pipeIdx = content.indexOf('|')
            return if (pipeIdx >= 0) {
                Pair(content.substring(0, pipeIdx).trim(), content.substring(pipeIdx + 1).trim())
            } else {
                Pair(content.trim(), "")
            }
        }

        // Fallback: old JSON format
        val jsonMatch = Regex("""\{[^}]+\}""").find(response) ?: return null
        return try {
            val json = jsonMatch.value
            val name = Regex(""""tool_name"\s*:\s*"(\w+)"""").find(json)?.groupValues?.get(1)
            val args = Regex(""""arguments"\s*:\s*(\{[^}]+\})""").find(json)?.groupValues?.get(1)
            if (name != null && args != null) Pair(name, args) else null
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
            val hash = HashUtil.hashSms(smsSender, smsText)
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
            applicationScope.launch {
                try { historyDao.insert(entry) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun emitFallbackVerdict() {
        val verdict = if (evidenceList.size >= 2) "SCAM" else "SUSPICIOUS"
        emitVerdict(
            DeepCheckVerdict(
                isScam = verdict == "SCAM",
                summary = if (verdict == "SCAM") context.getString(R.string.summary_scam) else context.getString(R.string.summary_suspicious),
                threatType = null,
                evidence = evidenceList.map {
                    EvidenceItem(source = "Rule-Based", detail = it, severity = "MEDIUM")
                },
                recommendedActions = if (verdict == "SCAM") {
                    listOf(
                        context.getString(R.string.action_no_click),
                        context.getString(R.string.action_block),
                        context.getString(R.string.action_report)
                    )
                } else {
                    listOf(
                        context.getString(R.string.action_caution),
                        context.getString(R.string.action_verify_org)
                    )
                }
            )
        )
    }

    private suspend fun runRuleBasedAnalysis(llmContext: String? = null) {
        val toolExecutor = ToolExecutor(
            allowlistDao, historyDao, reputationDb, officialSites, proxyClient
        )
        val urls = com.smssentry.deepcheck.prefilter.FastPathFilter.extractUrls(smsText)
        val domains = com.smssentry.deepcheck.prefilter.FastPathFilter.extractDomains(urls)
        val urlsJson = urls.joinToString(",") { "\"$it\"" }

        emitStep(context.getString(R.string.step_checking_reputation))
        val repResult = withTimeoutOrNull(DeepCheckConfig.TOOL_EXECUTION_TIMEOUT_MS) {
            toolExecutor.executeByName("offline_reputation_check", """{"urls":[$urlsJson]}""")
        }
        if (repResult is ToolResult.Evidence) {
            evidenceList.add(repResult.message)
            emitEvidence(repResult.message)
        }

        if (domains.isNotEmpty()) {
            emitStep(context.getString(R.string.step_whois))
            for (domain in domains.take(2)) {
                if (isCancelled) return
                val whoisResult = withTimeoutOrNull(DeepCheckConfig.TOOL_EXECUTION_TIMEOUT_MS) {
                    toolExecutor.executeByName("whois_lookup", """{"domain":"$domain"}""")
                }
                if (whoisResult is ToolResult.Evidence) {
                    evidenceList.add(whoisResult.message)
                    emitEvidence(whoisResult.message)
                }
            }
        }

        emitStep(context.getString(R.string.step_brand))
        val mismatchResult = withTimeoutOrNull(DeepCheckConfig.TOOL_EXECUTION_TIMEOUT_MS) {
            toolExecutor.executeByName("brand_mismatch_check", """{"sms_text":"$smsText","urls":[$urlsJson]}""")
        }
        if (mismatchResult is ToolResult.Evidence) {
            evidenceList.add(mismatchResult.message)
            emitEvidence(mismatchResult.message)
        }

        for (domain in domains.take(2)) {
            if (isCancelled) return
            val claimedEntity = officialSites.findMatchingBrand(smsText)
            if (claimedEntity != null) {
                emitStep(context.getString(R.string.step_official_compare, domain))
                val compareResult = withTimeoutOrNull(DeepCheckConfig.TOOL_EXECUTION_TIMEOUT_MS) {
                    toolExecutor.executeByName("compare_official_site", """{"claimed_entity":"$claimedEntity","linked_domain":"$domain"}""")
                }
                if (compareResult is ToolResult.Evidence) {
                    evidenceList.add(compareResult.message)
                    emitEvidence(compareResult.message)
                }
            }
        }

        emitFallbackVerdict()
    }

    private fun mapLegacyVerdict(v: com.smssentry.deepcheck.model.VerdictJson): DeepCheckVerdict {
        val isScam = v.verdict == "SCAM"
        return DeepCheckVerdict(
            isScam = isScam,
            summary = v.reasoning,
            threatType = if (isScam) "unknown" else null,
            evidence = v.evidence.map { detail ->
                EvidenceItem(source = "AI Analysis", detail = detail, severity = if (isScam) "HIGH" else "LOW")
            },
            recommendedActions = when (v.verdict) {
                "SCAM" -> listOf(
                    context.getString(R.string.action_no_click),
                    context.getString(R.string.action_block),
                    context.getString(R.string.action_report)
                )
                "SUSPICIOUS" -> listOf(
                    context.getString(R.string.action_caution),
                    context.getString(R.string.action_verify_org)
                )
                else -> emptyList()
            }
        )
    }

    private fun mapEducationalVerdict(parsed: ParsedEducationalVerdict): DeepCheckVerdict {
        val isScam = parsed.verdictLabel == "SCAM"
        return DeepCheckVerdict(
            isScam = isScam || parsed.verdictLabel == "SUSPICIOUS",
            summary = parsed.explanation.take(150).let { if (parsed.explanation.length > 150) "$it..." else it },
            threatType = parsed.scamType.takeIf { it != "safe" },
            evidence = evidenceList.map { EvidenceItem("AI Analysis", it, if (isScam) "HIGH" else "MEDIUM") },
            recommendedActions = emptyList(),
            educationalExplanation = parsed.explanation
        )
    }

    companion object {
        fun describeToolCall(toolName: String, context: Context): String = when (toolName) {
            "whois", "whois_lookup"                      -> context.getString(R.string.step_whois)
            "search_scam_db", "offline_reputation_check" -> context.getString(R.string.step_checking_reputation)
            "fetch_page"                                 -> "Fetching page content..."
            "official_site", "compare_official_site"     -> context.getString(R.string.step_official_site)
            "brand_mismatch", "brand_mismatch_check"     -> context.getString(R.string.step_brand)
            "lookup_allowlist"                           -> context.getString(R.string.step_allowlist)
            "search_personal_db"                         -> context.getString(R.string.step_history)
            else -> context.getString(R.string.step_running_tool, toolName)
        }
    }
}
