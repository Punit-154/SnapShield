package com.smssentry.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smssentry.data.mock.MockSMSSentryAI
import com.smssentry.data.model.*
import com.smssentry.deepcheck.ModelManager
import com.smssentry.deepcheck.data.AllowlistDao
import com.smssentry.deepcheck.data.HistoryDao
import com.smssentry.deepcheck.data.OfficialSitesRepository
import com.smssentry.deepcheck.data.ReputationDb
import com.smssentry.deepcheck.proxy.PrivacyProxyClient
import com.smssentry.deepcheck.session.DeepCheckSession
import com.smssentry.domain.service.DeepCheckListener
import com.smssentry.domain.service.DeepCheckSession as DeepCheckSessionInterface
import com.smssentry.domain.service.SMSSentryAI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val allowlistDao: AllowlistDao,
    private val historyDao: HistoryDao,
    private val reputationDb: ReputationDb,
    private val officialSites: OfficialSitesRepository,
    private val proxyClient: PrivacyProxyClient,
    private val modelManager: ModelManager
) : ViewModel() {

    companion object {
        const val USE_DEMO_DATA = false
    }

    private val aiService: SMSSentryAI = MockSMSSentryAI()

    private val smsId: String = savedStateHandle.get<String>("smsId") ?: ""

    private val _message = MutableStateFlow<SmsMessage?>(null)
    val message: StateFlow<SmsMessage?> = _message.asStateFlow()

    private val _investigationState = MutableStateFlow(InvestigationUiState())
    val investigationState: StateFlow<InvestigationUiState> = _investigationState.asStateFlow()

    private val _showDownloadPrompt = MutableStateFlow(false)
    val showDownloadPrompt: StateFlow<Boolean> = _showDownloadPrompt.asStateFlow()

    val modelState: StateFlow<ModelManager.State> = modelManager.state

    private var deepCheckSession: DeepCheckSessionInterface? = null

    init {
        loadMessage()
    }

    private fun loadMessage() {
        viewModelScope.launch {
            val sampleMessages = com.smssentry.data.mock.MockData.sampleSmsMessages
            val found = sampleMessages.find { it.id == smsId }
            found?.let { msg ->
                if (msg.classification == null) {
                    val result = classifyMessage(msg.text)
                    _message.value = msg.copy(classification = result)
                } else {
                    _message.value = msg
                }
            }
        }
    }

    private suspend fun classifyMessage(text: String): ClassificationResult {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            aiService.classifySMS(text) { result ->
                continuation.resume(result) {}
            }
        }
    }

    fun startDeepCheck() {
        val currentMessage = _message.value ?: return

        _investigationState.value = InvestigationUiState()

        if (USE_DEMO_DATA) {
            runDemoInvestigation(currentMessage)
            return
        }

        viewModelScope.launch {
            val engine = if (modelManager.state.value == ModelManager.State.READY) {
                modelManager.getInference()
            } else {
                null
            }

            val session = DeepCheckSession(
                engine = engine,
                allowlistDao = allowlistDao,
                historyDao = historyDao,
                reputationDb = reputationDb,
                officialSites = officialSites,
                proxyClient = proxyClient,
                smsText = currentMessage.text,
                smsSender = currentMessage.sender,
                listener = object : DeepCheckListener {
                    override fun onUpdate(update: DeepCheckUpdate) {
                        viewModelScope.launch {
                            when (update) {
                                is DeepCheckUpdate.Step -> {
                                    _investigationState.value = _investigationState.value.copy(
                                        progress = update.progress,
                                        currentStep = update.message
                                    )
                                }
                                is DeepCheckUpdate.FoundEvidence -> {
                                    _investigationState.value = _investigationState.value.copy(
                                        evidence = _investigationState.value.evidence + update.item
                                    )
                                }
                                is DeepCheckUpdate.FinalVerdict -> {
                                    _investigationState.value = _investigationState.value.copy(
                                        verdict = update.verdict,
                                        progress = 100,
                                        currentStep = null
                                    )
                                }
                                is DeepCheckUpdate.Error -> {
                                    _investigationState.value = _investigationState.value.copy(
                                        error = update.reason
                                    )
                                }
                            }
                        }
                    }
                }
            )

            deepCheckSession = session
            session.run()
        }
    }

    private fun runDemoInvestigation(message: SmsMessage) {
        viewModelScope.launch {
            val demoEvidence = listOf(
                EvidenceItem(
                    source = "URL Analysis",
                    detail = "Detected suspicious URL: hsbc-secure.xyz (not official domain)",
                    severity = "HIGH"
                ),
                EvidenceItem(
                    source = "Domain Check",
                    detail = "Domain registered 2 days ago via Namecheap privacy proxy",
                    severity = "CRITICAL"
                ),
                EvidenceItem(
                    source = "Brand Mismatch",
                    detail = "Sender claims to be HSBC but URL domain does not match official site",
                    severity = "HIGH"
                ),
                EvidenceItem(
                    source = "Reputation DB",
                    detail = "Domain hsbc-secure.xyz found in phishing database with 94% confidence",
                    severity = "CRITICAL"
                ),
                EvidenceItem(
                    source = "Text Analysis",
                    detail = "Message contains urgency language: 'URGENT', 'suspended', 'verify now'",
                    severity = "MEDIUM"
                )
            )

            val steps = listOf(
                "Extracting URLs and domains..." to 10,
                "Checking domain reputation..." to 25,
                "Analyzing brand legitimacy..." to 40,
                "Cross-referencing phishing database..." to 60,
                "Running AI text analysis..." to 80,
                "Compiling final verdict..." to 95
            )

            for ((stepText, progress) in steps) {
                _investigationState.value = _investigationState.value.copy(
                    progress = progress,
                    currentStep = stepText
                )
                delay(800)

                if (progress == 25) {
                    _investigationState.value = _investigationState.value.copy(
                        evidence = listOf(demoEvidence[0])
                    )
                } else if (progress == 40) {
                    _investigationState.value = _investigationState.value.copy(
                        evidence = demoEvidence.take(2)
                    )
                } else if (progress == 60) {
                    _investigationState.value = _investigationState.value.copy(
                        evidence = demoEvidence.take(3)
                    )
                } else if (progress == 80) {
                    _investigationState.value = _investigationState.value.copy(
                        evidence = demoEvidence.take(5)
                    )
                }
            }

            delay(500)

            _investigationState.value = _investigationState.value.copy(
                verdict = DeepCheckVerdict(
                    isScam = true,
                    threatType = "PHISHING",
                    summary = "This message is a phishing attempt impersonating HSBC. The URL hsbc-secure.xyz is not an official HSBC domain and was registered 2 days ago.",
                    evidence = demoEvidence,
                    recommendedActions = listOf(
                        "Do NOT click any links in this message",
                        "Block this sender immediately",
                        "Report to your bank if you shared any information",
                        "Delete this message"
                    )
                ),
                evidence = demoEvidence,
                progress = 100,
                currentStep = null
            )
        }
    }

    fun cancelDeepCheck() {
        deepCheckSession?.cancel()
        deepCheckSession = null
        _investigationState.value = InvestigationUiState()
    }

    fun onDownloadPromptDismissed() {
        _showDownloadPrompt.value = false
    }

    fun checkModelAndPromptDownload() {
        if (modelManager.state.value != ModelManager.State.READY &&
            modelManager.state.value != ModelManager.State.LOADING
        ) {
            _showDownloadPrompt.value = true
        }
    }
}
