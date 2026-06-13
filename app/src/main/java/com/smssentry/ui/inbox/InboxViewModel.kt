package com.smssentry.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smssentry.data.model.SmsMessage
import com.smssentry.data.repository.SmsRepository
import com.smssentry.deepcheck.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val smsRepository: SmsRepository
) : ViewModel() {

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
            val inboxMessages = smsRepository.getInboxMessages()
            _messages.value = inboxMessages
            _isLoading.value = false
        }
    }

    fun getMessageById(id: String): SmsMessage? {
        return _messages.value.find { it.id == id }
    }
}
