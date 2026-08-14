package com.example.viko.view.screens.team

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.mock.AppState
import com.example.viko.mock.mockNearbyDevices
import com.example.viko.view.screens.team.components.DeviceListItem
import com.example.viko.view.screens.team.components.PairingState
import com.example.viko.view.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Preview(showBackground = true)
@Composable
private fun MemberPairingScreenPreview() {
    val appState = AppState()
    VikoTheme {
        MemberPairingScreen(
            channelId = appState.channels.first().id,
            appState = appState,
            onBack = {},
            onDevicePaired = {}
        )
    }
}

@Composable
fun MemberPairingScreen(
    channelId: String,
    appState: AppState,
    onBack: () -> Unit,
    onDevicePaired: (String) -> Unit
) {
    var pairingState by remember { mutableStateOf(PairingState.SCANNING) }
    var scannedDevices by remember { mutableStateOf<List<NearbyDevice>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<NearbyDevice?>(null) }
    var pairingDevice by remember { mutableStateOf<NearbyDevice?>(null) }
    val scope = rememberCoroutineScope()

    // Simulate BLE scan
    LaunchedEffect(Unit) {
        delay(800)
        scannedDevices = listOf(mockNearbyDevices[0])
        pairingState = PairingState.FOUND
        delay(600)
        scannedDevices = mockNearbyDevices.take(2)
        delay(600)
        scannedDevices = mockNearbyDevices.take(3)
        delay(400)
        scannedDevices = mockNearbyDevices
    }

    // BLE spin animation
    val rotation by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "rot"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("SETUP DEVICE", style = MaterialTheme.typography.titleSmall, color = VOnSurfaceSecondary)
                Text("Nearby Device", style = MaterialTheme.typography.headlineSmall)
            }
        }

        HorizontalDivider(color = VBorder)
        Spacer(modifier = Modifier.height(16.dp))

        // BLE scan status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(VOnSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Bluetooth,
                    null,
                    tint = VOnSurface,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (pairingState == PairingState.SCANNING) rotation else 0f)
                )
            }
            Column {
                Text(
                    text = when (pairingState) {
                        PairingState.SCANNING -> "Scanning for devices..."
                        PairingState.FOUND -> "${scannedDevices.size} device(s) found"
                        PairingState.PAIRING -> "Pairing with ${pairingDevice?.name}..."
                        PairingState.SUCCESS -> "Paired successfully!"
                        PairingState.NO_DEVICES -> "No devices found"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = VOnSurface
                )
                Text(
                    text = "Pair your device to add a new member.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VOnSurfaceSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (pairingState == PairingState.SUCCESS) {
            // Success state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(VOnline.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = VOnline,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Text(
                        "Device Paired!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = VOnSurface
                    )
                    Text(
                        "The device has been configured and is ready to use.",
                        color = VOnSurfaceSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { onDevicePaired(selectedDevice?.id ?: "") },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VOnSurface)
                    ) {
                        Text("Setup Member Profile")
                    }
                }
            }
        } else if (scannedDevices.isEmpty() && pairingState != PairingState.SCANNING) {
            // No devices
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.BluetoothDisabled,
                        null,
                        tint = VOnSurfaceSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text("No devices found", color = VOnSurfaceSecondary)
                    Text(
                        "Make sure the VIKO wearable is powered on and nearby",
                        color = VOnSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scannedDevices) { device ->
                    val isPairing = pairingDevice?.id == device.id && pairingState == PairingState.PAIRING
                    DeviceListItem(
                        device = device,
                        isPairing = isPairing,
                        onPair = {
                            pairingDevice = device
                            selectedDevice = device
                            pairingState = PairingState.PAIRING
                            scope.launch {
                                delay(2000)
                                pairingState = PairingState.SUCCESS
                            }
                        }
                    )
                }
            }
        }
    }
}
