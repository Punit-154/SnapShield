package com.smssentry.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smssentry.data.model.DeepCheckVerdict
import com.smssentry.ui.theme.*

@Composable
fun VerdictCard(
    verdict: DeepCheckVerdict,
    modifier: Modifier = Modifier
) {
    val statusColor = if (verdict.isScam) ScamRed else SafeGreen
    val statusBackground = if (verdict.isScam) ScamRedBackground else SafeGreenBackground

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(statusBackground, RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (verdict.isScam) "\uD83D\uDED1" else "\uD83D\uDEE1\uFE0F",
                    fontSize = 32.sp
                )
            }

            Text(
                text = if (verdict.isScam) "SCAM DETECTED" else "MESSAGE SAFE",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = statusColor
            )

            verdict.threatType?.let { type ->
                Text(
                    text = type.replace("_", " ").uppercase(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Text(
                text = verdict.summary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (verdict.recommendedActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Recommended Actions",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                verdict.recommendedActions.forEach { action ->
                    Text(
                        text = "\u2022 $action",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
