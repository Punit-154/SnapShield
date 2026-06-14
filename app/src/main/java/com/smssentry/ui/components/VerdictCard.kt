package com.smssentry.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val isScam = verdict.isScam
    val statusColor: Color
    val statusContainerColor: Color
    val statusOnContainerColor: Color
    val gradientColors: List<Color>
    val icon: String
    val statusLabel: String

    when (verdict.verdictLabel) {
        "SCAM" -> {
            statusColor = ScamRed
            statusContainerColor = ScamRedBackground
            statusOnContainerColor = ScamRedDark
            gradientColors = listOf(ScamRed.copy(alpha = 0.1f), ScamRedBackground)
            icon = "\uD83D\uDED1"
            statusLabel = "SCAM DETECTED"
        }
        "SUSPICIOUS" -> {
            statusColor = SuspiciousOrangeDark
            statusContainerColor = SuspiciousOrangeBackground
            statusOnContainerColor = SuspiciousOrangeDark
            gradientColors = listOf(SuspiciousOrange.copy(alpha = 0.1f), SuspiciousOrangeBackground)
            icon = "⚠\uFE0F"
            statusLabel = "SUSPICIOUS MESSAGE"
        }
        else -> {
            statusColor = SafeGreen
            statusContainerColor = SafeGreenBackground
            statusOnContainerColor = SafeGreenDark
            gradientColors = listOf(SafeGreen.copy(alpha = 0.1f), SafeGreenBackground)
            icon = "\uD83D\uDEE1\uFE0F"
            statusLabel = "MESSAGE SAFE"
        }
    }

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val entranceScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "entranceScale"
    )

    // Icon pulse
    val iconScale by rememberInfiniteTransition(label = "verdict").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(entranceScale),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(gradientColors)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon badge
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(statusContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = icon,
                        fontSize = 40.sp,
                        modifier = Modifier.scale(iconScale)
                    )
                }

                // Status label
                Text(
                    text = statusLabel,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = statusColor,
                    letterSpacing = 1.5.sp
                )

                // Threat type
                verdict.threatType?.let { type ->
                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = type.replace("_", " ").uppercase(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = statusOnContainerColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Divider
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    color = statusColor.copy(alpha = 0.2f)
                )

                // Full educational explanation (falls back to summary if blank)
                val displayText = verdict.educationalExplanation.ifBlank { verdict.summary }
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                )

                // Recommended actions
                if (verdict.recommendedActions.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Recommended Actions",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        verdict.recommendedActions.forEach { action ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = if (isScam) "⚠️" else "✓",
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = action,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Share button
                if (onShareClick != null) {
                    OutlinedButton(
                        onClick = onShareClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = statusColor
                        )
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share verdict",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Verdict", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
