package com.snapshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapshield.ui.theme.*

@Composable
fun DemoModeBanner(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isEnabled) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SuspiciousOrangeBackground)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "\uD83C\uDFAE", fontSize = 20.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Demo Mode Active",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = SuspiciousOrange
                )
                Text(
                    text = "Showing pre-loaded scam scenarios",
                    fontSize = 12.sp,
                    color = SuspiciousOrange.copy(alpha = 0.8f)
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SuspiciousOrange
                )
            )
        }
    }
}
