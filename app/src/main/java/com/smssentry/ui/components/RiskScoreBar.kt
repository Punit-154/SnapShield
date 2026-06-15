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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.smssentry.R
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

    // Animated progress
    val animatedProgress by animateFloatAsState(
        targetValue = riskScore / 100f,
        animationSpec = tween(
            durationMillis = if (animate) 1000 else 0,
            easing = FastOutSlowInEasing
        ),
        label = "riskProgress"
    )

    // Animated score counter
    val animatedScore by animateIntAsState(
        targetValue = riskScore,
        animationSpec = tween(
            durationMillis = if (animate) 1000 else 0,
            easing = FastOutSlowInEasing
        ),
        label = "riskScore"
    )

    Column(modifier = modifier) {
        if (showLabel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.risk_score),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = "$animatedScore",
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                        fontSize = 14.sp
                    )
                    Text(
                        text = stringResource(R.string.risk_score_max),
                        fontWeight = FontWeight.Normal,
                        color = color.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Thin track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Progress fill — solid color, no gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}
