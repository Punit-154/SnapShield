package com.smssentry.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smssentry.ui.theme.*

@Composable
fun RiskScoreBar(
    riskScore: Int,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    animate: Boolean = true
) {
    val color = when {
        riskScore <= 30 -> SafeGreen
        riskScore <= 70 -> SuspiciousOrange
        else -> ScamRed
    }

    val gradientColors = when {
        riskScore <= 30 -> listOf(SafeGreen, SafeGreenLight)
        riskScore <= 70 -> listOf(SuspiciousOrange, SuspiciousOrangeLight)
        else -> listOf(ScamRedDark, ScamRed)
    }

    // Animated progress
    val animatedProgress by animateFloatAsState(
        targetValue = if (animate) riskScore / 100f else riskScore / 100f,
        animationSpec = tween(
            durationMillis = if (animate) 1200 else 0,
            easing = FastOutSlowInEasing
        ),
        label = "riskProgress"
    )

    // Animated score counter
    val animatedScore by animateIntAsState(
        targetValue = riskScore,
        animationSpec = tween(
            durationMillis = if (animate) 1200 else 0,
            easing = FastOutSlowInEasing
        ),
        label = "riskScore"
    )

    Column(modifier = modifier) {
        if (showLabel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Risk Score",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "$animatedScore",
                        fontWeight = FontWeight.Bold,
                        color = color,
                        fontSize = 24.sp
                    )
                    Text(
                        text = "/100",
                        fontWeight = FontWeight.Normal,
                        color = color.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Progress fill with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        brush = Brush.horizontalGradient(gradientColors)
                    )
            )
        }

        // Scale labels
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Safe",
                style = MaterialTheme.typography.labelSmall,
                color = SafeGreen.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
            Text(
                text = "Suspicious",
                style = MaterialTheme.typography.labelSmall,
                color = SuspiciousOrange.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
            Text(
                text = "Scam",
                style = MaterialTheme.typography.labelSmall,
                color = ScamRed.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
        }
    }
}
