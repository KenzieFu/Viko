package com.example.viko.ui.screens.team

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.data.*
import com.example.viko.ui.components.VikoBlueButton
import com.example.viko.ui.components.VikoTextField
import com.example.viko.ui.screens.channel.AddNewChannelSheetContent
import com.example.viko.ui.screens.channel.SelectChannelSheetContent
import com.example.viko.ui.theme.*
import kotlinx.coroutines.delay

// The whole "add a member" journey from Figma: pair a device, name the person,
// pick their channel, save. It is ONE composable holding ONE set of state, and
// each Figma frame is a bottom sheet over the screen behind it.
//
// Why not separate nav screens (how this used to work): the channel picker was
// a different route, so it could not write back into the half-filled setup
// form. It called updateWorkerChannel() with the *device* id for a worker that
// did not exist yet, silently did nothing, and the chosen channel was thrown
// away. Keeping the state in one place removes that class of bug entirely.

private enum class AddMemberStep { Pairing, Setup }

// Flip to true to preview Figma's "No devices found" frame without hardware.
private const val SIMULATE_NO_DEVICES = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberFlow(
    appState: AppState,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(AddMemberStep.Pairing) }
    var pairedDevice by remember { mutableStateOf<NearbyDevice?>(null) }
    var pairingDeviceId by remember { mutableStateOf<String?>(null) }
    var memberName by remember { mutableStateOf("") }
    var selectedChannelId by remember { mutableStateOf("") }

    // Sub-sheets stacked on top of the flow sheet
    var showChannelPicker by remember { mutableStateOf(false) }
    var addChannelPrefill by remember { mutableStateOf<String?>(null) }

    // Fake BLE scan
    var isScanning by remember { mutableStateOf(true) }
    var devices by remember { mutableStateOf<List<NearbyDevice>>(emptyList()) }
    LaunchedEffect(Unit) {
        delay(1200)
        devices = if (SIMULATE_NO_DEVICES) emptyList() else mockNearbyDevices
        isScanning = false
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VSurface,
        contentColor = VOnSurface,
        dragHandle = { com.example.viko.ui.components.VikoDragHandle() }
    ) {
        when (step) {
            AddMemberStep.Pairing -> NearbyDeviceSheetContent(
                devices = devices,
                isScanning = isScanning,
                pairingDeviceId = pairingDeviceId,
                onPair = { device ->
                    pairingDeviceId = device.id
                    pairedDevice = device
                },
                onPaired = {
                    pairingDeviceId = null
                    step = AddMemberStep.Setup
                },
                onClose = onDismiss
            )

            AddMemberStep.Setup -> SetupMemberSheetContent(
                device = pairedDevice,
                name = memberName,
                onNameChange = { memberName = it },
                channelName = appState.channels.find { it.id == selectedChannelId }?.name,
                onPickChannel = { showChannelPicker = true },
                onSave = {
                    appState.addWorker(
                        Worker(
                            id = "w_${appState.workers.size + 1}_${memberName.hashCode()}",
                            name = memberName.trim(),
                            channelId = selectedChannelId,
                            zone = pairedDevice?.location ?: "Zone 1",
                            deviceAddress = pairedDevice?.address ?: "Unknown",
                            macAddress = pairedDevice?.address ?: "00:00:00:00:00:00",
                            batteryLevel = pairedDevice?.batteryLevel ?: 100,
                            isOnline = true
                        )
                    )
                    onDismiss()
                },
                onClose = onDismiss
            )
        }
    }

    if (showChannelPicker) {
        ModalBottomSheet(
            onDismissRequest = { showChannelPicker = false },
            containerColor = VSurface,
            contentColor = VOnSurface,
            dragHandle = { com.example.viko.ui.components.VikoDragHandle() }
        ) {
            SelectChannelSheetContent(
                channels = appState.channels,
                selectedChannelId = selectedChannelId,
                onSelect = { id ->
                    // Writes straight into this flow's state — this is the line
                    // the old nav-based version could not reach.
                    selectedChannelId = id
                    showChannelPicker = false
                },
                onRequestAddChannel = { prefill -> addChannelPrefill = prefill },
                onBack = { showChannelPicker = false },
                memberCountFor = { id -> appState.workersForChannel(id).size }
            )
        }
    }

    addChannelPrefill?.let { prefill ->
        ModalBottomSheet(
            onDismissRequest = { addChannelPrefill = null },
            containerColor = VSurface,
            contentColor = VOnSurface,
            dragHandle = { com.example.viko.ui.components.VikoDragHandle() }
        ) {
            AddNewChannelSheetContent(
                prefill = prefill,
                onCreate = { name ->
                    val channel = Channel(id = "ch_${appState.channels.size + 1}", name = name)
                    appState.channels.add(channel)
                    selectedChannelId = channel.id
                    addChannelPrefill = null
                    showChannelPicker = false
                },
                onClose = { addChannelPrefill = null }
            )
        }
    }
}

@Composable
private fun NearbyDeviceSheetContent(
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

@Composable
private fun NearbyDeviceRow(
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

@Composable
private fun SetupMemberSheetContent(
    device: NearbyDevice?,
    name: String,
    onNameChange: (String) -> Unit,
    channelName: String?,
    onPickChannel: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    // Figma greys the Save button until BOTH a name and a channel are set. The
    // old screen let you save an empty form and quietly named the person
    // "New Worker", which is how blank members got created.
    val canSave = name.isNotBlank() && channelName != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
            .navigationBarsPadding()
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        DeviceIllustration(modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(modifier = Modifier.height(20.dp))

        // Name + Channel group
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("Name", fontSize = 11.sp, color = VOnSurfaceSecondary)
                VikoTextField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = "Enter Member Name",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = VBorder)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickChannel)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Channel", fontSize = 11.sp, color = VOnSurfaceSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = channelName ?: "Select Channel",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (channelName != null) VOnSurface else VOnSurfaceSecondary
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = VOnSurfaceSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Read-only device facts
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VSurfaceVariant)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SetupInfoRow("Device Address", device?.address ?: "Unknown")
            SetupInfoRow(
                "Device Status",
                "${device?.location ?: "Unknown"} • ${device?.batteryLevel ?: 0}%"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        VikoBlueButton(text = "Save", enabled = canSave, onClick = onSave)
    }
}

/**
 * Stand-in for the 3D earpiece render in Figma. Replace with the real asset
 * once design exports it — drop a PNG in res/drawable and swap this out.
 */
@Composable
private fun DeviceIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(112.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(VOnSurface.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(VSurfaceVariant)
                .border(3.dp, VOffline, CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(VWarning)
        )
    }
}
