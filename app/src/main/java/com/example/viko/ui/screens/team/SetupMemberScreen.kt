package com.example.viko.ui.screens.team

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.R
import com.example.viko.data.*
import com.example.viko.network.BleProvisioner
import com.example.viko.network.VikoBle
import com.example.viko.ui.theme.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch

/**
 * Wi-Fi credentials persist for the lifetime of the process so a supervisor
 * provisioning a batch of badges types the factory SSID/password once.
 *
 * Deliberately NOT persisted to disk: storing the factory Wi-Fi password in
 * app storage is a separate security decision that nobody has made yet (the
 * docx flags credential handling as unresolved). In-memory means it is gone
 * when the app is killed.
 */
object ProvisioningDefaults {
    var ssid: String = ""
    var password: String = ""
    var zone: String = "Production Line 2"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupMemberScreen(
    deviceId: String,
    appState: AppState,
    onBack: () -> Unit,
    onSetupComplete: () -> Unit,
    onSelectChannel: (String) -> Unit  // keep signature intact for nav compatibility
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ble = remember { VikoBle.get(context) }

    var workerName by remember { mutableStateOf("") }
    var selectedChannelId by remember { mutableStateOf(appState.channels.firstOrNull()?.id ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var showChannelPicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Wi-Fi credentials the badge will be provisioned with. Remembered across
    // pairings in this process so a supervisor setting up a dozen badges types
    // the factory password once, not twelve times.
    var ssid by remember { mutableStateOf(ProvisioningDefaults.ssid) }
    var wifiPassword by remember { mutableStateOf(ProvisioningDefaults.password) }
    var zone by remember { mutableStateOf(ProvisioningDefaults.zone) }

    // deviceId is the badge's BLE MAC, carried over from the pairing screen.
    val device = ble.devices.value.find { it.address == deviceId }
    val deviceAddress = device?.address ?: deviceId
    val selectedChannel = appState.channels.find { it.id == selectedChannelId }

    // Badge device_id per the docx (WR-001, WR-002, ...). Assigned by the app
    // because the badge has no way to know its own position in the fleet.
    val assignedDeviceId = remember(deviceId) {
        "WR-%03d".format(appState.workers.size + 1)
    }

    // Channel picker bottom sheet
    if (showChannelPicker) {
        ModalBottomSheet(
            onDismissRequest = { showChannelPicker = false },
            containerColor = VBackground,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            SelectChannelSheetContent(
                appState = appState,
                selectedChannelId = selectedChannelId,
                onClose = { showChannelPicker = false },
                onSelect = { channelId ->
                    selectedChannelId = channelId
                    showChannelPicker = false
                }
            )
        }
    }

    // Success screen
    if (showSuccess) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(VOnline.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = VOnline,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Text(
                    "Setup Complete!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = VOnSurface
                )
                // Deliberately does not claim the badge is "online" — the app has
                // no runtime transport yet, so it cannot know. Confirm on the
                // badge's serial monitor that it joined the network.
                Text(
                    "The badge saved its Wi-Fi credentials and rebooted into Wi-Fi mode.",
                    color = VOnSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SetupInfoRow("Badge", "$assignedDeviceId  ($deviceAddress)")
                    SetupInfoRow("Worker", workerName)
                    SetupInfoRow("Channel", selectedChannel?.name ?: "Unknown")
                }
                Button(
                    onClick = onSetupComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VAccentBlue)
                ) {
                    Text(
                        "Done",
                        style = MaterialTheme.typography.titleMedium,
                        color = VOnPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        return
    }

    // Main setup screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp)
        ) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "SETUP DEVICE",
                    style = MaterialTheme.typography.titleSmall,
                    color = VOnSurfaceSecondary,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = VOnSurface)
                }
            }

