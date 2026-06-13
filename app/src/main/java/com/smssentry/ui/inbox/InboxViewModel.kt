package com.smssentry.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smssentry.data.mock.MockData
import com.smssentry.data.mock.MockSMSSentryAI
import com.smssentry.data.model.SmsMessage
import com.smssentry.deepcheck.ModelManager
import com.smssentry.domain.service.SMSSentryAI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val modelManager: ModelManager
) : ViewModel() {

    private val aiService: SMSSentryAI = MockSMSSentryAI()

    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val modelState: StateFlow<ModelManager.State> = modelManager.state

    init {
        loadMessages()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            val sampleMessages = MockData.sampleSmsMessages
            _messages.value = sampleMessages

            val classifiedMessages = sampleMessages.map { message ->
                if (message.classification == null) {
                    val result = classifyMessage(message.text)
                    message.copy(classification = result)
                } else {
                    message
                }
            }
            _messages.value = classifiedMessages
            _isLoading.value = false
        }
    }

    private suspend fun classifyMessage(text: String): com.smssentry.data.model.ClassificationResult {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            aiService.classifySMS(text) { result ->
                continuation.resume(result) {}
            }
        }
    }

    fun getMessageById(id: String): SmsMessage? {
        return _messages.value.find { it.id == id }
    }
}
