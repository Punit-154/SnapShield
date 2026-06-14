package com.smssentry.ui.inbox

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Telephony
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smssentry.data.model.ClassificationResult
import com.smssentry.data.model.SmsMessage
import com.smssentry.data.repository.SmsRepository
import com.smssentry.deepcheck.data.ModelRepository
import com.smssentry.sms.SmsContentObserver
import com.smssentry.sms.SmsReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val modelRepository: ModelRepository,
    private val smsRepository: SmsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

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

    val modelState: StateFlow<ModelRepository.State> = modelRepository.state

    private val _isDefaultSmsApp = MutableStateFlow(false)
    val isDefaultSmsAppState: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

    private var smsContentObserver: SmsContentObserver? = null
    private var smsBroadcastReceiver: BroadcastReceiver? = null

    init {
        loadMessages()
        registerSmsObserver()
        registerSmsBroadcastReceiver()
    }

    fun checkDefaultSmsApp() {
        _isDefaultSmsApp.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) ?: false
        } else {
            true
        }
    }

    private fun registerSmsObserver() {
        smsContentObserver = SmsContentObserver {
            refreshMessages()
        }
        context.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsContentObserver!!
        )
    }

    private fun registerSmsBroadcastReceiver() {
        smsBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == SmsReceiver.ACTION_SMS_RECEIVED) {
                    refreshMessages()
                }
            }
        }
        val filter = IntentFilter(SmsReceiver.ACTION_SMS_RECEIVED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(smsBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(smsBroadcastReceiver, filter)
        }
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

    private fun loadMessages() {
        viewModelScope.launch {
            refreshMessages()
        }
    }

    fun refreshMessages() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val realMessages = smsRepository.getInboxMessages()
                val classifiedMessages = realMessages.map { message ->
                    if (message.classification == null) {
                        val result = classifyByRules(message.text)
                        message.copy(classification = result)
                    } else {
                        message
                    }
                }
                _allMessages.value = classifiedMessages
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun classifyByRules(smsText: String): ClassificationResult {
        val lowerText = smsText.lowercase()
        val scamIndicators = listOf("urgent", "verify", "suspended", "click here", "congratulations", "won", "prize", "lottery")
        val score = scamIndicators.count { lowerText.contains(it) }

        return when {
            score >= 2 -> ClassificationResult("SCAM", 0.85f, 85, "Multiple scam indicators detected", true)
            score == 1 -> ClassificationResult("SUSPICIOUS", 0.65f, 55, "Some suspicious patterns", false)
            else -> ClassificationResult("SAFE", 0.9f, 5, "No indicators detected", false)
        }
    }

    fun getMessageById(id: String): SmsMessage? {
        return _allMessages.value.find { it.id == id }
    }

    override fun onCleared() {
        super.onCleared()
        smsContentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
        }
        smsBroadcastReceiver?.let {
            context.unregisterReceiver(it)
        }
    }
}
