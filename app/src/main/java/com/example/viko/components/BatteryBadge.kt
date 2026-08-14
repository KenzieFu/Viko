package com.example.viko.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.VLowBattery
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VWarning
import com.example.viko.view.theme.VikoTheme

@Composable
fun BatteryBadge(level: Int) {
    val color = when {
        level <= 20 -> VLowBattery
        level <= 40 -> VWarning
        else -> VOnSurface
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(
            imageVector = Icons.Default.BatteryFull,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "$level%",
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview
@Composable
private fun BatteryBadgePreview() {
    VikoTheme {
        BatteryBadge(level = 15)
    }
}