            // Device illustration
            Image(
                painter = painterResource(id = R.drawable.device),
                contentDescription = "Device",
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Name field
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Name",
                    style = MaterialTheme.typography.bodySmall,
                    color = VOnSurfaceSecondary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VSurface)
                ) {
                    TextField(
                        value = workerName,
                        onValueChange = { workerName = it },
                        placeholder = {
                            Text(
                                "Enter Member Name",
                                color = VOnSurfaceSecondary,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = VOnSurface),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = VAccentBlue,
                            focusedTextColor = VOnSurface,
                            unfocusedTextColor = VOnSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Channel field — opens bottom sheet
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Channel",
                    style = MaterialTheme.typography.bodySmall,
                    color = VOnSurfaceSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VSurface)
                        .clickable { showChannelPicker = true }  // ← bottom sheet, not nav
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedChannel?.name ?: "Select Channel",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selectedChannel != null) VOnSurface else VOnSurfaceSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = VOnSurfaceSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Wi-Fi credentials — sent to the badge over BLE. Without these the
            // badge cannot join the factory network and is inert after reboot.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Factory Wi-Fi",
                    style = MaterialTheme.typography.bodySmall,
                    color = VOnSurfaceSecondary
                )
                SetupTextField(
                    value = ssid,
                    onValueChange = { ssid = it; ProvisioningDefaults.ssid = it },
                    placeholder = "Wi-Fi network name (SSID)"
                )
                SetupTextField(
                    value = wifiPassword,
                    onValueChange = { wifiPassword = it; ProvisioningDefaults.password = it },
                    placeholder = "Wi-Fi password",
                    isPassword = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Zone
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Zone",
                    style = MaterialTheme.typography.bodySmall,
                    color = VOnSurfaceSecondary
                )
                SetupTextField(
                    value = zone,
                    onValueChange = { zone = it; ProvisioningDefaults.zone = it },
                    placeholder = "e.g. Production Line 2"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Device info card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VOnSurface.copy(alpha = 0.05f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Device Address",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = VOnSurface
                    )
                    Text(
                        deviceAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = VOnSurfaceSecondary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Assigned ID",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = VOnSurface
                    )
                    Text(
                        assignedDeviceId,
                        style = MaterialTheme.typography.bodySmall,
                        color = VOnSurfaceSecondary
                    )
                }
            }
        }

        // Save button — pinned to bottom. This is the real provisioning step:
        // connect over BLE, write the config, wait for the badge to confirm.
        Button(
            onClick = {
                errorMessage = null
                if (ssid.isBlank()) {
                    errorMessage = "Enter the factory Wi-Fi network name."
                    return@Button
                }
                isLoading = true
                scope.launch {
                    val result = ble.provision(
                        address = deviceAddress,
                        config = BleProvisioner.Config(
                            ssid = ssid,
                            password = wifiPassword,
                            deviceId = assignedDeviceId,
                            workerId = "VN-%03d".format(appState.workers.size + 1),
                            workerName = workerName.ifBlank { "New Worker" },
                            factoryZone = zone.ifBlank { "Unassigned" }
                        )
                    )
                    isLoading = false
                    when (result) {
                        is BleProvisioner.Result.Success -> {
                            // Only record the worker once the badge has actually
                            // confirmed it saved the config. Adding it on tap would
                            // leave the roster claiming badges that were never
                            // provisioned.
                            appState.addWorker(
                                Worker(
                                    id = "w_${System.currentTimeMillis()}",
                                    name = workerName.ifBlank { "New Worker" },
                                    channelId = selectedChannelId,
                                    zone = zone,
                                    deviceAddress = assignedDeviceId,
                                    macAddress = deviceAddress,
                                    batteryLevel = 100,
                                    isOnline = true
                                )
                            )
                            showSuccess = true
                        }
                        is BleProvisioner.Result.Failure -> {
                            errorMessage = result.message
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VAccentBlue,
                disabledContainerColor = VAccentBlue.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = if (isLoading) "Provisioning badge..." else "Save & Provision",
                style = MaterialTheme.typography.titleMedium,
                color = VOnPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SelectChannelSheetContent(
    appState: AppState,
    selectedChannelId: String,
    onClose: () -> Unit,
    onSelect: (String) -> Unit
) {
    val channels = appState.channels

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {

        Text(
            text = "Select Channel",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(20.dp)
        )

        LazyColumn {
            items(channels, key = { it.id }) { channel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(channel.id) }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(channel.name)

                    if (channel.id == selectedChannelId) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = VAccentBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VSurface)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    placeholder,
                    color = VOnSurfaceSecondary,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation =
                if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = VOnSurface),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = VAccentBlue,
                focusedTextColor = VOnSurface,
                unfocusedTextColor = VOnSurface
            )
        )
    }
}

@Composable
fun SetupInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = VOnSurfaceSecondary, fontSize = 13.sp)
        Text(value, color = VOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}