package com.smssentry.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smssentry.data.model.EvidenceItem
import com.smssentry.ui.theme.*

@Composable
fun EvidenceCard(
    evidence: EvidenceItem,
    modifier: Modifier = Modifier,
    index: Int = 0
) {
    val (severityColor, severityBackground) = getSeverityColors(evidence.severity)
    val severityIcon = getSeverityIcon(evidence.severity)

    // Staggered entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 80L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn()
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = severityBackground.copy(alpha = 0.6f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Severity icon with colored dot
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = severityIcon,
                        fontSize = 20.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(severityColor)
                    )
                }

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
                            text = evidence.source,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = severityColor
                        )
                        SeverityTag(
                            severity = evidence.severity,
                            contentColor = severityColor,
                            containerColor = severityBackground
                        )
                    }

                    HorizontalDivider(
                        color = severityColor.copy(alpha = 0.15f),
                        thickness = 0.5.dp
                    )

                    Text(
                        text = evidence.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SeverityTag(
    severity: String,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = severity.uppercase(),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

private fun getSeverityIcon(severity: String): String {
    return when (severity.uppercase()) {
        "CRITICAL" -> "\uD83D\uDED1"  // 🛑
        "HIGH" -> "⚠\uFE0F"           // ⚠️
        "MEDIUM" -> "\uD83D\uDD36"    // 🔶
        "LOW" -> "ℹ\uFE0F"            // ℹ️
        else -> "•"
    }
}

private fun getSeverityColors(severity: String): Pair<Color, Color> {
    return when (severity.uppercase()) {
        "CRITICAL" -> ScamRed to ScamRedBackground
        "HIGH" -> SuspiciousOrange to SuspiciousOrangeBackground
        "MEDIUM" -> MediumYellow to SuspiciousOrangeBackground.copy(alpha = 0.5f)
        "LOW" -> LowGray to Color.LightGray.copy(alpha = 0.2f)
        else -> LowGray to Color.LightGray.copy(alpha = 0.2f)
    }
}
