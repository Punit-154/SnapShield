package com.smssentry.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smssentry.data.model.SmsMessage
import com.smssentry.deepcheck.ModelManager
import com.smssentry.ui.components.ShieldBadge
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onMessageClick: (String) -> Unit,
    viewModel: InboxViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val modelState by viewModel.modelState.collectAsState()

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
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ModelStatusBadge(modelState)

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        SmsMessageItem(
                            message = message,
                            onClick = { onMessageClick(message.id) }
                        )
                    }
                }
            }
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
private fun SmsMessageItem(
    message: SmsMessage,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = dateFormat.format(Date(message.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.sender,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            message.classification?.let { classification ->
                ShieldBadge(
                    label = classification.label,
                    riskScore = classification.riskScore
                )
            }
        }
    }
}
