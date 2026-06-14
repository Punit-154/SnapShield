package com.smssentry.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smssentry.data.model.DeepCheckVerdict
import com.smssentry.ui.theme.*

@Composable
fun VerdictCard(
    verdict: DeepCheckVerdict,
    modifier: Modifier = Modifier,
    onShareClick: (() -> Unit)? = null
) {
    val statusColor: Color
    val statusContainerColor: Color
    val statusLabel: String

    when (verdict.verdictLabel) {
        "SCAM" -> {
            statusColor = ScamRed
            statusContainerColor = ScamRedBackground
            statusLabel = "SCAM"
        }
        "SUSPICIOUS" -> {
            statusColor = SuspiciousOrange
            statusContainerColor = SuspiciousOrangeBackground
            statusLabel = "SUSPICIOUS"
        }
        else -> {
            statusColor = SafeGreen
            statusContainerColor = SafeGreenBackground
            statusLabel = "SAFE"
        }
    }

    // Subtle entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "entranceAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Thin colored left border strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header row: severity pill + threat type
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Small colored dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    // Severity label pill
                    Surface(
                        color = statusContainerColor,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = statusColor,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // Threat type tag
                    verdict.threatType?.let { type ->
                        Text(
                            text = type.replace("_", " ").uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Confidence bar (thin, subtle)
                verdict.evidence.size.let {
                    val confidence = when (verdict.verdictLabel) {
                        "SCAM" -> 85
                        "SUSPICIOUS" -> 60
                        else -> 20
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Confidence",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = confidence / 100f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(statusColor.copy(alpha = 0.7f))
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )

                // Full explanation text
                val displayText = verdict.educationalExplanation.ifBlank { verdict.summary }
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    lineHeight = 22.sp
                )

                // Recommended actions as a clean bulleted list
                if (verdict.recommendedActions.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Recommended Actions",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        verdict.recommendedActions.forEach { action ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    fontSize = 14.sp,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = action,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // Evidence items (compact)
                if (verdict.evidence.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Evidence",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        verdict.evidence.forEach { item ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "–",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = item.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // Share button (understated)
                if (onShareClick != null) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )
                    TextButton(
                        onClick = onShareClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share verdict",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Share",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
