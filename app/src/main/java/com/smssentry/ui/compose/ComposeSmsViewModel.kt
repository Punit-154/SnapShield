package com.smssentry.ui.compose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smssentry.data.repository.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ComposeSmsState(
    val recipient: String = "",
    val message: String = "",
    val isSending: Boolean = false,
    val sendResult: SendResult? = null,
)

enum class SendResult {
    SUCCESS,
    FAILURE,
    EMPTY_FIELDS,
}

@HiltViewModel
class ComposeSmsViewModel @Inject constructor(
    private val smsRepository: SmsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(ComposeSmsState())
    val state: StateFlow<ComposeSmsState> = _state.asStateFlow()

    init {
        // Pre-fill recipient if passed via navigation arg
        val prefillRecipient = savedStateHandle.get<String>("recipient") ?: ""
        if (prefillRecipient.isNotBlank()) {
            _state.value = _state.value.copy(recipient = prefillRecipient)
        }
    }

    fun onRecipientChanged(value: String) {
        _state.value = _state.value.copy(recipient = value, sendResult = null)
    }

    fun onMessageChanged(value: String) {
        _state.value = _state.value.copy(message = value, sendResult = null)
    }

    fun sendSms() {
        val current = _state.value
        if (current.recipient.isBlank() || current.message.isBlank()) {
            _state.value = current.copy(sendResult = SendResult.EMPTY_FIELDS)
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSending = true, sendResult = null)
            val success = smsRepository.sendSms(
                recipient = current.recipient.trim(),
                message = current.message.trim(),
            )
            _state.value = _state.value.copy(
                isSending = false,
                sendResult = if (success) SendResult.SUCCESS else SendResult.FAILURE,
                message = if (success) "" else _state.value.message,
            )
        }
    }

    fun clearSendResult() {
        _state.value = _state.value.copy(sendResult = null)
    }
}
