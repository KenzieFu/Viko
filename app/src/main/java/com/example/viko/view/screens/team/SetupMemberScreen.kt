package com.example.viko.view.screens.team

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.team.model.Worker
import com.example.viko.mock.AppState
import com.example.viko.mock.mockNearbyDevices
import com.example.viko.components.*
import com.example.viko.view.theme.*

@Composable
fun SetupMemberScreen(
    deviceId: String,
    appState: AppState,
    onBack: () -> Unit,
    onSetupComplete: () -> Unit,
    onSelectChannel: (String) -> Unit
) {
    var workerName by remember { mutableStateOf("") }
    var selectedChannelId by remember { mutableStateOf(appState.channels.firstOrNull()?.id ?: "") }
    var zone by remember { mutableStateOf("Zone 1") }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    val device = mockNearbyDevices.find { it.id == deviceId } ?: mockNearbyDevices.firstOrNull()
    val selectedChannel = appState.channels.find { it.id == selectedChannelId }

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
                        Icons.Default.CheckCircle,
                        null,
                        tint = VOnline,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Text(
                    "Setup Complete!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = VOnSurface
                )
                Text(
                    "The device has been configured with Wi-Fi credentials and worker profile via BLE.",
                    color = VOnSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SetupInfoRow("Device", device?.name ?: "Unknown")
                    SetupInfoRow("Worker", workerName)
                    SetupInfoRow("Channel", selectedChannel?.name ?: "Unknown")
                    SetupInfoRow("Zone", zone)
                }
                PrimaryButton(text = "Done", onClick = onSetupComplete)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
            Text(
                "SETUP DEVICE",
                style = MaterialTheme.typography.titleSmall,
                color = VOnSurfaceSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        // Device info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VSurface)
                .border(1.dp, VBorder, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(VOnSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bluetooth, null, tint = VOnSurface, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(
                        device?.name ?: "VIKO-ESP32",
                        style = MaterialTheme.typography.titleMedium,
                        color = VOnSurface
                    )
                    Text(
                        device?.address ?: "Unknown",
                        style = MaterialTheme.typography.bodySmall,
                        color = VOnSurfaceSecondary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(VOnline.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Paired", fontSize = 11.sp, color = VOnline, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Form
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(VSurface)
        ) {
            // Worker name
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Worker Name", fontSize = 11.sp, color = VOnSurfaceSecondary)
                TextField(
                    value = workerName,
                    onValueChange = { workerName = it },
                    placeholder = { Text("Enter worker name", color = VOnSurfaceSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = VOnSurface,
                        unfocusedTextColor = VOnSurface,
                        cursorColor = VOnSurface
                    )
                )
            }
            HorizontalDivider(color = VBorder)

            // Channel selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectChannel(deviceId) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Channel", fontSize = 11.sp, color = VOnSurfaceSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        selectedChannel?.name ?: "Select Channel",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selectedChannel != null) VOnSurface else VOnSurfaceSecondary
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = VOnSurfaceSecondary, modifier = Modifier.size(16.dp))
            }
            HorizontalDivider(color = VBorder)

            // Zone
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Zone", fontSize = 11.sp, color = VOnSurfaceSecondary)
                TextField(
                    value = zone,
                    onValueChange = { zone = it },
                    placeholder = { Text("e.g. Zone 1", color = VOnSurfaceSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = VOnSurface,
                        unfocusedTextColor = VOnSurface,
                        cursorColor = VOnSurface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Wi-Fi / config note
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VOnSurface.copy(alpha = 0.04f))
                .padding(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, null, tint = VOnSurfaceSecondary, modifier = Modifier.size(16.dp))
                Text(
                    "Wi-Fi credentials and worker profile will be sent to the device via BLE. " +
                            "The device will store this in NVS flash and connect automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VOnSurfaceSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save button
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            PrimaryButton(
                text = if (isLoading) "Configuring..." else "Save & Configure Device",
                onClick = {
                    isLoading = true
                    // Simulate config push
                    val newWorker = Worker(
                        id = "w_${System.currentTimeMillis()}",
                        name = workerName.ifBlank { "New Worker" },
                        channelId = selectedChannelId,
                        zone = zone,
                        deviceAddress = device?.address ?: "Unknown",
                        macAddress = device?.address ?: "00:00:00:00:00:00",
                        batteryLevel = 100,
                        isOnline = true
                    )
                    appState.addWorker(newWorker)
                    isLoading = false
                    showSuccess = true
                },
                enabled = !isLoading
            )
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
        Spacer(modifier = Modifier.height(20.dp))
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
