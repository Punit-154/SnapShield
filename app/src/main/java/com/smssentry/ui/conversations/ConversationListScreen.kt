package com.smssentry.ui.conversations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smssentry.data.model.Conversation
import com.smssentry.R
import androidx.compose.ui.res.stringResource
import com.smssentry.ui.theme.*
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

// ── Conversation List Screen ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (threadId: Long, address: String) -> Unit,
    onComposeClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: ConversationListViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val messageSearchResults by viewModel.messageSearchResults.collectAsState()
    val hasSmsPermission by viewModel.hasSmsPermission.collectAsState()
    val errorEvent by viewModel.errorEvent.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Re-check permission when lifecycle resumes (e.g. returning from Settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            viewModel.recheckPermission()
        }
    }

    // Reset refreshing when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }

    // Surface ViewModel errors via Snackbar
    LaunchedEffect(errorEvent) {
        errorEvent?.let { resId ->
            snackbarHostState.showSnackbar(
                message = context.getString(resId),
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = isSearchExpanded,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                        },
                        label = "topBarContent"
                    ) { searching ->
                        if (searching) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                placeholder = {
                                    Text(
                                        stringResource(R.string.search_conversations),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            val totalUnread = conversations.sumOf { it.unreadCount }
                            Column {
                                Text(
                                    text = stringResource(R.string.messages_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                                if (totalUnread > 0) {
                                    Text(
                                        text = stringResource(R.string.unread_count, totalUnread),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    // Search toggle
                    IconButton(onClick = {
                        isSearchExpanded = !isSearchExpanded
                        if (!isSearchExpanded) viewModel.onSearchQueryChanged("")
                    }) {
                        Icon(
                            if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (isSearchExpanded) stringResource(R.string.cd_close_search) else stringResource(R.string.cd_search)
                        )
                    }

                    // Settings
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }

                    // Overflow menu
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.refresh)) },
                                onClick = {
                                    showOverflowMenu = false
                                    isRefreshing = true
                                    viewModel.refresh()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mark_all_read)) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.markAllAsRead()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onComposeClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.cd_compose_message),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Filter Chips ──
            ConversationFilterChipRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { viewModel.onFilterSelected(it) },
                conversationCounts = Triple(
                    conversations.size, // doesn't matter, we show total from all
                    0, 0 // placeholders; chips simply label the filter
                )
            )

            // ── Content Area with Pull-to-Refresh ──
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.refresh()
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading && !isRefreshing -> {
                        // Shimmer loading skeleton
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            items(8) {
                                ShimmerConversationItem()
                            }
                        }
                    }

                    conversations.isEmpty() && !isLoading -> {
                        ConversationEmptyState(
                            hasSmsPermission = hasSmsPermission,
                            isSearchActive = searchQuery.isNotEmpty(),
                            searchQuery = searchQuery,
                            hasActiveFilter = selectedFilter != ConversationFilter.ALL
                        )
                    }

                    else -> {
                        var conversationToDelete by remember { mutableStateOf<Conversation?>(null) }
                        val pinnedIds by viewModel.pinnedThreadIds.collectAsState()

                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                conversations,
                                key = { _, convo -> convo.threadId },
                                contentType = { _, _ -> "conversation" }
                            ) { index, conversation ->
                                val isPinned = conversation.threadId in pinnedIds
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { dismissValue ->
                                        when (dismissValue) {
                                            SwipeToDismissBoxValue.EndToStart -> {
                                                conversationToDelete = conversation
                                                false
                                            }
                                            SwipeToDismissBoxValue.StartToEnd -> {
                                                viewModel.togglePin(conversation.threadId)
                                                false
                                            }
                                            else -> false
                                        }
                                    }
                                )

                                // Staggered entrance animation
                                var itemVisible by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    delay(minOf(index, 15) * 30L)
                                    itemVisible = true
                                }

                                val itemAlpha by animateFloatAsState(
                                    targetValue = if (itemVisible) 1f else 0f,
                                    animationSpec = tween(250),
                                    label = "itemAlpha"
                                )

                                Box(modifier = Modifier.alpha(itemAlpha)) {
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        backgroundContent = {
                                            val direction = dismissState.dismissDirection
                                            val bgColor = when (direction) {
                                                SwipeToDismissBoxValue.StartToEnd ->
                                                    MaterialTheme.colorScheme.primaryContainer
                                                SwipeToDismissBoxValue.EndToStart ->
                                                    MaterialTheme.colorScheme.errorContainer
                                                else -> Color.Transparent
                                            }
                                            val alignment = when (direction) {
                                                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                                else -> Alignment.CenterEnd
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(bgColor)
                                                    .padding(horizontal = 20.dp),
                                                contentAlignment = alignment
                                            ) {
                                                when (direction) {
                                                    SwipeToDismissBoxValue.StartToEnd -> {
                                                        Icon(
                                                            if (isPinned) Icons.Default.Close
                                                            else Icons.Default.PushPin,
                                                            contentDescription = if (isPinned) stringResource(R.string.cd_unpin) else stringResource(R.string.cd_pin),
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }
                                                    SwipeToDismissBoxValue.EndToStart -> {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = stringResource(R.string.cd_delete),
                                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                                        )
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        },
                                        enableDismissFromStartToEnd = true,
                                        enableDismissFromEndToStart = true
                                    ) {
                                        ConversationItem(
                                            conversation = conversation,
                                            isPinned = isPinned,
                                            onClick = {
                                                onConversationClick(conversation.threadId, conversation.address)
                                            }
                                        )
                                    }
                                }

                                // Divider between items
                                if (index < conversations.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 80.dp, end = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(
                                            alpha = 0.5f
                                        )
                                    )
                                }
                            }

                            // ── Global Message Search Results ──
                            if (searchQuery.length >= 2 && messageSearchResults.isNotEmpty()) {
                                item {
                                    Text(
                                        stringResource(R.string.search_messages_header),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                                    )
                                }
                                items(
                                    messageSearchResults,
                                    key = { "msg_${it.id}" }
                                ) { msg ->
                                    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
                                    Surface(
                                        onClick = { onConversationClick(msg.threadId, msg.sender) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = stringResource(R.string.cd_search_result),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = msg.sender,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = msg.text.take(120),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = dateFormat.format(Date(msg.timestamp)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        conversationToDelete?.let { conv ->
                            AlertDialog(
                                onDismissRequest = { conversationToDelete = null },
                                title = { Text(stringResource(R.string.delete_conversation_title)) },
                                text = { Text(stringResource(R.string.delete_conversation_body, conv.displayName)) },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            viewModel.deleteConversation(conv.threadId)
                                            conversationToDelete = null
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) { Text(stringResource(R.string.delete)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { conversationToDelete = null }) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Conversation Item ─────────────────────────────────────────────────────────

@Composable
private fun ConversationItem(
    conversation: Conversation,
    isPinned: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )

    val isUnread = conversation.unreadCount > 0
    val context = LocalContext.current
    val timeString = formatTimestamp(conversation.lastTimestamp, context)

    // Avatar color from name hash
    val avatarColor = remember(conversation.displayName) {
        avatarColorForName(conversation.displayName)
    }
    val avatarLetter = conversation.displayName
        .firstOrNull()?.uppercase() ?: "#"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        color = if (isUnread) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // ── Avatar ──
            Box(contentAlignment = Alignment.Center) {
                val context = LocalContext.current
                if (conversation.photoUri != null) {
                    val bitmap = remember(conversation.photoUri) {
                        try {
                            val uri = Uri.parse(conversation.photoUri)
                            val inputStream = context.contentResolver.openInputStream(uri)
                            inputStream?.use { BitmapFactory.decodeStream(it) }
                        } catch (e: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = conversation.displayName,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Letter fallback (photo URI failed to load)
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(avatarColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = avatarLetter,
                                color = avatarColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    }
                } else {
                    // No photo URI — letter avatar
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(avatarColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = avatarLetter,
                            color = avatarColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }

                // Risk indicator dot
                if (conversation.isFlagged) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(ScamRed)
                    )
                }
            }

            // ── Content ──
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Top row: name + pin indicator + timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = conversation.displayName,
                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isPinned) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = stringResource(R.string.cd_pinned),
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(14.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUnread) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        },
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                // Bottom row: last message preview + unread badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = conversation.lastMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isUnread) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        },
                        fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )

                    if (isUnread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        UnreadCountBadge(count = conversation.unreadCount)
                    }
                }
            }
        }
    }
}

