package com.example.viko.ui.screens.team

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.data.*
import com.example.viko.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PairingState { SCANNING, FOUND, PAIRING, SUCCESS, NO_DEVICES }

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

    // Spinner rotation for scanning indicator
    val rotation by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "rot"
    )

    // Bottom sheet layout: dark scrim on top, white sheet below
    Box(modifier = Modifier.fillMaxSize()) {

        // Scrim — dark area at top (status bar region)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.12f)
                .background(Color(0xFF1A1A1A))
        )

        // White bottom sheet panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(VBackground)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VBorder)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Header row: title + spinner + X
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title + inline spinner
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Nearby Device",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = VOnSurface
                    )
                    if (pairingState == PairingState.SCANNING || pairingState == PairingState.FOUND) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scanning",
                            tint = VOnSurfaceSecondary,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(rotation)
                        )
                    }
                }

                // X button
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = VOnSurface)
                }
            }

            // Subtitle
            Text(
                text = "Pair your device to add a new member.",
                style = MaterialTheme.typography.bodyLarge,
                color = VOnSurfaceSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Content
            when {
                pairingState == PairingState.SUCCESS -> {
                    SuccessState(
                        onContinue = { onDevicePaired(selectedDevice?.id ?: "") }
                    )
                }

                scannedDevices.isEmpty() -> {
                    // Still scanning — show skeleton placeholders
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(VSurface)
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(scannedDevices) { device ->
                            val isPairing =
                                pairingDevice?.id == device.id && pairingState == PairingState.PAIRING
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
    }
}

@Composable
private fun DeviceListItem(
    device: NearbyDevice,
    isPairing: Boolean,
    onPair: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VSurface)
            .clickable(enabled = !isPairing, onClick = onPair)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Device name — bold, left
        Text(
            text = device.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VOnSurface,
            modifier = Modifier.weight(1f)
        )

        // Right side: "Pair Device" label + link icon, or spinner
        if (isPairing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = VAccentBlue
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Pair Device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VAccentBlue
                )
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = VAccentBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SuccessState(onContinue: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(VOnline.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
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
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VAccentBlue)
            ) {
                Text(
                    "Setup Member Profile",
                    style = MaterialTheme.typography.titleMedium,
                    color = VOnPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}