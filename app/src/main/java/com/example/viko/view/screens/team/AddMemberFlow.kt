package com.example.viko.view.screens.team

import com.example.viko.view.theme.VikoTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.components.InfoRow
import com.example.viko.components.VikoBlueButton
import com.example.viko.components.VikoDragHandle
import com.example.viko.components.VikoTextField
import com.example.viko.di.AppViewModelProvider
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.view.screens.channel.AddNewChannelSheetContent
import com.example.viko.view.screens.channel.SelectChannelSheetContent
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VOffline
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VSurfaceVariant
import com.example.viko.view.theme.VWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberFlow(
    onDismiss: () -> Unit,
    viewModel: AddMemberViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showChannelPicker by rememberSaveable { mutableStateOf(false) }
    var addChannelPrefill by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.rescan() }

    val dismiss = {
        viewModel.restart()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = VSurface,
        contentColor = VOnSurface,
        dragHandle = { VikoDragHandle() }
    ) {
        when (uiState.step) {
            AddMemberStep.Pairing -> NearbyDeviceSheetContent(
                devices = uiState.devices,
                isScanning = uiState.isScanning,
                isNetworkAvailable = uiState.isNetworkAvailable,
                error = uiState.error,
                isEnrolled = uiState::isEnrolled,
                addedCount = uiState.addedCount,
                onSelect = viewModel::select,
                onRescan = viewModel::rescan,
                onDone = dismiss,
                onClose = dismiss
            )

            AddMemberStep.Setup -> SetupMemberSheetContent(
                device = uiState.pairedDevice,
                name = uiState.name,
                onNameChange = viewModel::onNameChange,
                zone = uiState.zone,
                onZoneChange = viewModel::onZoneChange,
                channelName = uiState.selectedChannelName,
                canSave = uiState.canSave,
                isProvisioning = uiState.isProvisioning,
                error = uiState.error,
                onPickChannel = { showChannelPicker = true },

                onSave = viewModel::save,
                onClose = dismiss
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
                channels = uiState.channels,
                selectedChannelId = uiState.selectedChannelId,
                memberCounts = uiState.memberCounts,
                onSelect = { channelId ->

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
            containerColor = VSurface,
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
    isNetworkAvailable: Boolean,
    error: String? = null,
    isEnrolled: (NearbyDevice) -> Boolean,
    addedCount: Int,
    onSelect: (NearbyDevice) -> Unit,
    onRescan: () -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit
) {
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
                text = "Wi-Fi badges",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VOnSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Wifi,
                contentDescription = null,
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
            text = "Connect the phone and badges to the same router. No internet needed.",
            style = MaterialTheme.typography.bodySmall,
            color = VOnSurfaceSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (error != null) Text(error, color = VWarning)

        if (devices.isEmpty()) {
            Text(

                text = when {
                    !isNetworkAvailable -> "Connect this phone to the router Wi-Fi first."
                    else -> "Waiting for badges on this Wi-Fi (up to 15 seconds). " +
                        "Keep ViKo open. Check the badge console shows the same router SSID " +
                        "and disable guest/client isolation on the router."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = VOnSurfaceSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                devices.forEach { device ->
                    NearbyDeviceRow(
                        device = device,
                        isEnrolled = isEnrolled(device),
                        onSelect = { onSelect(device) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        VikoBlueButton(text = "Scan again", onClick = onRescan)

        if (addedCount > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "$addedCount member${if (addedCount == 1) "" else "s"} added",
                style = MaterialTheme.typography.bodySmall,
                color = VOnSurfaceSecondary
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
            .background(VSurfaceVariant)

            .clickable(enabled = !isEnrolled, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isEnrolled) VOnSurfaceSecondary else VOnSurface
            )
            Text(

                text = device.address,
                fontSize = 11.sp,
                color = VOnSurfaceSecondary
            )
        }
        if (isEnrolled) {
            Text("Paired", color = VOnSurfaceSecondary, fontSize = 12.sp)
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
    zone: String,
    onZoneChange: (String) -> Unit,
    channelName: String?,
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
            .verticalScroll(rememberScrollState())
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

            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("Factory zone", fontSize = 11.sp, color = VOnSurfaceSecondary)
                VikoTextField(
                    value = zone,
                    onValueChange = onZoneChange,
                    placeholder = "e.g. Production Line 2",
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
                    contentDescription = null,
                    tint = VOnSurfaceSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Member details are saved on this phone. The badge is already connected to Wi-Fi.",
            style = MaterialTheme.typography.bodySmall, color = VOnSurfaceSecondary)
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VSurfaceVariant)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoRow("Device Address", device?.address ?: "Unknown")
            InfoRow("Device ID", device?.id ?: "Unknown")
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = VWarning
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        VikoBlueButton(
            text = if (isProvisioning) "Checking connection…" else "Add member",
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
                    text = "Keep both devices connected to the same router.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VOnSurfaceSecondary
                )
            }
        }
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

private fun previewDevice(id: String, name: String) =
    NearbyDevice(id = id, name = name, address = "192.168.1.$id")

/**
 * The two steps are previewed as their sheet *contents*, not through
 * [AddMemberFlow] itself — that wraps everything in a ModalBottomSheet, which
 * has no host to render into inside a preview.
 */
@Preview(showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun NearbyDeviceSheetContentPreview() {
    VikoTheme {
        NearbyDeviceSheetContent(
            devices = listOf(
                previewDevice("33", "ViKo-A1B2"),
                previewDevice("66", "ViKo-C3D4"),
                previewDevice("99", "ViKo-E5F6")
            ),
            isScanning = true,
            isNetworkAvailable = true,
            isEnrolled = { it.id == "99" },
            addedCount = 1,
            onSelect = {}, onRescan = {}, onDone = {}, onClose = {}
        )
    }
}

/** Scanning with nothing found yet. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun NearbyDeviceSheetContentScanningPreview() {
    VikoTheme {
        NearbyDeviceSheetContent(
            devices = emptyList(),
            isScanning = true,
            isNetworkAvailable = true,
            isEnrolled = { false },
            addedCount = 0,
            onSelect = {}, onRescan = {}, onDone = {}, onClose = {}
        )
    }
}

/** Setup filled in and ready to save. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun SetupMemberSheetContentPreview() {
    VikoTheme {
        SetupMemberSheetContent(
            device = previewDevice("33", "ViKo-A1B2"),
            name = "Budi",
            onNameChange = {},
            zone = "Production Line 2",
            onZoneChange = {},
            channelName = "Warehouse",
            canSave = true,
            isProvisioning = false,
            error = null,
            onPickChannel = {}, onSave = {}, onClose = {}
        )
    }
}

/** Setup still empty — Save dim, which is the state that is easy to get wrong. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun SetupMemberSheetContentEmptyPreview() {
    VikoTheme {
        SetupMemberSheetContent(
            device = previewDevice("33", "ViKo-A1B2"),
            name = "",
            onNameChange = {},
            zone = "",
            onZoneChange = {},
            channelName = null,
            canSave = false,
            isProvisioning = false,
            error = "Badge disconnected. Check the router connection.",
            onPickChannel = {}, onSave = {}, onClose = {}
        )
    }
}
