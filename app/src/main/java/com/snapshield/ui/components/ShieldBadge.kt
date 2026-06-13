package com.snapshield.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapshield.ui.theme.*

@Composable
fun ShieldBadge(
    label: String,
    riskScore: Int,
    modifier: Modifier = Modifier,
    animated: Boolean = false
) {
    val color = when (label.uppercase()) {
        "SAFE" -> SafeGreen
        "SUSPICIOUS" -> SuspiciousOrange
        "SCAM" -> ScamRed
        else -> LowGray
    }

    val backgroundColor = when (label.uppercase()) {
        "SAFE" -> SafeGreenBackground
        "SUSPICIOUS" -> SuspiciousOrangeBackground
        "SCAM" -> ScamRedBackground
        else -> Color.LightGray
    }

    val scale = if (animated && label.uppercase() == "SCAM") {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        scale
    } else {
        1f
    }

    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = when (label.uppercase()) {
                "SAFE" -> "\uD83D\uDEE1\uFE0F"
                "SUSPICIOUS" -> "\u26A0\uFE0F"
                "SCAM" -> "\uD83D\uDED1"
                else -> "?"
            },
            fontSize = 14.sp
        )
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Text(
            text = "$riskScore/100",
            color = color.copy(alpha = 0.8f),
            fontSize = 11.sp
        )
    }
}
