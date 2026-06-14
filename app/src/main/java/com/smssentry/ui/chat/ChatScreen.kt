package com.smssentry.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smssentry.data.model.SmsMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun formatDateHeader(timestamp: Long): String {
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val todayCal = Calendar.getInstance()

    return when {
        isSameDay(msgCal, todayCal) -> "Today"
        isSameDay(msgCal, todayCal.apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday"
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private const val TIME_GAP_MS = 15L * 60L * 1000L // 15 minutes

// ── Chat Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBackClick: () -> Unit,
    onDeepCheck: (smsId: String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val contactName by viewModel.contactName.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val context = LocalContext.current

    var messageText by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = contactName.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = contactName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Shield,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Deep Check")
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    // Find the latest received message for deep check
                                    val latestReceived = messages.lastOrNull { !it.isSent }
                                    latestReceived?.let { onDeepCheck(it.id) }
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            ChatInputBar(
                text = messageText,
                onTextChange = { messageText = it },
                onSend = {
                    val txt = messageText.trim()
                    if (txt.isNotEmpty()) {
                        viewModel.sendMessage(txt)
                        messageText = ""
                    }
                },
                isSending = isSending,
            )
        },
    ) { innerPadding ->
        // Build display items (messages + date headers + time gaps)
        val displayItems = remember(messages) { buildDisplayItems(messages) }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            reverseLayout = true,
            verticalArrangement = Arrangement.Top,
        ) {
            items(
                items = displayItems,
                key = { item ->
                    when (item) {
                        is DisplayItem.MessageItem -> "msg_${item.message.id}"
                        is DisplayItem.DateHeader -> "date_${item.label}"
                        is DisplayItem.TimeGap -> "gap_${item.timestamp}"
                    }
                },
            ) { item ->
                when (item) {
                    is DisplayItem.MessageItem -> {
                        ChatBubble(
                            message = item.message,
                            onLongPress = {
                                copyToClipboard(context, item.message.text)
                            },
                        )
                    }

                    is DisplayItem.DateHeader -> {
                        DateHeaderRow(label = item.label)
                    }

                    is DisplayItem.TimeGap -> {
                        TimeGapRow(timestamp = item.timestamp)
                    }
                }
            }
        }
    }
}

// ── Display items for LazyColumn ────────────────────────────────────────────

private sealed class DisplayItem {
    data class MessageItem(val message: SmsMessage) : DisplayItem()
    data class DateHeader(val label: String) : DisplayItem()
    data class TimeGap(val timestamp: Long) : DisplayItem()
}

/**
 * Build a reversed list of display items: messages interleaved with date headers
 * and time-gap separators. The list is reversed so reverseLayout=true shows
 * newest messages at the bottom.
 */
private fun buildDisplayItems(messages: List<SmsMessage>): List<DisplayItem> {
    if (messages.isEmpty()) return emptyList()

    val items = mutableListOf<DisplayItem>()
    var lastDate = ""
    var lastTimestamp = 0L

    // Walk chronologically (oldest first)
    for (msg in messages) {
        // Date header
        val dateLabel = formatDateHeader(msg.timestamp)
        if (dateLabel != lastDate) {
            items.add(DisplayItem.DateHeader(dateLabel))
            lastDate = dateLabel
            lastTimestamp = msg.timestamp
        }

        // Time gap within same day
        if (lastTimestamp != 0L && msg.timestamp - lastTimestamp > TIME_GAP_MS &&
            dateLabel == lastDate && items.lastOrNull() !is DisplayItem.DateHeader
        ) {
            items.add(DisplayItem.TimeGap(msg.timestamp))
        }

        items.add(DisplayItem.MessageItem(msg))
        lastTimestamp = msg.timestamp
    }

    // Reverse so newest is at index 0 for reverseLayout
    return items.reversed()
}

// ── Sub-composables ─────────────────────────────────────────────────────────

@Composable
private fun DateHeaderRow(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun TimeGapRow(timestamp: Long) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = timeFormat.format(Date(timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Message",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                maxLines = 4,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && !isSending,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (text.isNotBlank() && !isSending) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank() && !isSending) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Clipboard ───────────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("SMS Message", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
