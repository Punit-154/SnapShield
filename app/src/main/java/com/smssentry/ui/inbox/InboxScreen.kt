package com.smssentry.ui.inbox

import android.content.Intent
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smssentry.R
import com.smssentry.data.model.SmsMessage
import com.smssentry.deepcheck.data.ModelRepository
import com.smssentry.ui.components.ShieldBadge
import com.smssentry.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onMessageClick: (String) -> Unit,
    themeRepository: ThemePreferenceRepository,
    viewModel: InboxViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val modelState by viewModel.modelState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showThemeMenu by remember { mutableStateOf(false) }
    val currentThemeMode by themeRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

    val isDefaultSmsApp by viewModel.isDefaultSmsAppState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshMessages()
        viewModel.checkDefaultSmsApp()
    }

    // Track when loading finishes to reset refreshing
    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "\uD83D\uDEE1\uFE0F",
                            fontSize = 22.sp
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showThemeMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.theme))
                        }
                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                            Text(
                                text = stringResource(R.string.theme),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            ThemeMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = mode.label,
                                            color = if (currentThemeMode == mode) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    },
                                    onClick = {
                                        scope.launch {
                                            themeRepository.setThemeMode(mode)
                                        }
                                        showThemeMenu = false
                                    },
                                    leadingIcon = {
                                        RadioButton(
                                            selected = currentThemeMode == mode,
                                            onClick = null
                                        )
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = {
                        isRefreshing = true
                        viewModel.refreshMessages()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Default SMS app warning
            AnimatedVisibility(
                visible = !isDefaultSmsApp,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.default_sms_required),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                context.startActivity(intent)
                            }
                        ) {
                            Text(stringResource(R.string.set_default))
                        }
                    }
                }
            }

            // Model status badge
            ModelStatusBadge(modelState)

            // Search bar
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        onSearch = { /* no-op, filtering is live */ },
                        expanded = false,
                        onExpandedChange = { /* no-op */ },
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        },
                    )
                },
                expanded = false,
                onExpandedChange = { /* no-op */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {}

            // Filter chips
            FilterChipRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { viewModel.onFilterSelected(it) }
            )

            // Content area with pull to refresh
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.refreshMessages()
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading && !isRefreshing -> {
                        // Shimmer loading skeleton
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(6) {
                                ShimmerSmsCard()
                            }
                        }
                    }
                    messages.isEmpty() && !isLoading -> {
                        // Empty state
                        EmptyInboxState(
                            isDefaultSmsApp = isDefaultSmsApp,
                            hasActiveFilter = selectedFilter != SmsFilter.ALL || searchQuery.isNotEmpty()
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(messages, key = { _, msg -> msg.id }) { index, message ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { dismissValue ->
                                        if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                            viewModel.deleteMessage(message.id)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = context.getString(R.string.msg_deleted),
                                                    actionLabel = context.getString(R.string.undo),
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

                                // Staggered entrance
                                var itemVisible by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    kotlinx.coroutines.delay(index * 40L)
                                    itemVisible = true
                                }

                                val itemAlpha by animateFloatAsState(
                                    targetValue = if (itemVisible) 1f else 0f,
                                    animationSpec = tween(300),
                                    label = "itemAlpha"
                                )

                                Box(
                                    modifier = Modifier
                                        .alpha(itemAlpha)
                                ) {
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        backgroundContent = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(MaterialTheme.colorScheme.errorContainer),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(16.dp),
                                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        },
                                        enableDismissFromStartToEnd = false
                                    ) {
                                        SmsMessageCard(
                                            message = message,
                                            onClick = { onMessageClick(message.id) }
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
}

@Composable
private fun EmptyInboxState(
    isDefaultSmsApp: Boolean,
    hasActiveFilter: Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = if (hasActiveFilter) "\uD83D\uDD0D" else "\uD83D\uDCEC",
                fontSize = 56.sp
            )
            Text(
                text = if (!isDefaultSmsApp) {
                    stringResource(R.string.set_default_to_view)
                } else if (hasActiveFilter) {
                    stringResource(R.string.no_messages)
                } else {
                    "Your inbox is empty"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = if (!isDefaultSmsApp) {
                    "SMSentry needs to be your default SMS app to protect your messages."
                } else if (hasActiveFilter) {
                    "Try adjusting your filters or search query."
                } else {
                    "Messages will appear here once they arrive."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ShimmerSmsCard() {
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(brush)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                // Body placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                // Badge placeholder
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brush)
                )
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    selectedFilter: SmsFilter,
    onFilterSelected: (SmsFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SmsFilter.entries) { filter ->
            val filterColor = when (filter) {
                SmsFilter.ALL -> MaterialTheme.colorScheme.primary
                SmsFilter.SCAM -> ScamRed
                SmsFilter.SUSPICIOUS -> SuspiciousOrange
                SmsFilter.SAFE -> SafeGreen
            }

            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(stringResource(when(filter) {
                    SmsFilter.ALL -> R.string.filter_all
                    SmsFilter.SCAM -> R.string.filter_scam
                    SmsFilter.SUSPICIOUS -> R.string.filter_suspicious
                    SmsFilter.SAFE -> R.string.filter_safe
                })) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = filterColor,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun ModelStatusBadge(state: ModelRepository.State) {
    val textRes = when (state) {
        ModelRepository.State.IDLE -> R.string.model_not_downloaded
        ModelRepository.State.DOWNLOADING -> R.string.model_downloading
        ModelRepository.State.LOADING -> R.string.model_loading
        ModelRepository.State.READY -> R.string.model_ready
        ModelRepository.State.VERIFYING -> R.string.verifying_download
        ModelRepository.State.FAILED -> R.string.model_unavailable
    }

    val (bgColor, textColor, icon) = when (state) {
        ModelRepository.State.READY -> Triple(
            SafeGreenBackground,
            SafeGreenDark,
            "✓ "
        )
        ModelRepository.State.IDLE, ModelRepository.State.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "⚠ "
        )
        else -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "⏳ "
        )
    }

    Surface(
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon + stringResource(textRes),
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SmsMessageCard(
    message: SmsMessage,
    onClick: () -> Unit
) {
    var badgeVisible by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "scale"
    )

    LaunchedEffect(message.classification) {
        badgeVisible = false
        kotlinx.coroutines.delay(100)
        badgeVisible = true
    }

    val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val timeString = dateFormat.format(Date(message.timestamp))

    val avatarLetter = message.sender.firstOrNull()?.uppercase() ?: "?"
    val riskLabel = message.classification?.label?.uppercase()
    val avatarColor = when (riskLabel) {
        "SCAM" -> ScamRed
        "SUSPICIOUS" -> SuspiciousOrange
        "SAFE" -> SafeGreen
        else -> LowGray
    }

    val riskScore = message.classification?.riskScore ?: 0

    // Card border tint for high risk
    val cardContainerColor = when (riskLabel) {
        "SCAM" -> MaterialTheme.colorScheme.surfaceContainerHigh
        "SUSPICIOUS" -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardContainerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = avatarLetter,
                    color = avatarColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Header row: sender + time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.sender,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                // Message preview
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Risk badge + progress bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(
                        visible = badgeVisible,
                        enter = scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeIn(),
                        exit = fadeOut()
                    ) {
                        ShieldBadge(
                            label = message.classification?.label ?: stringResource(R.string.filter_all),
                            riskScore = riskScore
                        )
                    }

                    LinearProgressIndicator(
                        progress = { riskScore / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = when {
                            riskScore >= 70 -> ScamRed
                            riskScore >= 40 -> SuspiciousOrange
                            else -> SafeGreen
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}
