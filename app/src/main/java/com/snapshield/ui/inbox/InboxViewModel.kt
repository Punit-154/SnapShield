package com.snapshield.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapshield.data.mock.MockData
import com.snapshield.data.mock.MockSnapShieldAI
import com.snapshield.data.model.SmsMessage
import com.snapshield.domain.service.SnapShieldAI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor() : ViewModel() {

    private val aiService: SnapShieldAI = MockSnapShieldAI()

    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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

    private suspend fun classifyMessage(text: String): com.snapshield.data.model.ClassificationResult {
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
