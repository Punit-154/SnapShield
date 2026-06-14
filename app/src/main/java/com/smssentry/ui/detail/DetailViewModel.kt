package com.smssentry.ui.detail

import com.smssentry.data.repository.RealDeepCheckEngine
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.smssentry.data.mock.MockData
import com.smssentry.data.model.*
import com.smssentry.data.repository.SmsRepository
import com.smssentry.domain.service.DeepCheckListener
import com.smssentry.domain.service.DeepCheckSession
import com.smssentry.data.mock.MockSMSSentryAI
import com.smssentry.ml.SmsClassifierModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.lifecycle.AndroidViewModel

@HiltViewModel
class DetailViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val smsRepository: SmsRepository,
    private val classifier: SmsClassifierModel
) : AndroidViewModel(application) {

    private val aiService = MockSMSSentryAI()
    private val smsId: String = savedStateHandle.get<String>("smsId") ?: ""

    private val _message = MutableStateFlow<SmsMessage?>(null)
    val message: StateFlow<SmsMessage?> = _message.asStateFlow()

    private val _investigationState = MutableStateFlow(InvestigationUiState())
    val investigationState: StateFlow<InvestigationUiState> = _investigationState.asStateFlow()

    private var deepCheckSession: DeepCheckSession? = null

    init {
        loadMessage()
    }

    private fun loadMessage() {
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                smsRepository.getMessageById(smsId)
                    ?: MockData.sampleSmsMessages.find { it.id == smsId }
            }
            found?.let { msg ->
                val result = withContext(Dispatchers.Default) {
                    classifier.classify(msg.text)
                }
                _message.value = msg.copy(classification = result)
            }
        }
    }

    fun startDeepCheck() {
        val currentMessage = _message.value ?: return
        _investigationState.value = InvestigationUiState()

        val engine = RealDeepCheckEngine(
            smsText = currentMessage.text,
            listener = object : DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) {
                    viewModelScope.launch {
                        when (update) {
                            is DeepCheckUpdate.Step -> _investigationState.value =
                                _investigationState.value.copy(
                                    progress = update.progress,
                                    currentStep = update.message
                                )
                            is DeepCheckUpdate.FoundEvidence -> _investigationState.value =
                                _investigationState.value.copy(
                                    evidence = _investigationState.value.evidence + update.item
                                )
                            is DeepCheckUpdate.FinalVerdict -> _investigationState.value =
                                _investigationState.value.copy(
                                    verdict = update.verdict,
                                    progress = 100,
                                    currentStep = null
                                )
                            is DeepCheckUpdate.Error -> _investigationState.value =
                                _investigationState.value.copy(error = update.reason)
                        }
                    }
                }
            },
            classifier = classifier
        )
        deepCheckSession = engine
        engine.start()
    }

    fun cancelDeepCheck() {
        deepCheckSession?.cancel()
        deepCheckSession = null
        _investigationState.value = InvestigationUiState()
    }
}