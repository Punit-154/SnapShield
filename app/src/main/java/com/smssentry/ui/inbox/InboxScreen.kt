package com.smssentry.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smssentry.data.model.SmsMessage
import java.text.SimpleDateFormat
import java.util.*

// ── Dark theme palette ──
private val BG = Color(0xFF0E0E0E)
private val SURFACE = Color(0xFF1A1A1A)
private val SURFACE2 = Color(0xFF242424)
private val DIVIDER = Color(0xFF2A2A2A)
private val TEXT_PRIMARY = Color(0xFFEEEEEE)
private val TEXT_SECONDARY = Color(0xFF888888)
private val TEXT_HINT = Color(0xFF555555)
private val SCAM = Color(0xFFE53935)
private val WARN = Color(0xFFFB8C00)
private val SAFE = Color(0xFF43A047)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onMessageClick: (String) -> Unit,
    smsPermissionGranted: Boolean,
    viewModel: InboxViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val usedMockData by viewModel.usedMockData.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    LaunchedEffect(smsPermissionGranted) {
        viewModel.loadMessages(smsPermissionGranted)
    }

    val filtered = messages.filter { msg ->
        val matchesSearch = searchQuery.isEmpty() ||
                msg.sender.contains(searchQuery, ignoreCase = true) ||
                msg.text.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            "Scam" -> msg.classification?.label == "SCAM"
            "Suspicious" -> msg.classification?.label == "SUSPICIOUS"
            "Safe" -> msg.classification?.label == "SAFE"
            else -> true
        }
        matchesSearch && matchesFilter
    }

    val scamCount = messages.count { it.classification?.label == "SCAM" }
    val suspiciousCount = messages.count { it.classification?.label == "SUSPICIOUS" }
    val safeCount = messages.count { it.classification?.label == "SAFE" }

    Scaffold(containerColor = BG) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // ── Header ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SURFACE)
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SMSentry",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TEXT_PRIMARY
                            )
                            if (!isLoading) {
                                Text(
                                    text = "${messages.size} messages scanned",
                                    fontSize = 13.sp,
                                    color = TEXT_SECONDARY
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SURFACE2),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🛡️", fontSize = 18.sp)
                        }
                    }

                    if (!isLoading && messages.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatPill("$scamCount Scams", SCAM, Modifier.weight(1f))
                            StatPill("$suspiciousCount Suspicious", WARN, Modifier.weight(1f))
                            StatPill("$safeCount Safe", SAFE, Modifier.weight(1f))
                        }
                    }
                }
                HorizontalDivider(color = DIVIDER, thickness = 1.dp)
            }

            // ── Search ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SURFACE)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search messages or senders...",
                                color = TEXT_HINT,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = TEXT_HINT,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TEXT_SECONDARY,
                            unfocusedBorderColor = DIVIDER,
                            focusedTextColor = TEXT_PRIMARY,
                            unfocusedTextColor = TEXT_PRIMARY,
                            cursorColor = TEXT_PRIMARY,
                            focusedContainerColor = SURFACE2,
                            unfocusedContainerColor = SURFACE2
                        ),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )
                }
                HorizontalDivider(color = DIVIDER, thickness = 1.dp)
            }

            // ── Filter chips ──
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SURFACE)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf("All", "Scam", "Suspicious", "Safe")) { filter ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) TEXT_PRIMARY else SURFACE2)
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = filter,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) BG else TEXT_SECONDARY
                            )
                        }
                    }
                }
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(BG)
                )
            }

            // ── Demo banner ──
            if (usedMockData) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2A2000))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚠️", fontSize = 14.sp)
                        Text(
                            "Demo mode — showing sample messages",
                            fontSize = 13.sp,
                            color = Color(0xFFD4A017)
                        )
                    }
                }
            }

            // ── Loading ──
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = TEXT_PRIMARY,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                "Scanning messages...",
                                fontSize = 13.sp,
                                color = TEXT_SECONDARY
                            )
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { message ->
                    MessageRow(
                        message = message,
                        onClick = { onMessageClick(message.id) }
                    )
                }

                if (filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No messages found",
                                fontSize = 14.sp,
                                color = TEXT_HINT
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, dotColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SURFACE2)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = TEXT_SECONDARY,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MessageRow(message: SmsMessage, onClick: () -> Unit) {
    val classification = message.classification
    val labelColor = when (classification?.label) {
        "SCAM" -> SCAM
        "SUSPICIOUS" -> WARN
        "SAFE" -> SAFE
        else -> TEXT_HINT
    }

    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val timeString = remember(message.timestamp) {
        dateFormat.format(Date(message.timestamp))
    }

    val initials = remember(message.sender) {
        message.sender.filter { it.isLetter() }.take(2).uppercase().ifEmpty { "#" }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SURFACE)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(labelColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = labelColor
                )
            }

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.sender,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TEXT_PRIMARY,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeString,
                        fontSize = 11.sp,
                        color = TEXT_HINT
                    )
                }
                Text(
                    text = message.text,
                    fontSize = 13.sp,
                    color = TEXT_SECONDARY,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            // Label badge
            classification?.let {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(labelColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = when (it.label) {
                                "SCAM" -> "SCAM"
                                "SUSPICIOUS" -> "WARN"
                                else -> "SAFE"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = labelColor
                        )
                    }
                    Text(
                        text = "${it.riskScore}",
                        fontSize = 11.sp,
                        color = TEXT_HINT
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 70.dp),
            color = DIVIDER,
            thickness = 0.5.dp
        )
    }
}