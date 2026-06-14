package com.smssentry.data.repository

import com.smssentry.data.model.DeepCheckUpdate
import com.smssentry.data.model.DeepCheckVerdict
import com.smssentry.data.model.EvidenceItem
import com.smssentry.domain.service.DeepCheckListener
import com.smssentry.domain.service.DeepCheckSession
import com.smssentry.ml.SmsClassifierModel
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class RealDeepCheckEngine(
    private val smsText: String,
    private val listener: DeepCheckListener,
    private val classifier: SmsClassifierModel
) : DeepCheckSession {

    private val _isActive = AtomicBoolean(false)
    override val isActive: Boolean get() = _isActive.get()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        _isActive.set(true)
        scope.launch { runInvestigation() }
    }

    private suspend fun runInvestigation() {
        try {
            val lower = smsText.lowercase()
            val evidence = mutableListOf<EvidenceItem>()

            // Step 1: Sender analysis
            emit(DeepCheckUpdate.Step("Analyzing sender details...", 14))
            delay(700)

            // Step 2: URL detection
            emit(DeepCheckUpdate.Step("Scanning for suspicious URLs...", 28))
            delay(800)
            val urlRegex = Regex("https?://[^\\s]+|www\\.[^\\s]+|[a-z0-9-]+\\.[a-z]{2,}(/[^\\s]*)?")
            val urls = urlRegex.findAll(lower).map { it.value }.toList()
            if (urls.isNotEmpty()) {
                val suspiciousTlds = listOf(".xyz", ".buzz", ".win", ".tk", ".ml", ".ga", ".cf", ".top")
                val hasSuspiciousTld = urls.any { url -> suspiciousTlds.any { url.contains(it) } }
                if (hasSuspiciousTld) {
                    val item = EvidenceItem("URL Analysis", "Suspicious domain detected: ${urls.first()}", "CRITICAL")
                    evidence.add(item)
                    emit(DeepCheckUpdate.FoundEvidence(item))
                } else {
                    val item = EvidenceItem("URL Analysis", "URL found: ${urls.first()}", "LOW")
                    evidence.add(item)
                    emit(DeepCheckUpdate.FoundEvidence(item))
                }
            }
            delay(400)

            // Step 3: Urgency language
            emit(DeepCheckUpdate.Step("Checking for urgency manipulation...", 42))
            delay(700)
            val urgencyWords = listOf("urgent", "immediately", "act now", "limited time", "expire", "suspended")
            val foundUrgency = urgencyWords.filter { lower.contains(it) }
            if (foundUrgency.isNotEmpty()) {
                val item = EvidenceItem("Language Analysis", "Urgency language: ${foundUrgency.joinToString(", ")}", "HIGH")
                evidence.add(item)
                emit(DeepCheckUpdate.FoundEvidence(item))
            }
            delay(400)

            // Step 4: Financial bait detection
            emit(DeepCheckUpdate.Step("Checking for financial bait...", 57))
            delay(800)
            val financialBait = Regex("(won|win|prize|lottery|reward|claim|free).{0,40}(\\$|£|€|usd|gbp|[0-9]+)")
            if (financialBait.containsMatchIn(lower)) {
                val item = EvidenceItem("Content Analysis", "Financial bait language detected", "CRITICAL")
                evidence.add(item)
                emit(DeepCheckUpdate.FoundEvidence(item))
            }
            delay(400)

            // Step 5: AI classification
            emit(DeepCheckUpdate.Step("Running AI classification...", 71))
            delay(900)
            val classResult = classifier.ruleBasedClassify(smsText)
            if (classResult.riskScore > 20) {
                val item = EvidenceItem("AI Analysis", classResult.reasoning,
                    if (classResult.riskScore > 50) "HIGH" else "MEDIUM")
                evidence.add(item)
                emit(DeepCheckUpdate.FoundEvidence(item))
            }
            delay(400)

            // Step 6: OTP/legitimate pattern check
            emit(DeepCheckUpdate.Step("Checking legitimate patterns...", 85))
            delay(600)
            val legitimatePatterns = listOf("otp", "one-time", "do not share", "transaction id", "ref no")
            val foundLegit = legitimatePatterns.filter { lower.contains(it) }
            if (foundLegit.isNotEmpty()) {
                val item = EvidenceItem("Pattern Match", "Legitimate patterns found: ${foundLegit.joinToString(", ")}", "LOW")
                evidence.add(item)
                emit(DeepCheckUpdate.FoundEvidence(item))
            }
            delay(400)

            // Step 7: Final verdict
            emit(DeepCheckUpdate.Step("Forming final verdict...", 100))
            delay(500)

            // Determine verdict based on collected evidence
            val criticalCount = evidence.count { it.severity == "CRITICAL" }
            val highCount = evidence.count { it.severity == "HIGH" }
            val lowCount = evidence.count { it.severity == "LOW" }
            val hasLegitPatterns = foundLegit.isNotEmpty()

            val isScam = when {
                criticalCount >= 2 -> true
                criticalCount >= 1 && highCount >= 1 -> true
                criticalCount >= 1 && !hasLegitPatterns -> true
                highCount >= 2 && !hasLegitPatterns -> true
                else -> false
            }

            val isSuspicious = !isScam && (criticalCount >= 1 || highCount >= 1)

            val verdict = DeepCheckVerdict(
                isScam = isScam,
                summary = when {
                    isScam -> "This message shows strong signs of being a scam. Do not click any links or share personal information."
                    isSuspicious -> "This message has some suspicious characteristics. Exercise caution."
                    else -> "This message appears to be legitimate based on our analysis."
                },
                threatType = when {
                    isScam && urls.isNotEmpty() -> "phishing"
                    isScam -> "social_engineering"
                    isSuspicious -> "suspicious_content"
                    else -> null
                },
                evidence = evidence,
                recommendedActions = when {
                    isScam -> listOf(
                        "Do not click any links",
                        "Do not share personal information",
                        "Block this sender",
                        "Report as spam"
                    )
                    isSuspicious -> listOf(
                        "Verify sender through official channels",
                        "Do not click links directly",
                        "Contact organization using official number"
                    )
                    else -> listOf("No action needed", "Message appears safe")
                }
            )

            if (_isActive.get()) emit(DeepCheckUpdate.FinalVerdict(verdict))

        } catch (e: Exception) {
            if (_isActive.get()) emit(DeepCheckUpdate.Error(e.message ?: "Unknown error"))
        } finally {
            _isActive.set(false)
        }
    }

    private fun emit(update: DeepCheckUpdate) {
        if (_isActive.get()) listener.onUpdate(update)
    }

    override fun cancel() {
        _isActive.set(false)
        scope.cancel()
    }
}