// ── Unread Count Badge ────────────────────────────────────────────────────────

@Composable
private fun UnreadCountBadge(count: Int) {
    val displayText = if (count > 99) "99+" else count.toString()
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

// ── Filter Chip Row ───────────────────────────────────────────────────────────

@Composable
private fun ConversationFilterChipRow(
    selectedFilter: ConversationFilter,
    onFilterSelected: (ConversationFilter) -> Unit,
    conversationCounts: Triple<Int, Int, Int>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ConversationFilter.entries) { filter ->
            val filterColor = when (filter) {
                ConversationFilter.ALL -> MaterialTheme.colorScheme.primary
                ConversationFilter.UNREAD -> BrandIndigo
                ConversationFilter.FLAGGED -> ScamRed
            }

            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = stringResource(filter.labelRes),
                        fontWeight = if (selectedFilter == filter) FontWeight.SemiBold
                        else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = filterColor,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

// ── Shimmer Loading Skeleton ──────────────────────────────────────────────────

@Composable
private fun ShimmerConversationItem() {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, translateAnim - 200f),
        end = Offset(translateAnim, translateAnim)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(brush)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Name row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
            // Message preview
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
    }
}

// ── Empty State ───────────────────────────────────────────────────────────────

@Composable
private fun ConversationEmptyState(
    hasSmsPermission: Boolean,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    hasActiveFilter: Boolean = false
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            if (!hasSmsPermission) {
                // Permission denied state
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Text(
                    text = stringResource(R.string.sms_permission_required),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.sms_permission_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text(stringResource(R.string.open_settings))
                }
            } else if (isSearchActive) {
                // Search with no results
                Text(
                    text = stringResource(R.string.no_results),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = stringResource(R.string.no_search_results_for, searchQuery),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            } else {
                // Normal empty or filtered empty
                Text(
                    text = if (hasActiveFilter) stringResource(R.string.no_results) else stringResource(R.string.no_messages),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Light
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (hasActiveFilter) stringResource(R.string.no_conversations_found)
                    else stringResource(R.string.no_messages_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (hasActiveFilter)
                        stringResource(R.string.try_adjusting_filter)
                    else
                        stringResource(R.string.start_conversation_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Utility Functions ─────────────────────────────────────────────────────────

private fun formatTimestamp(timestamp: Long, context: android.content.Context): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val calendar = Calendar.getInstance()
    val msgCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }

    return when {
        // Today — show time
        calendar.get(Calendar.DAY_OF_YEAR) == msgCalendar.get(Calendar.DAY_OF_YEAR) &&
            calendar.get(Calendar.YEAR) == msgCalendar.get(Calendar.YEAR) -> {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        }
        // Yesterday
        diff < 2 * 24 * 60 * 60 * 1000L &&
            calendar.get(Calendar.DAY_OF_YEAR) - msgCalendar.get(Calendar.DAY_OF_YEAR) == 1 -> {
            context.getString(R.string.yesterday)
        }
        // This week — show day name
        diff < 7 * 24 * 60 * 60 * 1000L -> {
            SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
        }
        // This year — show month and day
        calendar.get(Calendar.YEAR) == msgCalendar.get(Calendar.YEAR) -> {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
        // Older — show full date
        else -> {
            SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

/**
 * Generate a deterministic avatar color from a display name.
 */
private fun avatarColorForName(name: String): Color {
    val colors = listOf(
        Color(0xFF6750A4), // Purple
        Color(0xFF7C4DFF), // Deep purple
        Color(0xFF536DFE), // Indigo
        Color(0xFF2196F3), // Blue
        Color(0xFF00897B), // Teal
        Color(0xFF2E7D32), // Green
        Color(0xFFEF6C00), // Orange
        Color(0xFFD32F2F), // Red
        Color(0xFFC2185B), // Pink
        Color(0xFF5E35B1), // Deep purple variant
    )
    val index = name.hashCode().absoluteValue % colors.size
    return colors[index]
}
