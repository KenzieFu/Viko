package com.example.viko.view.screens.team.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.mock.mockNearbyDevices
import com.example.viko.view.theme.*
import kotlinx.coroutines.delay

@Preview(showBackground = true)
@Composable
private fun NearbyDeviceSheetContentPreview() {
    VikoTheme {
        NearbyDeviceSheetContent(
            devices = mockNearbyDevices,
            isScanning = false,
            pairingDeviceId = null,
            onPair = {},
            onPaired = {},
            onClose = {}
        )
    }
}

@Composable
fun NearbyDeviceSheetContent(
    devices: List<NearbyDevice>,
    isScanning: Boolean,
    pairingDeviceId: String?,
    onPair: (NearbyDevice) -> Unit,
    onPaired: () -> Unit,
    onClose: () -> Unit
) {
    // Pairing takes a moment, then the flow advances on its own — Figma has no
    // "Device Paired!" confirmation step between here and Setup Member.
    LaunchedEffect(pairingDeviceId) {
        if (pairingDeviceId != null) {
            delay(1400)
            onPaired()
        }
    }

    val rotation by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "rot"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 440.dp)
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Nearby Device",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VOnSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Bluetooth,
                null,
                tint = VOnSurfaceSecondary,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(if (isScanning) rotation else 0f)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        Text(
            text = "Pair your device to add a new member.",
            style = MaterialTheme.typography.bodySmall,
            color = VOnSurfaceSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (devices.isEmpty()) {
            Text(
                text = if (isScanning) "Scanning..." else "No devices found",
                style = MaterialTheme.typography.bodyMedium,
                color = VOnSurfaceSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                devices.forEach { device ->
                    NearbyDeviceRow(
                        device = device,
                        isPairing = pairingDeviceId == device.id,
                        onPair = { onPair(device) }
                    )
                }
            }
        }
    }
}
