package com.snapshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snapshield.data.model.EvidenceItem
import com.snapshield.ui.theme.*

@Composable
fun EvidenceCard(
    evidence: EvidenceItem,
    modifier: Modifier = Modifier
) {
    val (severityColor, severityBackground) = getSeverityColors(evidence.severity)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Extracted Tag for better readability
            SeverityTag(
                severity = evidence.severity,
                contentColor = severityColor,
                containerColor = severityBackground
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = evidence.source,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = evidence.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = severity.uppercase(),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun getSeverityColors(severity: String): Pair<Color, Color> {
    return when (severity.uppercase()) {
        "CRITICAL" -> CriticalRed to ScamRedBackground
        "HIGH" -> HighOrange to SuspiciousOrangeBackground
        "MEDIUM" -> MediumYellow to SafeGreenBackground // Note: SafeGreenBackground might be a typo for Medium
        "LOW" -> LowGray to Color.LightGray.copy(alpha = 0.3f)
        else -> LowGray to Color.LightGray.copy(alpha = 0.3f)
    }
}
