package com.snapshield.data.mock

import com.snapshield.data.model.ClassificationResult
import com.snapshield.data.model.DeepCheckUpdate
import com.snapshield.data.model.EvidenceItem
import com.snapshield.data.model.DeepCheckVerdict
import com.snapshield.domain.service.DeepCheckListener
import com.snapshield.domain.service.DeepCheckSession
import com.snapshield.domain.service.SnapShieldAI
import android.content.Context
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class MockSnapShieldAI : SnapShieldAI {

    private var demoMode = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun initialize(context: Context, callback: (Boolean) -> Unit) {
        callback(true)
    }

    override fun classifySMS(smsText: String, callback: (ClassificationResult) -> Unit) {
        scope.launch {
            delay(30)
            callback(classifyByPatterns(smsText))
        }
    }

    override fun startDeepCheck(smsText: String, listener: DeepCheckListener): DeepCheckSession {
        val session = MockDeepCheckSession(smsText, listener, demoMode)
        session.start()
        return session
    }

    override fun enableDemoMode() {
        demoMode = true
    }

    private fun classifyByPatterns(smsText: String): ClassificationResult {
        val lowerText = smsText.lowercase()

        val scamKeywords = listOf(
            "urgent", "verify", "account", "suspended", "click here",
            "congratulations", "won", "prize", "lottery", "winner",
            "bank", "password", "otp", "verify your", "immediately",
            "limited time", "act now", "free gift", "claim now"
        )

        val suspiciousKeywords = listOf(
            "discount", "offer", "limited", "exclusive", "special",
            "meeting", "interview", "job", "salary", "hiring"
        )

        val scamScore = scamKeywords.count { lowerText.contains(it) }
        val suspiciousScore = suspiciousKeywords.count { lowerText.contains(it) }

        return when {
            scamScore >= 2 -> ClassificationResult(
                label = "SCAM",
                confidence = 0.85f + (scamScore * 0.03f).coerceAtMost(0.15f),
                riskScore = (80 + scamScore * 4).coerceAtMost(100),
                reasoning = "Matches ${scamScore} scam patterns: ${scamKeywords.filter { lowerText.contains(it) }.take(3).joinToString(", ")}",
                isScam = true
            )
            scamScore == 1 || suspiciousScore >= 2 -> ClassificationResult(
                label = "SUSPICIOUS",
                confidence = 0.6f + (suspiciousScore * 0.05f).coerceAtMost(0.2f),
                riskScore = (40 + suspiciousScore * 8).coerceAtMost(75),
                reasoning = "Contains suspicious language patterns",
                isScam = false
            )
            else -> ClassificationResult(
                label = "SAFE",
                confidence = 0.9f,
                riskScore = 5,
                reasoning = "No scam indicators detected",
                isScam = false
            )
        }
    }
}

class MockDeepCheckSession(
    private val smsText: String,
    private val listener: DeepCheckListener,
    private val demoMode: Boolean
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
            val steps = if (demoMode) getDemoSteps() else getDefaultSteps()

            for ((index, step) in steps.withIndex()) {
                if (!_isActive.get()) return

                val progress = ((index + 1) * 100) / steps.size
                listener.onUpdate(DeepCheckUpdate.Step(step.message, progress))

                delay(step.delayMs)

                step.evidence?.let { evidence ->
                    if (_isActive.get()) {
                        listener.onUpdate(DeepCheckUpdate.FoundEvidence(evidence))
                        delay(200)
                    }
                }
            }

            if (_isActive.get()) {
                val verdict = if (demoMode) getDemoVerdict() else getDefaultVerdict()
                listener.onUpdate(DeepCheckUpdate.FinalVerdict(verdict))
            }
        } catch (e: Exception) {
            if (_isActive.get()) {
                listener.onUpdate(DeepCheckUpdate.Error(e.message ?: "Unknown error"))
            }
        } finally {
            _isActive.set(false)
        }
    }

    override fun cancel() {
        _isActive.set(false)
        scope.cancel()
    }

    private fun getDefaultSteps(): List<InvestigationStep> = listOf(
        InvestigationStep("Extracting sender details...", 800, null),
        InvestigationStep("Performing WHOIS lookup...", 1200,
            EvidenceItem("WHOIS", "Checking domain registration details", "LOW")),
        InvestigationStep("Checking domain age...", 1000,
            EvidenceItem("WHOIS", "Domain registered 3 days ago", "HIGH")),
        InvestigationStep("Comparing with official domains...", 1500,
            EvidenceItem("Pattern Match", "Domain does not match official organization", "HIGH")),
        InvestigationStep("Searching scam databases...", 1200,
            EvidenceItem("Scam DB", "URL found in phishing database", "CRITICAL")),
        InvestigationStep("Analyzing message patterns...", 800, null),
        InvestigationStep("Forming conclusion...", 600, null)
    )

    private fun getDemoSteps(): List<InvestigationStep> = listOf(
        InvestigationStep("Extracting link and sender details...", 600, null),
        InvestigationStep("Performing WHOIS lookup...", 1000,
            EvidenceItem("WHOIS", "Checking domain registration details", "LOW")),
        InvestigationStep("Domain registered 2 days ago in Russia", 800,
            EvidenceItem("WHOIS", "Domain registered 2 days ago in Russia", "CRITICAL")),
        InvestigationStep("Comparing with official HSBC domain...", 1200,
            EvidenceItem("Pattern Match", "Real HSBC domain is hsbc.com", "HIGH")),
        InvestigationStep("Searching scam databases...", 1000,
            EvidenceItem("Scam DB", "URL found in SmishTank", "CRITICAL")),
        InvestigationStep("Analyzing message urgency patterns...", 600,
            EvidenceItem("NLP", "Urgent language detected: 'immediately', 'suspended'", "MEDIUM")),
        InvestigationStep("Forming conclusion...", 400, null)
    )

    private fun getDefaultVerdict(): DeepCheckVerdict = DeepCheckVerdict(
        isScam = true,
        summary = "Scam detected with high confidence",
        threatType = "credential_theft",
        evidence = listOf(
            EvidenceItem("WHOIS", "Domain registered 3 days ago", "HIGH"),
            EvidenceItem("Pattern Match", "Does not match official domain", "HIGH"),
            EvidenceItem("Scam DB", "Found in phishing database", "CRITICAL")
        ),
        recommendedActions = listOf(
            "Block sender",
            "Report as spam",
            "Delete message",
            "Do not click any links"
        )
    )

    private fun getDemoVerdict(): DeepCheckVerdict = DeepCheckVerdict(
        isScam = true,
        summary = "Credential-theft scam detected",
        threatType = "credential_theft",
        evidence = listOf(
            EvidenceItem("WHOIS", "Domain registered 2 days ago in Russia", "CRITICAL"),
            EvidenceItem("Pattern Match", "Real HSBC domain is hsbc.com", "HIGH"),
            EvidenceItem("Scam DB", "URL found in SmishTank", "CRITICAL"),
            EvidenceItem("NLP", "Urgent language patterns detected", "MEDIUM")
        ),
        recommendedActions = listOf(
            "Block sender immediately",
            "Report as phishing",
            "Delete message",
            "Do not click any links",
            "Contact your bank if you entered any credentials"
        )
    )
}

data class InvestigationStep(
    val message: String,
    val delayMs: Long,
    val evidence: EvidenceItem?
)
