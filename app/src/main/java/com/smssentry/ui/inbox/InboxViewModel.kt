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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SmsFilter(val label: String) {
    ALL("All"),
    SCAM("Scam"),
    SUSPICIOUS("Suspicious"),
    SAFE("Safe")
}

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val modelManager: ModelManager
) : ViewModel() {

    private val aiService: SMSSentryAI = MockSMSSentryAI()

    private val _allMessages = MutableStateFlow<List<SmsMessage>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(SmsFilter.ALL)
    val selectedFilter: StateFlow<SmsFilter> = _selectedFilter.asStateFlow()

    private var lastDeletedMessage: SmsMessage? = null

    val messages: StateFlow<List<SmsMessage>> = combine(
        _allMessages, _searchQuery, _selectedFilter
    ) { allMessages, query, filter ->
        allMessages.filter { message ->
            val matchesSearch = query.isBlank() ||
                message.sender.contains(query, ignoreCase = true) ||
                message.text.contains(query, ignoreCase = true) ||
                message.classification?.label?.contains(query, ignoreCase = true) == true
            val matchesFilter = when (filter) {
                SmsFilter.ALL -> true
                SmsFilter.SCAM -> message.classification?.label?.uppercase() == "SCAM"
                SmsFilter.SUSPICIOUS -> message.classification?.label?.uppercase() == "SUSPICIOUS"
                SmsFilter.SAFE -> message.classification?.label?.uppercase() == "SAFE"
            }
            matchesSearch && matchesFilter
        }.sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val modelState: StateFlow<ModelManager.State> = modelManager.state

    init {
        loadMessages()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelected(filter: SmsFilter) {
        _selectedFilter.value = filter
    }

    fun deleteMessage(id: String) {
        val deleted = _allMessages.value.find { it.id == id }
        lastDeletedMessage = deleted
        _allMessages.value = _allMessages.value.filter { it.id != id }
    }

    fun undoLastDelete() {
        lastDeletedMessage?.let { message ->
            _allMessages.value = _allMessages.value + message
            lastDeletedMessage = null
        }
    }

    fun restoreMessage(message: SmsMessage) {
        _allMessages.value = _allMessages.value + message
    }

    private fun loadMessages() {
        viewModelScope.launch {
            val sampleMessages = MockData.sampleSmsMessages

            val classifiedMessages = sampleMessages.map { message ->
                if (message.classification == null) {
                    val result = classifyMessage(message.text)
                    message.copy(classification = result)
                } else {
                    message
                }
            }
            _allMessages.value = classifiedMessages
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
        return _allMessages.value.find { it.id == id }
    }
}
