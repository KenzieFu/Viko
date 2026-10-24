package com.example.viko.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.VLowBattery
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VOnline
import com.example.viko.view.theme.VikoTheme

/**
 * `🔋 70%` as Figma draws it inline in a subtitle: the glyph carries the state
 * (green healthy, red low) and the number stays the colour of the text around it.
 */
@Composable
fun BatteryBadge(level: Int, textColor: Color = VOnSurfaceSecondary) {
    val isLow = level <= LOW_BATTERY
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        Icon(
            imageVector = if (isLow) Icons.Default.Battery2Bar else Icons.Default.BatteryFull,
            contentDescription = null,
            tint = if (isLow) VLowBattery else VOnline,
            modifier = Modifier.size(13.dp)
        )
        Text(text = "$level%", fontSize = 12.sp, color = textColor)
    }
}

/** At or below this a badge shows red — about an hour of talk time left. */
private const val LOW_BATTERY = 20

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun BatteryBadgePreview() {
    VikoTheme {
        BatteryBadge(level = 15)
    }
}
