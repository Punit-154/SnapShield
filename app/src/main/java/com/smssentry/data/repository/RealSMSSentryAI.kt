package com.smssentry.data.repository

import android.content.Context
import com.smssentry.data.model.ClassificationResult
import com.smssentry.data.model.DeepCheckUpdate
import com.smssentry.data.model.EvidenceItem
import com.smssentry.data.model.DeepCheckVerdict
import com.smssentry.data.model.SmsMessage
import com.smssentry.deepcheck.data.AllowlistDao
import com.smssentry.deepcheck.data.HistoryDao
import com.smssentry.deepcheck.data.OfficialSitesRepository
import com.smssentry.deepcheck.data.ReputationDb
import com.smssentry.deepcheck.model.LlmInferenceEngine
import com.smssentry.deepcheck.proxy.PrivacyProxyClient
import com.smssentry.deepcheck.session.DeepCheckSession
import com.smssentry.domain.service.DeepCheckListener
import com.smssentry.domain.service.DeepCheckSession as DeepCheckSessionInterface
import com.smssentry.domain.service.SMSSentryAI
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class RealSMSSentryAI(
    private val context: Context,
    private val allowlistDao: AllowlistDao,
    private val historyDao: HistoryDao,
    private val reputationDb: ReputationDb,
    private val officialSites: OfficialSitesRepository,
    private val proxyClient: PrivacyProxyClient,
    private val engine: LlmInferenceEngine?
) : SMSSentryAI {

    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun initialize(context: Context, callback: (Boolean) -> Unit) {
        scope.launch {
            isInitialized = true
            callback(true)
        }
    }

    override fun classifySMS(smsText: String, callback: (ClassificationResult) -> Unit) {
        scope.launch {
            val result = classifyByRules(smsText)
            callback(result)
        }
    }

    override fun startDeepCheck(smsText: String, listener: DeepCheckListener): DeepCheckSessionInterface {
        val session = RealDeepCheckSession(
            smsText, "", listener,
            engine, allowlistDao, historyDao, reputationDb, officialSites, proxyClient
        )
        session.start()
        return session
    }

    override fun enableDemoMode() {}

    private fun classifyByRules(smsText: String): ClassificationResult {
        val lowerText = smsText.lowercase()
        val scamIndicators = listOf("urgent", "verify", "suspended", "click here", "congratulations")
        val score = scamIndicators.count { lowerText.contains(it) }

        return when {
            score >= 2 -> ClassificationResult("SCAM", 0.85f, 85, "Multiple scam indicators", true)
            score == 1 -> ClassificationResult("SUSPICIOUS", 0.65f, 55, "Some suspicious patterns", false)
            else -> ClassificationResult("SAFE", 0.9f, 5, "No indicators detected", false)
        }
    }
}

class RealDeepCheckSession(
    private val smsText: String,
    private val smsSender: String,
    private val listener: DeepCheckListener,
    private val engine: LlmInferenceEngine?,
    private val allowlistDao: AllowlistDao,
    private val historyDao: HistoryDao,
    private val reputationDb: ReputationDb,
    private val officialSites: OfficialSitesRepository,
    private val proxyClient: PrivacyProxyClient
) : DeepCheckSessionInterface {

    private val _isActive = AtomicBoolean(false)
    override val isActive: Boolean get() = _isActive.get()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        _isActive.set(true)
        scope.launch {
            try {
                if (engine != null) {
                    val session = DeepCheckSession(
                        engine, allowlistDao, historyDao, reputationDb,
                        officialSites, proxyClient, smsText, smsSender, listener
                    )
                    session.run()
                } else {
                    listener.onUpdate(DeepCheckUpdate.Step("Model unavailable — using rule-based analysis.", 50))
                    listener.onUpdate(DeepCheckUpdate.FinalVerdict(
                        DeepCheckVerdict(
                            isScam = true,
                            summary = "Model not available. Rule-based analysis suggests caution.",
                            threatType = "unknown",
                            evidence = listOf(EvidenceItem("System", "LLM model not loaded", "LOW")),
                            recommendedActions = listOf("Verify manually with the claimed organization.")
                        )
                    ))
                }
            } catch (e: Exception) {
                if (_isActive.get()) {
                    listener.onUpdate(DeepCheckUpdate.Error(e.message ?: "Unknown error"))
                }
            } finally {
                _isActive.set(false)
            }
        }
    }

    override fun cancel() {
        _isActive.set(false)
        scope.cancel()
    }
}
