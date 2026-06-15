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
import com.smssentry.di.DispatcherProvider
import com.smssentry.domain.service.DeepCheckListener
import com.smssentry.domain.service.DeepCheckSession as DeepCheckSessionInterface
import com.smssentry.domain.service.SMSSentryAI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [SMSSentryAI] that provides two-tier SMS classification:
 *
 * **Tier 1 — Rule-based fast path** ([classifyByRules]):
 *   Uses weighted keyword scoring with safe-sender and safe-content awareness to produce
 *   instant SAFE / SUSPICIOUS / SCAM verdicts. This avoids the false-positive problem of
 *   the original flat keyword approach (which flagged legitimate bank OTPs as scam).
 *
 * **Tier 2 — On-device LLM deep check** ([startDeepCheck]):
 *   Spins up a [DeepCheckSession] using the LiteRT-LM engine. The session performs multi-step
 *   forensic analysis including URL reputation, WHOIS lookups, brand impersonation detection,
 *   and scam history matching — all on-device via a Cloudflare Worker privacy proxy.
 *
 * Thread-safety: This class is `@Singleton` and all coroutine work is launched on
 * [applicationScope] so it survives ViewModel/Activity lifecycle changes.
 *
 * @see DeepCheckSession for the LLM-based investigation pipeline
 * @see classifyByRules for the scoring algorithm details
 */
