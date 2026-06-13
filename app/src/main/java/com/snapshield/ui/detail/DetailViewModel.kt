package com.snapshield.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapshield.data.mock.MockSnapShieldAI
import com.snapshield.data.model.*
import com.snapshield.domain.service.DeepCheckListener
import com.snapshield.domain.service.DeepCheckSession
import com.snapshield.domain.service.SnapShieldAI
import com.snapshield.ui.inbox.InboxViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val aiService: SnapShieldAI = MockSnapShieldAI()

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
            val sampleMessages = com.snapshield.data.mock.MockData.sampleSmsMessages
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

        deepCheckSession = aiService.startDeepCheck(
            smsText = currentMessage.text,
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
    }

    fun cancelDeepCheck() {
        deepCheckSession?.cancel()
        deepCheckSession = null
        _investigationState.value = InvestigationUiState()
    }
}
