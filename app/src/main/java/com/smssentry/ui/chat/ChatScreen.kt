package com.smssentry.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.provider.BlockedNumberContract
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import android.content.Intent
import androidx.compose.ui.res.stringResource
import com.smssentry.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smssentry.data.model.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun formatDateHeader(timestamp: Long): String {
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val todayCal = Calendar.getInstance()
    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        isSameDay(msgCal, todayCal) -> "Today"
        isSameDay(msgCal, yesterdayCal) -> "Yesterday"
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
    val sendError by viewModel.sendError.collectAsState()
    val contactPhotoUri by viewModel.contactPhotoUri.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    val context = LocalContext.current

    // Derive loading state: true until first real collection completes
    var hasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(messages) { hasLoaded = true }
    val isLoading = !hasLoaded

    var messageText by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<SmsMessage?>(null) }
    var messageToDelete by remember { mutableStateOf<SmsMessage?>(null) }
    var showDeleteConversation by remember { mutableStateOf(false) }
    var showBlockConfirmation by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val senderAddress = viewModel.address

    val listState = rememberLazyListState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show send error snackbar
    LaunchedEffect(sendError) {
        sendError?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSendError()
        }
    }

    // Auto-scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Detect scroll to top (oldest messages) and load more.
    // reverseLayout=true means index 0 = newest (bottom), high indices = oldest (top).
    val layoutInfo = listState.layoutInfo
    LaunchedEffect(layoutInfo.visibleItemsInfo.lastOrNull()?.index, canLoadMore) {
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems > 0 && lastVisible >= totalItems - 4 && canLoadMore) {
            viewModel.loadMoreMessages()
        }
    }

    // ── Delete confirmation dialog ──
    messageToDelete?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text(stringResource(R.string.delete_message_title)) },
            text = { Text(stringResource(R.string.delete_message_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMessage(msg.id)
                        messageToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── Delete conversation confirmation ──
    if (showDeleteConversation) {
        AlertDialog(
            onDismissRequest = { showDeleteConversation = false },
            title = { Text(stringResource(R.string.delete_conversation_title)) },
            text = { Text(stringResource(R.string.delete_conversation_body, contactName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConversation = false
                        viewModel.deleteConversation()
                        onBackClick()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConversation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── Block sender confirmation dialog ──
    if (showBlockConfirmation) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmation = false },
            title = { Text(stringResource(R.string.block_sender_title, contactName)) },
            text = { Text(stringResource(R.string.block_sender_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockConfirmation = false
                        scope.launch {
                            val success = withContext(Dispatchers.IO) {
                                try {
                                    val values = ContentValues().apply {
                                        put(
                                            BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                                            senderAddress
                                        )
                                    }
                                    context.contentResolver.insert(
                                        BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                                        values
                                    ) != null
                                } catch (_: Exception) {
                                    false
                                }
                            }
                            snackbarHostState.showSnackbar(
                                message = if (success)
                                    context.getString(R.string.sender_blocked, contactName)
                                else
                                    context.getString(R.string.block_failed),
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.block_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── Long-press action sheet ──
    if (selectedMessage != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedMessage = null }
        ) {
            val msg = selectedMessage ?: return@ModalBottomSheet
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                // Copy
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy)) },
                    onClick = {
                        copyToClipboard(context, msg.text)
                        selectedMessage = null
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.cd_copy),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
                // Forward / Share
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.forward)) },
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, msg.text)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.forward_message)))
                        selectedMessage = null
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.cd_forward),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
                // Deep Check (only for received messages)
                if (!msg.isSent) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.deep_check)) },
                        onClick = {
                            selectedMessage = null
                            onDeepCheck(msg.id)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = stringResource(R.string.cd_deep_check),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
                // Delete
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        messageToDelete = msg
                        selectedMessage = null
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
                // Block sender (only for received messages)
                if (!msg.isSent) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.block_sender),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            selectedMessage = null
                            showBlockConfirmation = true
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = stringResource(R.string.cd_block_sender),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar
                        val photoBitmap = remember(contactPhotoUri) {
                            contactPhotoUri?.let { uriStr ->
                                try {
                                    val stream = context.contentResolver.openInputStream(
                                        android.net.Uri.parse(uriStr)
                                    )
                                    stream?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                                } catch (_: Exception) { null }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (photoBitmap != null) {
                                Image(
                                    bitmap = photoBitmap,
                                    contentDescription = contactName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    text = contactName.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
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
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
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
                                            contentDescription = stringResource(R.string.cd_deep_check),
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.deep_check))
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    // Find the latest received message for deep check
                                    val latestReceived = messages.lastOrNull { !it.isSent }
                                    latestReceived?.let { onDeepCheck(it.id) }
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.cd_delete_conversation),
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.delete_conversation_menu),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    showDeleteConversation = true
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                isLoading -> {
                    // Subtle loading indicator
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                messages.isEmpty() -> {
                    // Empty state
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.no_messages_empty),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.start_conversation),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        itemsIndexed(
                            items = displayItems,
                            key = { index, item ->
                                when (item) {
                                    is DisplayItem.MessageItem -> "msg_${item.message.id}"
                                    is DisplayItem.DateHeader -> "date_${index}_${item.label}"
                                    is DisplayItem.TimeGap -> "gap_${index}_${item.timestamp}"
                                }
                            },
                            contentType = { _, item ->
                                when (item) {
                                    is DisplayItem.MessageItem -> "message"
                                    is DisplayItem.DateHeader -> "header"
                                    is DisplayItem.TimeGap -> "gap"
                                }
                            },
                        ) { _, item ->
                            when (item) {
                                is DisplayItem.MessageItem -> {
                                    ChatBubble(
                                        message = item.message,
                                        onLongPress = {
                                            selectedMessage = item.message
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
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
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
    val smsLimit = 160
    val showCounter = text.length > smsLimit - 20 // Show when nearing limit

    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // Character counter
            AnimatedVisibility(
                visible = showCounter,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = "${text.length}/$smsLimit",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (text.length > smsLimit)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    textAlign = TextAlign.End,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                        contentDescription = stringResource(R.string.cd_send),
                        tint = if (text.isNotBlank() && !isSending) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Clipboard ───────────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("SMS Message", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.cd_copied_to_clipboard), Toast.LENGTH_SHORT).show()
}
