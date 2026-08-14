package com.example.viko.view.screens.team.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
private fun NearbyDeviceRowPreview() {
    VikoTheme {
        NearbyDeviceRow(
            device = NearbyDevice(id = "nd1", name = "VIKO-ESP32-A1B2", address = "AA:BB:CC:11:22:33"),
            isPairing = false,
            onPair = {}
        )
    }
}

@Composable
fun NearbyDeviceRow(
    device: NearbyDevice,
    isPairing: Boolean,
    onPair: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VSurfaceVariant)
            .clickable(enabled = !isPairing, onClick = onPair)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = device.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = VOnSurface,
            modifier = Modifier.weight(1f)
        )
        if (isPairing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = VMicBlue
            )
        } else {
            Text("Pair Device", color = VOnSurfaceSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.Link, null, tint = VMicBlue, modifier = Modifier.size(14.dp))
        }
    }
}
