package com.smssentry.ui.conversations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smssentry.data.model.Conversation
import com.smssentry.ui.theme.*
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

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Reset refreshing when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
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
                                        "Search conversations…",
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
                            Text(
                                text = "Messages",
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
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
                            contentDescription = if (isSearchExpanded) "Close search" else "Search"
                        )
                    }

                    // Settings
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }

                    // Overflow menu
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                onClick = {
                                    showOverflowMenu = false
                                    isRefreshing = true
                                    viewModel.refresh()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Mark all as read") },
                                onClick = { showOverflowMenu = false }
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
                    contentDescription = "Compose message",
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
                            hasActiveFilter = selectedFilter != ConversationFilter.ALL
                                || searchQuery.isNotEmpty()
                        )
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                conversations,
                                key = { _, convo -> convo.threadId }
                            ) { index, conversation ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { dismissValue ->
                                        if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                            viewModel.deleteConversation(conversation.threadId)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Conversation deleted",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.undoLastDelete()
                                                }
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                )

                                // Staggered entrance animation
                                var itemVisible by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    delay(index * 30L)
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
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.errorContainer),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    modifier = Modifier.padding(24.dp),
                                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        },
                                        enableDismissFromStartToEnd = false
                                    ) {
                                        ConversationItem(
                                            conversation = conversation,
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
    val timeString = formatTimestamp(conversation.lastTimestamp)

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
                // Top row: name + timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.displayName,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                        text = filter.label,
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
private fun ConversationEmptyState(hasActiveFilter: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Text(
                text = if (hasActiveFilter) "No results" else "No messages",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                fontWeight = FontWeight.Light
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasActiveFilter) "No conversations found"
                else "No messages yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (hasActiveFilter)
                    "Try adjusting your search or filter."
                else
                    "Start a conversation by tapping the compose button below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Utility Functions ─────────────────────────────────────────────────────────

private fun formatTimestamp(timestamp: Long): String {
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
            "Yesterday"
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
