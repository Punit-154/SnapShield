package com.smssentry.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.smssentry.data.model.SmsMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SentBubbleShape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
private val ReceivedBubbleShape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: SmsMessage,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeText = timeFormat.format(Date(message.timestamp))

    val isSent = message.isSent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 48.dp, max = maxBubbleWidth)
                    .clip(if (isSent) SentBubbleShape else ReceivedBubbleShape)
                    .background(
                        if (isSent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSent) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Timestamp + delivery indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isSent) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (message.isRead) Icons.Default.DoneAll else Icons.Default.Done,
                        contentDescription = if (message.isRead) "Read" else "Delivered",
                        modifier = Modifier.size(14.dp),
                        tint = if (message.isRead) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
