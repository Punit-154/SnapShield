package com.smssentry.ui.inbox

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smssentry.data.model.SmsMessage
import com.smssentry.deepcheck.ModelManager
import com.smssentry.ui.components.ShieldBadge
import com.smssentry.ui.theme.*
import com.smssentry.ui.theme.ThemeMode
import com.smssentry.ui.theme.ThemePreferenceRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

sealed class InboxListItem {
    data class DateHeader(val dateLabel: String, val dateKey: String) : InboxListItem()
    data class MessageItem(val message: SmsMessage) : InboxListItem()
}

fun groupMessagesByDate(messages: List<SmsMessage>): List<InboxListItem> {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val items = mutableListOf<InboxListItem>()
    var lastDateKey = ""

    for (message in messages) {
        val messageDate = Instant.ofEpochMilli(message.timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val dateKey = when (messageDate) {
            today -> "today"
            yesterday -> "yesterday"
            else -> messageDate.toString()
        }

        if (dateKey != lastDateKey) {
            val dateLabel = when (messageDate) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> {
                    val day = messageDate.dayOfMonth
                    val suffix = when {
                        day in 11..13 -> "th"
                        day % 10 == 1 -> "st"
                        day % 10 == 2 -> "nd"
                        day % 10 == 3 -> "rd"
                        else -> "th"
                    }
                    val month = messageDate.format(DateTimeFormatter.ofPattern("MMM"))
                    val year = messageDate.year
                    val currentYear = today.year
                    if (year == currentYear) {
                        "$day$suffix $month"
                    } else {
                        "$day$suffix $month $year"
                    }
                }
            }
            items.add(InboxListItem.DateHeader(dateLabel, dateKey))
            lastDateKey = dateKey
        }

        items.add(InboxListItem.MessageItem(message))
    }

    return items
}

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
    val showDefaultSmsPrompt by viewModel.showDefaultSmsPrompt.collectAsState()
    val inaccessibleReason by viewModel.inaccessibleReason.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showThemeMenu by remember { mutableStateOf(false) }
    val currentThemeMode by themeRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

    val defaultSmsRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refreshMessages()
        }
    }

    val groupedItems = remember(messages) { groupMessagesByDate(messages) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SMS Sentry",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    Box {
                        IconButton(onClick = { showThemeMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                            Text(
                                text = "Theme",
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
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ModelStatusBadge(modelState)

            if (showDefaultSmsPrompt) {
                DefaultSmsPromptCard(
                    reason = inaccessibleReason,
                    onSetDefault = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val roleManager = context.getSystemService(RoleManager::class.java)
                            if (roleManager != null) {
                                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                                defaultSmsRoleLauncher.launch(intent)
                            }
                        } else {
                            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onSearch = { },
                active = false,
                onActiveChange = { },
                placeholder = { Text("Search by sender, text, or classification") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {}

            FilterChipRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { viewModel.onFilterSelected(it) }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (messages.isEmpty() && !showDefaultSmsPrompt) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages match your filter",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = groupedItems,
                        key = { item ->
                            when (item) {
                                is InboxListItem.DateHeader -> "header_${item.dateKey}"
                                is InboxListItem.MessageItem -> "msg_${item.message.id}"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is InboxListItem.DateHeader -> {
                                DateHeaderRow(dateLabel = item.dateLabel)
                            }
                            is InboxListItem.MessageItem -> {
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { dismissValue ->
                                        if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                            viewModel.deleteMessage(item.message.id)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Message deleted",
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
                                                contentDescription = "Delete",
                                                modifier = Modifier.padding(16.dp),
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    },
                                    enableDismissFromStartToEnd = false
                                ) {
                                    SmsMessageCard(
                                        message = item.message,
                                        onClick = { onMessageClick(item.message.id) }
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

@Composable
private fun DateHeaderRow(
    dateLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun DefaultSmsPromptCard(
    reason: String?,
    onSetDefault: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SMS Access Required",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Text(
                text = reason ?: "This app needs to be set as the default SMS app to read and analyze your messages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSetDefault,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Set as Default SMS App")
                }

                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Open Settings")
                }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SmsFilter.entries) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun ModelStatusBadge(state: ModelManager.State) {
    val (text, color) = when (state) {
        ModelManager.State.NOT_DOWNLOADED -> "Model not downloaded" to MaterialTheme.colorScheme.errorContainer
        ModelManager.State.DOWNLOADING -> "Downloading model…" to MaterialTheme.colorScheme.secondaryContainer
        ModelManager.State.LOADING -> "Loading model…" to MaterialTheme.colorScheme.secondaryContainer
        ModelManager.State.READY -> "Deep Check ready" to MaterialTheme.colorScheme.primaryContainer
        ModelManager.State.FAILED -> "Model unavailable" to MaterialTheme.colorScheme.errorContainer
    }

    Surface(
        color = color,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = when (state) {
                ModelManager.State.READY -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onErrorContainer
            }
        )
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
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
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
    val avatarColor = when (message.classification?.label?.uppercase()) {
        "SCAM" -> ScamRed
        "SUSPICIOUS" -> SuspiciousOrange
        "SAFE" -> SafeGreen
        else -> LowGray
    }

    val riskScore = message.classification?.riskScore ?: 0

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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.15f)),
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

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
                            label = message.classification?.label ?: "Unknown",
                            riskScore = riskScore
                        )
                    }

                    LinearProgressIndicator(
                        progress = { riskScore / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = when {
                            riskScore >= 70 -> ScamRed
                            riskScore >= 40 -> SuspiciousOrange
                            else -> SafeGreen
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Text(
                        text = "$riskScore",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