@Singleton
class RealSMSSentryAI @Inject constructor(
    @ApplicationContext private val context: Context,
    private val allowlistDao: AllowlistDao,
    private val historyDao: HistoryDao,
    private val reputationDb: ReputationDb,
    private val officialSites: OfficialSitesRepository,
    private val proxyClient: PrivacyProxyClient,
    private val modelRepository: ModelRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val dispatchers: DispatcherProvider
) : SMSSentryAI {

    private var isInitialized = false

    /** Ensures the LLM model files are downloaded and ready. Runs on [applicationScope]. */
    override fun initialize(context: Context, callback: (Boolean) -> Unit) {
        applicationScope.launch {
            modelRepository.ensureReady()
            isInitialized = true
            callback(true)
        }
    }

    /** Quick rule-based classification (Tier 1). Result delivered via [callback]. */
    override fun classifySMS(smsText: String, callback: (ClassificationResult) -> Unit) {
        applicationScope.launch {
            val result = classifyByRules(smsText)
            callback(result)
        }
    }

    /**
     * Launches a Tier 2 deep check using the on-device LLM.
     * The returned session can be cancelled by the caller (e.g. when navigating away).
     * Progress updates are streamed to [listener] as [DeepCheckUpdate] events.
     */
    override fun startDeepCheck(smsText: String, listener: DeepCheckListener): DeepCheckSessionInterface {
        val session = RealDeepCheckSession(
            context, smsText, "", listener,
            modelRepository.getEngine(), allowlistDao, historyDao, reputationDb, officialSites, proxyClient,
            applicationScope, dispatchers
        )
        session.start()
        return session
    }

    /**
     * Classify SMS using weighted scoring with safe-sender and safe-content awareness.
     *
     * The old approach used a flat keyword list ("urgent", "verify", etc.) which
     * produced massive false positives on legitimate bank/business SMS.
     *
     * New approach:
     *  1. Check for safe sender patterns (alphanumeric IDs = business senders)
     *  2. Check for safe content patterns (OTP, txn alerts, delivery updates)
     *  3. Apply weighted scam indicators (high/medium/low weight)
     *  4. Use higher threshold before flagging
     */
    private fun classifyByRules(smsText: String, sender: String = ""): ClassificationResult {
        val lowerText = smsText.lowercase()
        val lowerSender = sender.lowercase()

        // ── Step 1: Safe sender detection ──
        // Alphanumeric sender IDs (e.g., VM-HDFCBK, JD-ICICIT, AX-SBIPSG)
        // are registered business senders — almost never scam.
        val isAlphanumericSender = sender.isNotBlank() &&
            !sender.all { it.isDigit() || it == '+' } &&
            sender.any { it.isLetter() }

        // ── Step 2: Safe content patterns ──
        // These strongly indicate legitimate messages
        val safePatterns = listOf(
            "otp is", "otp:", "one time password", "verification code",
            "transaction of", "credited with", "debited from", "a/c",
            "account ****", "account xx", "acct no",
            "your order", "delivered to", "out for delivery", "shipped",
            "appointment confirmed", "booking confirmed", "ticket booked",
            "balance is", "available balance", "closing balance",
            "emi due", "payment received", "bill generated",
            "logged in from", "new sign-in", "security alert from"
        )
        val safeHits = safePatterns.count { lowerText.contains(it) }

        // If it's a business sender AND has safe content → definitely safe
        if (isAlphanumericSender && safeHits >= 1) {
            return ClassificationResult(
                "SAFE", 0.95f, 3,
                context.getString(R.string.reason_no_indicators),
                false
            )
        }

        // ── Step 3: Weighted scam indicators ──
        data class Indicator(val phrase: String, val weight: Int)

        val highWeight = listOf(
            Indicator("won a prize", 3), Indicator("lottery winner", 3),
            Indicator("claim your reward", 3), Indicator("send money to", 3),
            Indicator("wire transfer", 3), Indicator("western union", 3),
            Indicator("bitcoin payment", 3), Indicator("crypto wallet", 3),
            Indicator("nigerian prince", 3), Indicator("inheritance from", 3),
            Indicator("million dollars", 3), Indicator("selected as winner", 3),
        )
        val medWeight = listOf(
            Indicator("click here now", 2), Indicator("act now or", 2),
            Indicator("account suspended", 2), Indicator("account will be closed", 2),
            Indicator("limited time offer", 2), Indicator("free gift", 2),
            Indicator("you have been selected", 2), Indicator("call this number", 2),
            Indicator("confirm your identity", 2), Indicator("update your payment", 2),
            Indicator("unusual activity", 2), Indicator("unauthorized access", 2),
        )
        val lowWeight = listOf(
            Indicator("click here", 1), Indicator("congratulations", 1),
            Indicator("winner", 1), Indicator("prize", 1),
            Indicator("urgent action", 1), Indicator("immediate action", 1),
            Indicator("expire soon", 1), Indicator("risk of closure", 1),
        )

        val allIndicators = highWeight + medWeight + lowWeight
        val weightedScore = allIndicators.sumOf { indicator ->
            if (lowerText.contains(indicator.phrase)) indicator.weight else 0
        }

        // Reduce score for business senders (they're less likely to be scam)
        val adjustedScore = if (isAlphanumericSender) weightedScore / 2 else weightedScore

        return when {
            adjustedScore >= 4 -> ClassificationResult(
                "SCAM", 0.85f, 80 + minOf(adjustedScore * 2, 15),
                context.getString(R.string.reason_multiple_indicators),
                true
            )
            adjustedScore >= 2 -> ClassificationResult(
                "SUSPICIOUS", 0.60f, 40 + adjustedScore * 5,
                context.getString(R.string.reason_some_indicators),
                false
            )
            else -> ClassificationResult(
                "SAFE", 0.90f, 5 + adjustedScore * 3,
                context.getString(R.string.reason_no_indicators),
                false
            )
        }
    }
}

/**
 * Wraps a [DeepCheckSession] in a cancellable, lifecycle-aware shell.
 *
 * Key design decisions:
 * - Uses [AtomicBoolean] for `isActive` so it's safe to read from any thread.
 * - Runs on [applicationScope] (not viewModelScope) so the deep check survives
 *   configuration changes. The caller can still [cancel] explicitly.
 * - Errors are forwarded to the [listener] as [DeepCheckUpdate.Error] rather than
 *   being thrown, ensuring the UI can display a user-friendly error message.
 */
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
    private val applicationScope: CoroutineScope,
    private val dispatchers: DispatcherProvider
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
                    applicationScope = applicationScope,
                    dispatchers = dispatchers
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
