package com.example.viko.view.screens.team

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.R
import com.example.viko.components.VikoBlueButton
import com.example.viko.components.VikoDragHandle
import com.example.viko.components.VikoFieldLabel
import com.example.viko.components.VikoOutlinedField
import com.example.viko.components.VikoPickerField
import com.example.viko.di.AppViewModelProvider
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.view.screens.channel.AddNewChannelSheetContent
import com.example.viko.view.screens.channel.SelectChannelSheetContent
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VBrand
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VWarning
import com.example.viko.view.theme.VikoTheme

/**
 * Pair a badge, name the person, pick their channel, save — all as one stack of
 * sheets, because the channel picker has to be able to write back into a
 * half-filled setup form.
 */
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
        containerColor = VBackground,
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
                onClose = dismiss
            )

            AddMemberStep.Setup -> SetupMemberSheetContent(
                device = uiState.pairedDevice,
                name = uiState.name,
                onNameChange = viewModel::onNameChange,
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
            containerColor = VBackground,
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
    isNetworkAvailable: Boolean,
    error: String?,
    isEnrolled: (NearbyDevice) -> Boolean,
    addedCount: Int,
    onSelect: (NearbyDevice) -> Unit,
    onRescan: () -> Unit,
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Nearby Device",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_scanning_spinner),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 16.dp, height = 17.dp)
                        .rotate(if (isScanning) rotation else 0f)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Text(
                text = "Pair your device to add a new member.",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        if (error != null) {
            Text(text = error, fontSize = 14.sp, color = VWarning)
        }

        if (devices.isEmpty()) {
            Text(
                text = if (isScanning) "Scanning…" else "No devices found",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.3f)
            )
            // The design stops at "No devices found"; on a real floor the next
            // question is always "why", and it is nearly always the router.
            if (!isNetworkAvailable) {
                Text(
                    text = "Connect this phone to the router Wi-Fi first.",
                    fontSize = 14.sp,
                    color = VOnSurfaceSecondary
                )
            } else if (!isScanning) {
                Text(
                    text = "Badges must be on the same router, with guest/client " +
                        "isolation switched off.",
                    fontSize = 14.sp,
                    color = VOnSurfaceSecondary
                )
            }
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

        if (addedCount > 0) {
            Text(
                text = "$addedCount member${if (addedCount == 1) "" else "s"} added",
                fontSize = 12.sp,
                color = VOnSurfaceSecondary
            )
        }

        VikoBlueButton(text = "Scan again", onClick = onRescan, enabled = !isScanning)
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
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VSurface)
            .clickable(enabled = !isEnrolled, onClick = onSelect)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = device.name,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isEnrolled) VOnSurfaceSecondary else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isEnrolled) {
            Text(text = "Paired", fontSize = 12.sp, color = VOnSurfaceSecondary)
        } else {
            Text(text = "Pair Device", fontSize = 12.sp, color = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                tint = VBrand,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SetupMemberSheetContent(
    device: NearbyDevice?,
    name: String,
    onNameChange: (String) -> Unit,
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
            .navigationBarsPadding()
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = "Close",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Image(
            painter = painterResource(id = R.drawable.viko_badge_device),
            contentDescription = null,
            modifier = Modifier.size(171.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VikoFieldLabel("Name")
                VikoOutlinedField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = "Enter Member Name"
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VikoFieldLabel("Channel")
                VikoPickerField(
                    value = channelName.orEmpty(),
                    placeholder = "Select Channel",
                    onClick = onPickChannel
                )
            }

            // What the badge told us about itself. Read-only: it is reported,
            // not configured.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VSurface)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DeviceFactRow("Device Address", device?.address ?: "Unknown")
                DeviceFactRow("Device ID", device?.id ?: "Unknown")
            }
        }

        if (error != null) {
            Text(
                text = error,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 14.sp,
                color = VWarning
            )
        }

        if (isProvisioning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = VBrand
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Keep both devices connected to the same router.",
                    fontSize = 12.sp,
                    color = VOnSurfaceSecondary
                )
            }
        }

        VikoBlueButton(
            text = if (isProvisioning) "Checking connection…" else "Save",
            enabled = canSave,
            onClick = onSave
        )
    }
}

/** A `label — value` line inside the badge facts card. */
@Composable
private fun DeviceFactRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFFEFEFE)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            color = Color(0xFFFEFEFE).copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun NearbyDeviceSheetContentPreview() {
    VikoTheme {
        NearbyDeviceSheetContent(
            devices = listOf(
                previewDevice("33", "Device 1"),
                previewDevice("66", "Device 2"),
                previewDevice("99", "Device 3")
            ),
            isScanning = true,
            isNetworkAvailable = true,
            error = null,
            isEnrolled = { it.id == "99" },
            addedCount = 1,
            onSelect = {}, onRescan = {}, onClose = {}
        )
    }
}

/** Scan finished with nothing on the network. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun NearbyDeviceSheetContentEmptyPreview() {
    VikoTheme {
        NearbyDeviceSheetContent(
            devices = emptyList(),
            isScanning = false,
            isNetworkAvailable = true,
            error = null,
            isEnrolled = { false },
            addedCount = 0,
            onSelect = {}, onRescan = {}, onClose = {}
        )
    }
}

/** Setup filled in and ready to save. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun SetupMemberSheetContentPreview() {
    VikoTheme {
        SetupMemberSheetContent(
            device = previewDevice("33", "Device 1"),
            name = "Le Khanh Linh",
            onNameChange = {},
            channelName = "Operations",
            canSave = true,
            isProvisioning = false,
            error = null,
            onPickChannel = {}, onSave = {}, onClose = {}
        )
    }
}

/** Setup still empty — Save dim, which is the state that is easy to get wrong. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun SetupMemberSheetContentEmptyPreview() {
    VikoTheme {
        SetupMemberSheetContent(
            device = previewDevice("33", "Device 1"),
            name = "",
            onNameChange = {},
            channelName = null,
            canSave = false,
            isProvisioning = false,
            error = null,
            onPickChannel = {}, onSave = {}, onClose = {}
        )
    }
}
