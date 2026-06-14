package com.smssentry.ui.chat

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smssentry.data.model.SmsMessage
import com.smssentry.data.repository.SmsRepository
import com.smssentry.data.util.ContactResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val smsRepository: SmsRepository,
    private val contactResolver: ContactResolver,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val threadId: Long = savedStateHandle.get<Long>("threadId") ?: -1L
    val address: String = savedStateHandle.get<String>("address") ?: ""

    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    private val _contactName = MutableStateFlow(address)
    val contactName: StateFlow<String> = _contactName.asStateFlow()

    private val _contactPhotoUri = MutableStateFlow<String?>(null)
    val contactPhotoUri: StateFlow<String?> = _contactPhotoUri.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private var smsObserver: ContentObserver? = null

    init {
        resolveContact()
        loadMessages()
        markAsRead()
        registerSmsObserver()
        // Dismiss any notification for this sender when opening the chat
        if (address.isNotBlank()) {
            com.smssentry.sms.NotificationHelper.cancelNotification(context, address)
        }
    }

    private fun resolveContact() {
        if (address.isNotBlank()) {
            val info = contactResolver.resolve(address)
            _contactName.value = info.displayName
            _contactPhotoUri.value = info.photoUri
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            val msgs = smsRepository.getThreadMessages(threadId)
            _messages.value = msgs
        }
    }

    private fun markAsRead() {
        viewModelScope.launch {
            smsRepository.markThreadAsRead(threadId)
        }
    }

    fun refreshMessages() {
        loadMessages()
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || address.isBlank()) return
        _isSending.value = true
        viewModelScope.launch {
            val success = smsRepository.sendSms(address, text)
            if (success) {
                loadMessages()
            }
            _isSending.value = false
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            smsRepository.deleteMessage(messageId)
            loadMessages()
        }
    }

    private fun registerSmsObserver() {
        smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                loadMessages()
                markAsRead()
            }
        }
        context.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsObserver!!
        )
    }

    override fun onCleared() {
        super.onCleared()
        smsObserver?.let { context.contentResolver.unregisterContentObserver(it) }
    }
}
