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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.viko.data.*
import com.example.viko.network.BlePermissions
import com.example.viko.network.VikoBle
import com.example.viko.ui.theme.*

@Composable
fun MemberPairingScreen(
    channelId: String,
    appState: AppState,
    onBack: () -> Unit,
    onDevicePaired: (String) -> Unit
) {
    val context = LocalContext.current
    val ble = remember { VikoBle.get(context) }

    val scannedDevices by ble.devices.collectAsState()
    val isScanning by ble.isScanning.collectAsState()

    var hasPermission by remember { mutableStateOf(BlePermissions.allGranted(context)) }
    var bluetoothOff by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasPermission = grants.values.all { it }
    }

    // Request permissions on entry, then scan. Scanning is stopped when the
    // screen leaves composition — an unstopped BLE scan keeps the radio hot and
    // Android will eventually throttle the app's scans app-wide.
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(BlePermissions.required)
    }

    DisposableEffect(hasPermission) {
        if (hasPermission) {
            if (ble.isBluetoothEnabled) {
                bluetoothOff = false
                ble.startScan()
            } else {
                bluetoothOff = true
            }
        }
        onDispose { ble.stopScan() }
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
                    if (isScanning) {
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

                // Manual rescan — badges only advertise while unprovisioned, so a
                // supervisor who powers one on late needs a way to look again.
                IconButton(onClick = { if (hasPermission) ble.startScan() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = VOnSurface)
                }

                // X button
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = VOnSurface)
                }
            }

            // Subtitle
            Text(
                text = "Hold the badge button for 5 seconds until the LED blinks blue, then select it below.",
                style = MaterialTheme.typography.bodyLarge,
                color = VOnSurfaceSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Content
            when {
                !hasPermission -> InfoState(
                    title = "Bluetooth permission needed",
                    body = "ViKo needs Bluetooth access to find nearby badges.",
                    actionLabel = "Grant permission",
                    onAction = { permissionLauncher.launch(BlePermissions.required) }
                )

                bluetoothOff -> InfoState(
                    title = "Bluetooth is off",
                    body = "Turn Bluetooth on, then scan again.",
                    actionLabel = "Scan again",
                    onAction = {
                        if (ble.isBluetoothEnabled) {
                            bluetoothOff = false
                            ble.startScan()
                        }
                    }
                )

                scannedDevices.isEmpty() && isScanning -> {
                    // Scanning with nothing found yet — skeleton placeholders
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

                scannedDevices.isEmpty() -> InfoState(
                    title = "No badges found",
                    body = "Make sure the badge is in pairing mode — hold its button for " +
                        "5 seconds until the LED blinks blue. A badge that is already " +
                        "provisioned will not appear here.",
                    actionLabel = "Scan again",
                    onAction = { ble.startScan() }
                )

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(scannedDevices, key = { it.address }) { device ->
                            DeviceListItem(
                                device = device,
                                // Selecting a badge just carries its address forward;
                                // the GATT write happens on Save in the setup screen,
                                // once we actually have Wi-Fi creds and a worker name.
                                onPair = {
                                    ble.stopScan()
                                    onDevicePaired(device.address)
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
private fun InfoState(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VOnSurface
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = VOnSurfaceSecondary
        )
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VAccentBlue)
        ) {
            Text(actionLabel, color = VOnPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DeviceListItem(
    device: NearbyDevice,
    onPair: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VSurface)
            .clickable(onClick = onPair)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VOnSurface
            )
            // Real MAC + live RSSI. Signal strength is genuinely useful here: with
            // several badges in pairing mode at once, the nearest one is the one
            // the supervisor is holding.
            Text(
                text = "${device.address}  ·  ${device.signalStrength} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = VOnSurfaceSecondary
            )
        }

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
