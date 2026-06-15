package com.smssentry.ui.conversations

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smssentry.data.model.Conversation
import com.smssentry.data.util.ContactResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.smssentry.data.model.SmsMessage
import com.smssentry.data.repository.SmsRepository
import javax.inject.Inject

import androidx.annotation.StringRes
import com.smssentry.R

enum class ConversationFilter(val label: String, @StringRes val labelRes: Int) {
    ALL("All", R.string.filter_all),
    UNREAD("Unread", R.string.filter_unread),
    FLAGGED("Flagged", R.string.filter_flagged)
}

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val contactResolver: ContactResolver,
    private val smsRepository: SmsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "ConversationListVM"
    }

    private val _allConversations = MutableStateFlow<List<Conversation>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ConversationFilter.ALL)
    val selectedFilter: StateFlow<ConversationFilter> = _selectedFilter.asStateFlow()

    // Global message search results
    private val _messageSearchResults = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messageSearchResults: StateFlow<List<SmsMessage>> = _messageSearchResults.asStateFlow()
    private var searchJob: Job? = null

    // Pinned conversations persisted in SharedPreferences
    private val pinPrefs = context.getSharedPreferences("pinned_conversations", Context.MODE_PRIVATE)
    private val _pinnedThreadIds = MutableStateFlow<Set<Long>>(loadPinnedIds())
    val pinnedThreadIds: StateFlow<Set<Long>> = _pinnedThreadIds.asStateFlow()

    // Deleted conversation for undo support
    private var lastDeletedConversation: Conversation? = null
    private var pendingDeleteJob: kotlinx.coroutines.Job? = null

    val conversations: StateFlow<List<Conversation>> = combine(
        _allConversations, _searchQuery, _selectedFilter, _pinnedThreadIds
    ) { allConversations, query, filter, pinned ->
        allConversations
            // Guard against duplicate threadIds — prevents LazyColumn key crash
            .distinctBy { it.threadId }
            .filter { conversation ->
                val matchesSearch = query.isBlank() ||
                    conversation.displayName.contains(query, ignoreCase = true) ||
                    conversation.address.contains(query, ignoreCase = true) ||
                    conversation.lastMessage.contains(query, ignoreCase = true)
                val matchesFilter = when (filter) {
                    ConversationFilter.ALL -> true
                    ConversationFilter.UNREAD -> conversation.unreadCount > 0
                    ConversationFilter.FLAGGED -> conversation.isFlagged
                }
                matchesSearch && matchesFilter
            }.sortedWith(
                compareByDescending<Conversation> { it.threadId in pinned }
                    .thenByDescending { it.lastTimestamp }
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var smsObserver: ContentObserver? = null
    private var debounceJob: Job? = null

    init {
        loadConversations()
        registerSmsObserver()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        // Trigger debounced global message search
        searchJob?.cancel()
        if (query.length >= 2) {
            searchJob = viewModelScope.launch {
                delay(300L)
                _messageSearchResults.value = smsRepository.searchMessages(query)
            }
        } else {
            _messageSearchResults.value = emptyList()
        }
    }

    fun onFilterSelected(filter: ConversationFilter) {
        _selectedFilter.value = filter
    }

    fun togglePin(threadId: Long) {
        val current = _pinnedThreadIds.value.toMutableSet()
        if (threadId in current) {
            current.remove(threadId)
        } else {
            current.add(threadId)
        }
        _pinnedThreadIds.value = current
        savePinnedIds(current)
    }

    fun isPinned(threadId: Long): Boolean = threadId in _pinnedThreadIds.value

    private fun loadPinnedIds(): Set<Long> {
        return pinPrefs.getStringSet("pinned_ids", emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
    }

    private fun savePinnedIds(ids: Set<Long>) {
        pinPrefs.edit()
            .putStringSet("pinned_ids", ids.map { it.toString() }.toSet())
            .apply()
    }

    fun deleteConversation(threadId: Long) {
        // Cancel any previous pending delete
        pendingDeleteJob?.cancel()

        val deleted = _allConversations.value.find { it.threadId == threadId }
        lastDeletedConversation = deleted
        // Remove from UI immediately
        _allConversations.value = _allConversations.value.filter { it.threadId != threadId }

        // Defer actual content provider deletion — gives user time to undo
        pendingDeleteJob = viewModelScope.launch {
            kotlinx.coroutines.delay(5000L)
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.delete(
                        Telephony.Sms.CONTENT_URI,
                        "${Telephony.Sms.THREAD_ID} = ?",
                        arrayOf(threadId.toString())
                    )
                    Log.d(TAG, "Deleted conversation $threadId from provider")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete conversation $threadId", e)
                }
            }
            lastDeletedConversation = null
        }
    }

    fun undoLastDelete() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        lastDeletedConversation?.let { conversation ->
            // Only re-add if the observer hasn't already refreshed it back
            val current = _allConversations.value
            if (current.none { it.threadId == conversation.threadId }) {
                _allConversations.value = (current + conversation)
                    .sortedByDescending { it.lastTimestamp }
            }
            lastDeletedConversation = null
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val values = android.content.ContentValues()
                    values.put(Telephony.Sms.READ, 1)
                    context.contentResolver.update(
                        Telephony.Sms.CONTENT_URI,
                        values,
                        "${Telephony.Sms.READ} = 0",
                        null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to mark all as read", e)
                }
            }
            refresh()
        }
    }

    fun refresh() {
        loadConversations()
    }

    private fun loadConversations() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val convos = readConversationsFromProvider()
                _allConversations.value = convos
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load conversations", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun readConversationsFromProvider(): List<Conversation> =
        withContext(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext emptyList()
            }

            val conversations = mutableListOf<Conversation>()

            // Query conversation threads
            val threadUri = Uri.parse("content://sms/conversations")
            val threadCursor = context.contentResolver.query(
                threadUri,
                arrayOf("thread_id", "msg_count", "snippet"),
                null, null, "date DESC"
            )

            // Track seen thread IDs to prevent duplicates from system provider
            val seenThreadIds = mutableSetOf<Long>()

            threadCursor?.use { cursor ->
                val threadIdIdx = cursor.getColumnIndexOrThrow("thread_id")
                val msgCountIdx = cursor.getColumnIndexOrThrow("msg_count")
                val snippetIdx = cursor.getColumnIndexOrThrow("snippet")

                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(threadIdIdx)
                    // Skip duplicate thread IDs from system provider
                    if (!seenThreadIds.add(threadId)) continue
                    val messageCount = cursor.getInt(msgCountIdx)
                    val snippet = cursor.getString(snippetIdx) ?: ""

                    // Get the latest message details for this thread
                    val threadInfo = getThreadInfo(threadId, snippet)
                    if (threadInfo != null) {
                        conversations.add(
                            threadInfo.copy(messageCount = messageCount)
                        )
                    }
                }
            }

            conversations
        }

    private fun getThreadInfo(threadId: Long, snippet: String): Conversation? {
        // Query the latest message in the thread for address, timestamp, read status
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.DATE,
                Telephony.Sms.BODY,
                Telephony.Sms.READ
            ),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC"
        )

        return cursor?.use {
            if (it.moveToFirst()) {
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val readIdx = it.getColumnIndex(Telephony.Sms.READ)

                // ADDRESS is essential — bail out if the column is missing
                if (addressIdx == -1 || dateIdx == -1) return@use null

                val address = it.getString(addressIdx) ?: return@use null
                val timestamp = it.getLong(dateIdx)
                val body = if (bodyIdx != -1) it.getString(bodyIdx) ?: snippet else snippet

                // Count unread messages in thread
                var unreadCount = 0
                // Check for flagged content (basic scam indicators)
                var isFlagged = false

                val scamIndicators = listOf(
                    "urgent", "verify", "suspended", "click here",
                    "congratulations", "won", "prize", "lottery",
                    "account has been", "immediate action"
                )

                // Iterate through all messages in this thread
                it.moveToPosition(-1) // reset cursor
                while (it.moveToNext()) {
                    if (readIdx != -1) {
                        val read = it.getInt(readIdx)
                        if (read == 0) unreadCount++
                    }

                    if (!isFlagged && bodyIdx != -1) {
                        val msgBody = it.getString(bodyIdx)?.lowercase() ?: ""
                        val hitCount = scamIndicators.count { indicator ->
                            msgBody.contains(indicator)
                        }
                        if (hitCount >= 2) isFlagged = true
                    }
                }

                // Resolve contact
                val contactInfo = contactResolver.resolve(address)

                Conversation(
                    threadId = threadId,
                    address = address,
                    displayName = contactInfo.displayName,
                    photoUri = contactInfo.photoUri,
                    lastMessage = body,
                    lastTimestamp = timestamp,
                    unreadCount = unreadCount,
                    messageCount = 0, // will be set by caller
                    isFlagged = isFlagged
                )
            } else null
        }
    }

    private fun registerSmsObserver() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                debounceJob?.cancel()
                debounceJob = viewModelScope.launch {
                    delay(300L)
                    loadConversations()
                }
            }
        }
        smsObserver = observer
        context.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            observer
        )
    }

    override fun onCleared() {
        super.onCleared()
        debounceJob?.cancel()
        pendingDeleteJob?.cancel()
        smsObserver?.let { context.contentResolver.unregisterContentObserver(it) }
    }
}
