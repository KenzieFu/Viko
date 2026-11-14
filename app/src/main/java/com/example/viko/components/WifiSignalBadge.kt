package com.example.viko.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.VOnSurfaceSecondary

@Composable
fun WifiSignalBadge(rssiDbm: Int?, showDbm: Boolean = false) {
    val bars = when {
        rssiDbm == null -> 0
        rssiDbm >= -60 -> 4
        rssiDbm >= -67 -> 3
        rssiDbm >= -75 -> 2
        else -> 1
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(14.dp, 12.dp).semantics {
            contentDescription = if (rssiDbm == null) "Wi-Fi signal unavailable" else "Wi-Fi signal, $bars of 4 bars"
        }) {
            repeat(4) { bar ->
                val height = size.height * (bar + 1) / 4
                drawRect(
                    color = VOnSurfaceSecondary.copy(alpha = if (bar < bars) 1f else .3f),
                    topLeft = Offset(bar * size.width / 4, size.height - height),
                    size = Size(size.width / 6, height)
                )
            }
        }
        if (showDbm) {
            Text(rssiDbm?.let { "$it dBm" } ?: "Unavailable", fontSize = 12.sp, color = VOnSurfaceSecondary)
        }
    }
}
