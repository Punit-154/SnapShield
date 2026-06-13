package com.snapshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapshield.ui.theme.*

@Composable
fun PrivacyIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SafeGreenBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "\uD83D\uDEE1\uFE0F", fontSize = 20.sp)
        Column {
            Text(
                text = "Running On Device",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = SafeGreen
            )
            Text(
                text = "No SMS content leaves your phone",
                fontSize = 12.sp,
                color = SafeGreen.copy(alpha = 0.8f)
            )
        }
    }
}
