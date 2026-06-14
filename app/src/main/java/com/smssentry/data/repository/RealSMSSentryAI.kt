package com.smssentry.data.repository

import android.content.Context
import com.smssentry.R
import com.smssentry.data.model.ClassificationResult
import com.smssentry.data.model.DeepCheckUpdate
import com.smssentry.deepcheck.data.*
import com.smssentry.deepcheck.model.LlmInferenceEngine
import com.smssentry.deepcheck.proxy.PrivacyProxyClient
import com.smssentry.deepcheck.session.DeepCheckSession
import com.smssentry.di.ApplicationScope
import com.smssentry.domain.service.DeepCheckListener
import com.smssentry.domain.service.DeepCheckSession as DeepCheckSessionInterface
import com.smssentry.domain.service.SMSSentryAI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealSMSSentryAI @Inject constructor(
    @ApplicationContext private val context: Context,
    private val allowlistDao: AllowlistDao,
    private val historyDao: HistoryDao,
    private val reputationDb: ReputationDb,
    private val officialSites: OfficialSitesRepository,
    private val proxyClient: PrivacyProxyClient,
    private val modelRepository: ModelRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) : SMSSentryAI {

    private var isInitialized = false

    override fun initialize(context: Context, callback: (Boolean) -> Unit) {
        applicationScope.launch {
            modelRepository.ensureReady()
            isInitialized = true
            callback(true)
        }
    }

    override fun classifySMS(smsText: String, callback: (ClassificationResult) -> Unit) {
        applicationScope.launch {
            val result = classifyByRules(smsText)
            callback(result)
        }
    }

    override fun startDeepCheck(smsText: String, listener: DeepCheckListener): DeepCheckSessionInterface {
        val session = RealDeepCheckSession(
            context, smsText, "", listener,
            modelRepository.getEngine(), allowlistDao, historyDao, reputationDb, officialSites, proxyClient,
            applicationScope
        )
        session.start()
        return session
    }

    override fun enableDemoMode() {
        // Implementation for demo mode if needed
    }

    private fun classifyByRules(smsText: String): ClassificationResult {
        val lowerText = smsText.lowercase()
        val scamIndicators = listOf("urgent", "verify", "suspended", "click here", "congratulations")
        val score = scamIndicators.count { lowerText.contains(it) }

        return when {
            score >= 2 -> ClassificationResult(
                "SCAM",
                0.85f,
                85,
                context.getString(R.string.reason_multiple_indicators),
                true
            )
            score == 1 -> ClassificationResult(
                "SUSPICIOUS",
                0.65f,
                55,
                context.getString(R.string.reason_some_indicators),
                false
            )
            else -> ClassificationResult(
                "SAFE",
                0.9f,
                5,
                context.getString(R.string.reason_no_indicators),
                false
            )
        }
    }
}

class RealDeepCheckSession(
    private val context: Context,
    private val smsText: String,
    private val smsSender: String,
    private val listener: DeepCheckListener,
    private val engine: LlmInferenceEngine?,
    private val allowlistDao: AllowlistDao,
    private val historyDao: HistoryDao,
    private val reputationDb: ReputationDb,
    private val officialSites: OfficialSitesRepository,
    private val proxyClient: PrivacyProxyClient,
    private val applicationScope: CoroutineScope
) : DeepCheckSessionInterface {

    private val _isActive = AtomicBoolean(false)
    override val isActive: Boolean get() = _isActive.get()

    private var sessionJob: Job? = null

    fun start() {
        _isActive.set(true)
        sessionJob = applicationScope.launch {
            try {
                val session = DeepCheckSession(
                    context = context,
                    engine = engine,
                    allowlistDao = allowlistDao,
                    historyDao = historyDao,
                    reputationDb = reputationDb,
                    officialSites = officialSites,
                    proxyClient = proxyClient,
                    smsText = smsText,
                    smsSender = smsSender,
                    listener = listener,
                    applicationScope = applicationScope
                )
                session.run()
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
        sessionJob?.cancel()
    }
}
