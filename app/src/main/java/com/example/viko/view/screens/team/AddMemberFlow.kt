package com.example.viko.view.screens.team

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.components.AddPillButton
import com.example.viko.components.DeviceIllustration
import com.example.viko.components.DeviceInfoCard
import com.example.viko.components.LabeledSelectField
import com.example.viko.components.LabeledTextField
import com.example.viko.components.VikoBlueButton
import com.example.viko.components.VikoDragHandle
import com.example.viko.data.team.ble.BlePermissions
import com.example.viko.di.AppViewModelProvider
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.view.screens.channel.AddNewChannelSheetContent
import com.example.viko.view.screens.channel.SelectChannelSheetContent
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VWarning
import com.example.viko.view.theme.VikoTheme

// The whole "add a member" journey from Figma: pair a device, name the person,
// pick their channel, save. It is ONE composable over ONE ViewModel, and each
// Figma frame is a bottom sheet over the screen behind it.
//
// Why not separate nav screens (how this used to work): the channel picker was
// a different route, so it could not write back into the half-filled setup
// form. It called updateWorkerChannel() with the *device* id for a worker that
// did not exist yet, silently did nothing, and the chosen channel was thrown
// away. Keeping the state in one place removes that class of bug entirely.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberFlow(
    onDismiss: () -> Unit,
    /**
     * Set when the flow is opened from a channel's team sheet: every member
     * paired in this run joins that channel, and the Channel row is locked.
     */
    presetChannelId: String? = null,
    viewModel: AddMemberViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Keyed on the selection too: save() clears it for the next badge, and the
    // next badge in this run belongs to the same channel.
    LaunchedEffect(presetChannelId, uiState.selectedChannelId) {
        if (presetChannelId != null && uiState.selectedChannelId != presetChannelId) {
            viewModel.selectChannel(presetChannelId)
        }
    }

    // Sub-sheets stacked on top of the flow sheet. rememberSaveable, not
    // remember: an Activity recreation (rotation, IME insets, a system overlay)
    // would otherwise drop the supervisor back to the sheet underneath.
    var showChannelPicker by rememberSaveable { mutableStateOf(false) }
    var addChannelPrefill by rememberSaveable { mutableStateOf<String?>(null) }

    // BLE scanning is a runtime permission, and denying it is silent: the scan
    // starts fine and simply never reports a device. So the grant is tracked
    // explicitly and the sheet says what is missing instead of showing an empty
    // list forever.
    var hasBlePermission by remember { mutableStateOf(BlePermissions.allGranted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasBlePermission = grants.values.all { it }
        if (hasBlePermission) viewModel.rescan()
    }

    LaunchedEffect(Unit) {
        if (hasBlePermission) viewModel.rescan() else permissionLauncher.launch(BlePermissions.required)
    }

    // The form itself lives in the ViewModel, which survives recreation; clearing
    // it is the last thing that happens on the way out.
    val dismiss = {
        viewModel.restart()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = VBackground,
        contentColor = VOnSurface,
        dragHandle = { VikoDragHandle() }
    ) {
        when (uiState.step) {
            AddMemberStep.Pairing -> NearbyDeviceSheetContent(
                devices = uiState.devices,
                isScanning = uiState.isScanning,
                hasPermission = hasBlePermission,
                isBluetoothEnabled = uiState.isBluetoothEnabled,
                isEnrolled = uiState::isEnrolled,
                addedCount = uiState.addedCount,
                onSelect = viewModel::select,
                onRescan = {
                    if (hasBlePermission) viewModel.rescan()
                    else permissionLauncher.launch(BlePermissions.required)
                },
                onDone = dismiss,
                onClose = dismiss
            )

            AddMemberStep.Setup -> SetupMemberSheetContent(
                device = uiState.pairedDevice,
                name = uiState.name,
                onNameChange = viewModel::onNameChange,
                zone = uiState.zone,
                onZoneChange = viewModel::onZoneChange,
                wifiSsid = uiState.wifiSsid,
                onSsidChange = viewModel::onSsidChange,
                wifiPassword = uiState.wifiPassword,
                onPasswordChange = viewModel::onPasswordChange,
                channelName = uiState.selectedChannelName,
                isChannelLocked = presetChannelId != null,
                canSave = uiState.canSave,
                isProvisioning = uiState.isProvisioning,
                error = uiState.error,
                onPickChannel = { showChannelPicker = true },
                // Saving returns to the device list rather than closing — see
                // AddMemberViewModel.save.
                onSave = viewModel::save,
                onClose = dismiss
            )
        }
    }

    if (showChannelPicker) {
        ModalBottomSheet(
            onDismissRequest = { showChannelPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground,
            contentColor = VOnSurface,
            dragHandle = { VikoDragHandle() }
        ) {
            SelectChannelSheetContent(
                channels = uiState.channels,
                selectedChannelId = uiState.selectedChannelId,
                onSelect = { channelId ->
                    // Writes straight into this flow's state — this is the line
                    // the old nav-based version could not reach.
                    viewModel.selectChannel(channelId)
                    showChannelPicker = false
                },
                onRequestAddChannel = { prefill -> addChannelPrefill = prefill },
                onBack = { showChannelPicker = false }
            )
        }
    }

    addChannelPrefill?.let { prefill ->
        ModalBottomSheet(
            onDismissRequest = { addChannelPrefill = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground,
            contentColor = VOnSurface,
            dragHandle = { VikoDragHandle() }
        ) {
            AddNewChannelSheetContent(
                prefill = prefill,
                onCreate = { name ->
                    viewModel.createChannel(name)
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
    hasPermission: Boolean,
    isBluetoothEnabled: Boolean,
    isEnrolled: (NearbyDevice) -> Boolean,
    addedCount: Int,
    onSelect: (NearbyDevice) -> Unit,
    onRescan: () -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nearby Device",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = VOnSurface
            )
            Spacer(modifier = Modifier.width(14.dp))
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = VOnSurfaceSecondary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        Text(
            text = "Pair your device to add a new member.",
            style = MaterialTheme.typography.bodyLarge,
            color = VOnSurfaceSecondary,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (devices.isEmpty()) {
            Text(
                // Each of these is a different fix, and an undifferentiated
                // "No devices found" sends the supervisor hunting the wrong one.
                text = when {
                    !hasPermission -> "Bluetooth permission is needed to find devices."
                    !isBluetoothEnabled -> "Turn Bluetooth on to find devices."
                    else -> "No devices found"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = VOnSurfaceSecondary.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 4.dp)
            )
            // A badge only advertises while it is unprovisioned, and one with bad
            // Wi-Fi credentials only falls back to that ~20s after power-on — so
            // the empty list is usually "not yet", and this says how to fix it.
            if (hasPermission && isBluetoothEnabled && !isScanning) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hold the earpiece button for 5 seconds to put it in pairing mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VOnSurfaceSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                devices.forEach { device ->
                    NearbyDeviceRow(
                        device = device,
                        isEnrolled = isEnrolled(device),
                        onSelect = { onSelect(device) }
                    )
                }
            }
        }

        // The scan window closes on its own to spare the radio, so getting
        // another one has to be reachable — this is the button a supervisor
        // presses after holding the badge button.
        if (!isScanning) {
            Spacer(modifier = Modifier.height(16.dp))
            if (hasPermission) {
                AddPillButton(text = "Scan again", onClick = onRescan)
            } else {
                VikoBlueButton(text = "Allow Bluetooth", onClick = onRescan)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Onboarding pairs a whole shift in one sitting, so the flow stays open
        // after each save. Done is how the supervisor says they are finished.
        if (addedCount > 0) {
            Text(
                text = "$addedCount member${if (addedCount == 1) "" else "s"} added",
                style = MaterialTheme.typography.bodySmall,
                color = VOnSurfaceSecondary,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            VikoBlueButton(text = "Done", onClick = onDone)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun NearbyDeviceRow(
    device: NearbyDevice,
    isEnrolled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VSurface)
            // A badge already on the roster cannot be set up again: the flow
            // stays open after each save, so the one just enrolled is still
            // sitting in this list, and a second pass would create a duplicate
            // member on the same hardware.
            .clickable(enabled = !isEnrolled, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = device.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isEnrolled) VOnSurfaceSecondary else VOnSurface,
            modifier = Modifier.weight(1f)
        )
        if (isEnrolled) {
            Text("Paired", color = VOnSurfaceSecondary, fontSize = 12.sp)
        } else {
            Text("Pair Device", color = VOnSurface, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.Link, null, tint = VMicBlue, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SetupMemberSheetContent(
    device: NearbyDevice?,
    name: String,
    onNameChange: (String) -> Unit,
    zone: String,
    onZoneChange: (String) -> Unit,
    wifiSsid: String,
    onSsidChange: (String) -> Unit,
    wifiPassword: String,
    onPasswordChange: (String) -> Unit,
    channelName: String?,
    isChannelLocked: Boolean,
    canSave: Boolean,
    isProvisioning: Boolean,
    error: String?,
    onPickChannel: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        // The form scrolls; Save stays pinned at the bottom where Figma has it,
        // however many fields the badge needs above it.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            DeviceIllustration(modifier = Modifier.align(Alignment.CenterHorizontally))

            Spacer(modifier = Modifier.height(20.dp))

            LabeledTextField(
                label = "Name",
                value = name,
                onValueChange = onNameChange,
                placeholder = "Enter Member Name"
            )

            Spacer(modifier = Modifier.height(16.dp))

            LabeledSelectField(
                label = "Channel",
                value = channelName,
                placeholder = "Select Channel",
                // Opened from a channel's own team sheet: the channel is already
                // decided, so the row is shown locked rather than as a picker.
                onClick = if (isChannelLocked) null else onPickChannel
            )

            Spacer(modifier = Modifier.height(16.dp))

            // The zone goes onto the badge, not just the roster: it is what an
            // emergency_alert reports, and it is how a supervisor knows where to
            // run before anything has been translated.
            LabeledTextField(
                label = "Location",
                value = zone,
                onValueChange = onZoneChange,
                placeholder = "e.g. Central Building"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // The badge has no keyboard and no screen: this is the only moment the
            // factory Wi-Fi can reach it. It is sent over BLE, saved to the badge's
            // flash, and used for every connection it ever makes.
            LabeledTextField(
                label = "Factory Wi-Fi",
                value = wifiSsid,
                onValueChange = onSsidChange,
                placeholder = "Network name"
            )

            Spacer(modifier = Modifier.height(16.dp))

            LabeledTextField(
                label = "Wi-Fi Password",
                value = wifiPassword,
                onValueChange = onPasswordChange,
                placeholder = "Password",
                isPassword = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            DeviceInfoCard(
                address = device?.address ?: "Unknown",
                status = {
                    Text(
                        // Battery and location only arrive once the badge is on Wi-Fi
                        // and registers; before that, signal is all BLE can tell us.
                        text = device?.let { "Pairing • ${it.signalStrength} dBm" } ?: "Unknown",
                        fontSize = 12.sp,
                        color = VOnSurfaceSecondary
                    )
                }
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = VWarning
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)) {
            // Figma dims Save until the form is complete — see AddMemberViewModel.save
            // for why that matters. Saving here also writes the badge over BLE,
            // which takes a few seconds and can fail.
            VikoBlueButton(
                text = if (isProvisioning) "Saving…" else "Save",
                enabled = canSave,
                onClick = onSave
            )

            if (isProvisioning) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = VMicBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Keep the earpiece nearby — it restarts onto Wi-Fi when this finishes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VOnSurfaceSecondary
                    )
                }
            }
        }
    }
}

private fun previewDevice(id: String, name: String, rssi: Int = -45) =
    NearbyDevice(id = id, name = name, address = "AA:BB:CC:11:22:$id", signalStrength = rssi)

/**
 * The two steps are previewed as their sheet *contents*, not through
 * [AddMemberFlow] itself — that wraps everything in a ModalBottomSheet, which
 * has no host to render into inside a preview.
 */
@Preview(showBackground = true, backgroundColor = 0xFF1C1D20, heightDp = 720)
@Composable
private fun NearbyDeviceSheetContentPreview() {
    VikoTheme {
        NearbyDeviceSheetContent(
            devices = listOf(
                previewDevice("33", "ViKo-A1B2"),
                previewDevice("66", "ViKo-C3D4", rssi = -67),
                previewDevice("99", "ViKo-E5F6", rssi = -80)
            ),
            isScanning = true,
            hasPermission = true,
            isBluetoothEnabled = true,
            isEnrolled = { it.id == "99" },
            addedCount = 1,
            onSelect = {}, onRescan = {}, onDone = {}, onClose = {}
        )
    }
}

/** Scanning with nothing found yet. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1D20, heightDp = 720)
@Composable
private fun NearbyDeviceSheetContentScanningPreview() {
    VikoTheme {
        NearbyDeviceSheetContent(
            devices = emptyList(),
            isScanning = true,
            hasPermission = true,
            isBluetoothEnabled = true,
            isEnrolled = { false },
            addedCount = 0,
            onSelect = {}, onRescan = {}, onDone = {}, onClose = {}
        )
    }
}

/** Setup filled in and ready to save. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1D20, heightDp = 760)
@Composable
private fun SetupMemberSheetContentPreview() {
    VikoTheme {
        SetupMemberSheetContent(
            device = previewDevice("33", "ViKo-A1B2"),
            name = "Budi",
            onNameChange = {},
            zone = "Production Line 2",
            onZoneChange = {},
            wifiSsid = "FactoryTranslate_AP",
            onSsidChange = {},
            wifiPassword = "hunter2hunter2",
            onPasswordChange = {},
            channelName = "Warehouse",
            isChannelLocked = false,
            canSave = true,
            isProvisioning = false,
            error = null,
            onPickChannel = {}, onSave = {}, onClose = {}
        )
    }
}

/** Setup still empty — Save dim, which is the state that is easy to get wrong. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1D20, heightDp = 760)
@Composable
private fun SetupMemberSheetContentEmptyPreview() {
    VikoTheme {
        SetupMemberSheetContent(
            device = previewDevice("33", "ViKo-A1B2"),
            name = "",
            onNameChange = {},
            zone = "",
            onZoneChange = {},
            wifiSsid = "",
            onSsidChange = {},
            wifiPassword = "",
            onPasswordChange = {},
            channelName = null,
            isChannelLocked = false,
            canSave = false,
            isProvisioning = false,
            error = "Timed out. Is the badge still in pairing mode?",
            onPickChannel = {}, onSave = {}, onClose = {}
        )
    }
}
