package com.example.viko.view.screens.team

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import com.example.viko.R
import com.example.viko.components.VikoListRow
import com.example.viko.view.theme.VOnboardingBlue
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.TextButton
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
import com.example.viko.view.screens.settings.components.FactoryWifiSheetContent
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VWarning
import com.example.viko.view.theme.VikoTheme

// The whole "add a member" journey from Figma: pair a device, name the person,
// pick their channel, save. One ViewModel retains the form across the discovery
// sheet, full-screen setup and nested channel/Wi-Fi pickers.
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
    closeAfterSave: Boolean = false,
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
    var showWifiSetup by rememberSaveable { mutableStateOf(false) }

    var hasBlePermission by remember { mutableStateOf(BlePermissions.allGranted(context)) }
    var bluetoothEnabled by remember { mutableStateOf(BlePermissions.isEnabled(context)) }
    var locationReady by remember { mutableStateOf(BlePermissions.isLocationReady(context)) }
    val bluetoothSupported = remember { BlePermissions.isSupported(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refreshPrerequisites(): Boolean {
        hasBlePermission = BlePermissions.allGranted(context)
        bluetoothEnabled = BlePermissions.isEnabled(context)
        locationReady = BlePermissions.isLocationReady(context)
        val ready = bluetoothSupported && hasBlePermission && bluetoothEnabled && locationReady
        if (!ready) viewModel.stopScan()
        return ready
    }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (refreshPrerequisites()) viewModel.rescan()
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (refreshPrerequisites()) viewModel.rescan()
    }
    fun enableOrScan() {
        refreshPrerequisites()
        when {
            !bluetoothSupported || !hasBlePermission -> Unit
            !bluetoothEnabled -> {
                // Permission and radio power are different Android operations.
                // Prefer the OS enable prompt; provide settings if unavailable.
                runCatching { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                    .onFailure { settingsLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            }
            !locationReady -> settingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else -> viewModel.rescan()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (BlePermissions.allGranted(context)) enableOrScan() else refreshPrerequisites()
    }

    // Returning from Settings or changing the radio in Quick Settings must not
    // leave the sheet showing an old permission/radio state.
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPrerequisites()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { refreshPrerequisites() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
            viewModel.stopScan()
        }
    }
    LaunchedEffect(Unit) {
        if (refreshPrerequisites() && uiState.step == AddMemberStep.Pairing) viewModel.rescan()
    }

    // The form itself lives in the ViewModel, which survives recreation; clearing
    // it is the last thing that happens on the way out.
    val dismiss = {
        viewModel.restart()
        onDismiss()
    }

    LaunchedEffect(uiState.addedCount, closeAfterSave) {
        if (closeAfterSave && uiState.addedCount > 0) dismiss()
    }

    val content: @Composable () -> Unit = {
        when (uiState.step) {
            AddMemberStep.Pairing -> NearbyDeviceSheetContent(
                devices = uiState.devices,
                isScanning = uiState.isScanning,
                hasPermission = hasBlePermission,
                isBluetoothEnabled = bluetoothEnabled,
                isBluetoothSupported = bluetoothSupported,
                isLocationReady = locationReady,
                scanError = uiState.error,
                onBluetoothSettings = { settingsLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                onPermissionSettings = {
                    settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")))
                },
                isEnrolled = uiState::isEnrolled,
                addedCount = uiState.addedCount,
                onSelect = viewModel::select,
                onRescan = {
                    if (BlePermissions.allGranted(context)) enableOrScan()
                    else permissionLauncher.launch(BlePermissions.required)
                },
                onDone = dismiss,
                onClose = dismiss
            )

            AddMemberStep.Setup -> SetupMemberScreenContent(
                device = uiState.pairedDevice,
                name = uiState.name,
                onNameChange = viewModel::onNameChange,
                zone = uiState.zone,
                onZoneChange = viewModel::onZoneChange,
                isWifiConfigured = uiState.factoryWifi.isConfigured,
                onSetUpWifi = { showWifiSetup = true },
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

    if (uiState.step == AddMemberStep.Pairing) {
        ModalBottomSheet(onDismissRequest = dismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground, contentColor = VOnSurface,
            scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = .5f),
            dragHandle = { VikoDragHandle() }) { content() }
    } else {
        Dialog(onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Column(Modifier.fillMaxSize().background(VBackground).statusBarsPadding().imePadding()) { content() }
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

    if (showWifiSetup) {
        ModalBottomSheet(
            onDismissRequest = { showWifiSetup = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground,
            contentColor = VOnSurface,
            dragHandle = { VikoDragHandle() }
        ) {
            FactoryWifiSheetContent(
                current = uiState.factoryWifi,
                onSave = { wifi ->
                    viewModel.saveFactoryWifi(wifi)
                    showWifiSetup = false
                },
                onClose = { showWifiSetup = false }
            )
        }
    }
}

@Composable
internal fun NearbyDeviceSheetContent(
    devices: List<NearbyDevice>,
    isScanning: Boolean,
    hasPermission: Boolean,
    isBluetoothEnabled: Boolean,
    isEnrolled: (NearbyDevice) -> Boolean,
    addedCount: Int,
    onSelect: (NearbyDevice) -> Unit,
    onRescan: () -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
    isBluetoothSupported: Boolean = true,
    isLocationReady: Boolean = true,
    scanError: String? = null,
    onBluetoothSettings: () -> Unit = {},
    onPermissionSettings: () -> Unit = {},
    replay: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nearby Device",
                fontSize = 24.sp,
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

        Spacer(Modifier.height(16.dp))
        Text(
            text = if (replay) "Replay mode: try adding a demo member. Your saved data stays unchanged." else "Pair your device to add a new member.",
            fontSize = 16.sp,
            color = VOnSurfaceSecondary,
            modifier = Modifier
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (devices.isEmpty() || !hasPermission || !isBluetoothEnabled || !isLocationReady || !isBluetoothSupported) {
                Text(
                    // Each of these is a different fix, and an undifferentiated
                    // "No devices found" sends the supervisor hunting the wrong one.
                    text = when {
                        replay -> "No demo devices shown yet. Tap Find nearby devices."
                        !isBluetoothSupported -> "This phone does not support Bluetooth LE."
                        !hasPermission -> "Allow nearby-device access to find your wearable."
                        !isBluetoothEnabled -> "Turn Bluetooth on to find devices."
                        !isLocationReady -> "Turn on Location services for Bluetooth scanning on this Android version."
                        isScanning -> "Searching for nearby ViKo devices…"
                        else -> "No devices found. Put the wearable in pairing mode and try again."
                    },
                    fontSize = 16.sp,
                    color = VOnSurfaceSecondary.copy(alpha = 0.7f),
                    modifier = Modifier
                )
                // Match the firmware's startup gesture; a normal long PTT is speech.
                if (!replay && hasPermission && isBluetoothEnabled && !isScanning) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Hold PTT while resetting the wearable, and keep holding for 5 seconds. New wearables enter pairing automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VOnSurfaceSecondary.copy(alpha = 0.7f),
                        modifier = Modifier
                    )
                }
            } else {
                Column(
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

        }
        scanError?.let {
            Text(it, color = VWarning, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp))
        }
        // Keep the action outside the scrolling results, including during scans.
        VikoBlueButton(
            text = when {
                !isBluetoothSupported -> "Bluetooth LE unavailable"
                !hasPermission -> "Allow nearby devices"
                !isBluetoothEnabled -> "Turn on Bluetooth"
                !isLocationReady -> "Turn on Location"
                isScanning -> "Searching nearby devices…"
                else -> "Find nearby devices"
            },
            enabled = isBluetoothSupported && !isScanning,
            onClick = onRescan
        )
        if (!hasPermission) {
            TextButton(onClick = onPermissionSettings) { Text("Open app permissions") }
        } else if (isBluetoothSupported && !replay) {
            TextButton(onClick = onBluetoothSettings) { Text("Bluetooth settings") }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Onboarding pairs a whole shift in one sitting, so the flow stays open
        // after each save. Done is how the supervisor says they are finished.
        if (addedCount > 0) {
            Text(
                text = "$addedCount member${if (addedCount == 1) "" else "s"} added",
                style = MaterialTheme.typography.bodySmall,
                color = VOnSurfaceSecondary,
                modifier = Modifier
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
    VikoListRow(title = device.name, onClick = onSelect, enabled = !isEnrolled) {
        Text(if (isEnrolled) "Paired" else "Pair Device", color = VOnSurfaceSecondary, fontSize = 12.sp)
        if (!isEnrolled) {
            Spacer(Modifier.width(12.dp))
            Icon(painterResource(R.drawable.ic_link), null, tint = VOnboardingBlue, modifier = Modifier.size(20.dp))
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
private fun SetupMemberScreenContentPreview() {
    VikoTheme {
        SetupMemberScreenContent(
            device = previewDevice("33", "ViKo-A1B2"),
            name = "Budi",
            onNameChange = {},
            zone = "Production Line 2",
            onZoneChange = {},
            isWifiConfigured = true,
            onSetUpWifi = {},
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
private fun SetupMemberScreenContentEmptyPreview() {
    VikoTheme {
        SetupMemberScreenContent(
            device = previewDevice("33", "ViKo-A1B2"),
            name = "",
            onNameChange = {},
            zone = "",
            onZoneChange = {},
            isWifiConfigured = false,
            onSetUpWifi = {},
            channelName = null,
            isChannelLocked = false,
            canSave = false,
            isProvisioning = false,
            error = "Timed out. Is the badge still in pairing mode?",
            onPickChannel = {}, onSave = {}, onClose = {}
        )
    }
}
