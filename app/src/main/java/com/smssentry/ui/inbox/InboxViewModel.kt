package com.smssentry.ui.inbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smssentry.data.mock.MockData
import com.smssentry.data.model.SmsMessage
import com.smssentry.data.repository.SmsRepository
import com.smssentry.ml.SmsClassifierModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    application: Application,
    private val smsRepository: SmsRepository,
    private val classifier: SmsClassifierModel
) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _usedMockData = MutableStateFlow(false)
    val usedMockData: StateFlow<Boolean> = _usedMockData.asStateFlow()

    fun loadMessages(permissionGranted: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true

            val rawMessages = withContext(Dispatchers.IO) {
                if (permissionGranted) {
                    val real = smsRepository.getInboxMessages(500)
                    if (real.isNotEmpty()) {
                        _usedMockData.value = false
                        real
                    } else {
                        _usedMockData.value = true
                        MockData.sampleSmsMessages
                    }
                } else {
                    _usedMockData.value = true
                    MockData.sampleSmsMessages
                }
            }

            _messages.value = rawMessages

            val classified = withContext(Dispatchers.Default) {
                rawMessages.map { msg ->
                    msg.copy(classification = classifier.classify(msg.text))
                }
            }
            _messages.value = classified
            _isLoading.value = false
        }
    }

    fun getMessageById(id: String): SmsMessage? = _messages.value.find { it.id == id }
}