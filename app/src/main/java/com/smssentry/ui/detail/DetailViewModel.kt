package com.smssentry.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smssentry.data.model.*
import com.smssentry.data.repository.SmsRepository
import com.smssentry.deepcheck.ModelManager
import com.smssentry.deepcheck.data.AllowlistDao
import com.smssentry.deepcheck.data.HistoryDao
import com.smssentry.deepcheck.data.OfficialSitesRepository
import com.smssentry.deepcheck.data.ReputationDb
import com.smssentry.deepcheck.proxy.PrivacyProxyClient
import com.smssentry.deepcheck.session.DeepCheckSession
import com.smssentry.di.ApplicationScope
import com.smssentry.domain.service.DeepCheckListener
import com.smssentry.domain.service.DeepCheckSession as DeepCheckSessionInterface
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
    savedStateHandle: SavedStateHandle,
    private val allowlistDao: AllowlistDao,
    private val historyDao: HistoryDao,
    private val reputationDb: ReputationDb,
    private val officialSites: OfficialSitesRepository,
    private val proxyClient: PrivacyProxyClient,
    private val modelManager: ModelManager,
    private val smsRepository: SmsRepository
) : ViewModel() {

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
            val found = smsRepository.getMessageById(smsId)
            found?.let { msg ->
                _message.value = msg
            }
        }
    }

    fun startDeepCheck() {
        val currentMessage = _message.value ?: return

        _investigationState.value = InvestigationUiState()

        viewModelScope.launch {
            val engine = if (modelManager.state.value == ModelManager.State.READY) {
                modelManager.getLlmEngine()
            } else {
                null
            }

            val session = DeepCheckSession(
                context = context,
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
                },
                applicationScope = applicationScope
            )

            deepCheckSession = session
            session.run()
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
