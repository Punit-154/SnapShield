package com.smssentry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smssentry.ui.theme.*

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
        else -> Color.LightGray.copy(alpha = 0.3f)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Small colored dot instead of emoji
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label.uppercase(),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp
        )
        // Score in a subtle chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = "$riskScore",
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
