package com.example.viko.view.screens.team

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.viko.components.VikoDragHandle
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.domain.team.model.Worker
import com.example.viko.mock.AppState
import com.example.viko.mock.mockNearbyDevices
import com.example.viko.view.screens.channel.components.AddNewChannelSheetContent
import com.example.viko.view.screens.channel.components.SelectChannelSheetContent
import com.example.viko.view.screens.team.components.NearbyDeviceSheetContent
import com.example.viko.view.screens.team.components.SetupMemberSheetContent
import com.example.viko.view.theme.*
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

@Preview(showBackground = true)
@Composable
private fun AddMemberFlowPreview() {
    VikoTheme {
        AddMemberFlow(appState = AppState(), onDismiss = {})
    }
}

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
        dragHandle = { VikoDragHandle() }
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
            dragHandle = { VikoDragHandle() }
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
            dragHandle = { VikoDragHandle() }
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
