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
                try {
                    delay(300L)
                    _messageSearchResults.value = smsRepository.searchMessages(query)
                } catch (e: Exception) {
                    Log.e(TAG, "Message search failed", e)
                }
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
                val threadIdIdx = cursor.getColumnIndex("thread_id")
                val msgCountIdx = cursor.getColumnIndex("msg_count")
                val snippetIdx = cursor.getColumnIndex("snippet")
                if (threadIdIdx == -1 || msgCountIdx == -1 || snippetIdx == -1) return@withContext emptyList()

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
                // Check for flagged content using weighted scoring
                var isFlagged = false

                // Safe sender detection: alphanumeric IDs (e.g., VM-HDFCBK) are business senders
                val isBusinessSender = address.isNotBlank() &&
                    !address.all { c -> c.isDigit() || c == '+' } &&
                    address.any { c -> c.isLetter() }

                // Safe content patterns — these indicate legitimate messages
                val safePatterns = listOf(
                    "otp is", "otp:", "verification code", "transaction of",
                    "credited with", "debited from", "a/c", "account ****",
                    "your order", "delivered to", "out for delivery",
                    "balance is", "available balance", "payment received"
                )

                // Weighted scam indicators (phrase to weight)
                val scamPhrases = listOf(
                    "won a prize" to 3, "lottery winner" to 3,
                    "claim your reward" to 3, "send money to" to 3,
                    "selected as winner" to 3, "million dollars" to 3,
                    "account suspended" to 2, "account will be closed" to 2,
                    "click here now" to 2, "free gift" to 2,
                    "limited time offer" to 2, "you have been selected" to 2,
                    "click here" to 1, "congratulations" to 1,
                    "winner" to 1, "prize" to 1,
                    "urgent action" to 1, "immediate action" to 1,
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

                        // Skip flagging if message has safe content from business sender
                        val hasSafeContent = safePatterns.any { p -> msgBody.contains(p) }
                        if (isBusinessSender && hasSafeContent) continue

                        val weightedScore = scamPhrases.sumOf { (phrase, weight) ->
                            if (msgBody.contains(phrase)) weight else 0
                        }
                        val adjustedScore = if (isBusinessSender) weightedScore / 2 else weightedScore
                        if (adjustedScore >= 4) isFlagged = true
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
                    try {
                        delay(300L)
                        loadConversations()
                    } catch (e: Exception) {
                        Log.e(TAG, "SMS observer refresh failed", e)
                    }
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
