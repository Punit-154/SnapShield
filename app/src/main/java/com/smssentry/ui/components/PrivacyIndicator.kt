package com.smssentry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smssentry.ui.theme.*

@Composable
fun PrivacyIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SafeGreenBackground)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Privacy protected",
            tint = SafeGreen,
            modifier = Modifier.size(22.dp)
        )
        Column {
            Text(
                text = "On-Device Analysis",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = SafeGreenDark
            )
            Text(
                text = "Your messages never leave your phone",
                fontSize = 12.sp,
                color = SafeGreen.copy(alpha = 0.8f)
            )
        }
    }
}
