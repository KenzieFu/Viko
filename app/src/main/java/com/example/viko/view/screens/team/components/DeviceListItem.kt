package com.example.viko.view.screens.team.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun DeviceListItemPreview() {
    VikoTheme {
        DeviceListItem(
            device = NearbyDevice(id = "nd1", name = "VIKO-ESP32-A1B2", address = "AA:BB:CC:11:22:33"),
            isPairing = false,
            onPair = {}
        )
    }
}

@Composable
fun DeviceListItem(
    device: NearbyDevice,
    isPairing: Boolean,
    onPair: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VSurface)
            .border(1.dp, VBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(VBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Bluetooth, null, tint = VOnSurface, modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium, color = VOnSurface)
            Text(
                text = "${device.address} • ${device.signalStrength} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = VOnSurfaceSecondary
            )
        }

        if (isPairing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = VOnSurface
            )
        } else {
            TextButton(
                onClick = onPair,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Link, null, modifier = Modifier.size(14.dp), tint = VOnSurface)
                Spacer(Modifier.width(4.dp))
                Text("Pair", color = VOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
