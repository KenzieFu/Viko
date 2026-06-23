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
import com.example.viko.ui.theme.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupMemberScreen(
    deviceId: String,
    appState: AppState,
    onBack: () -> Unit,
    onSetupComplete: () -> Unit,
    onSelectChannel: (String) -> Unit  // keep signature intact for nav compatibility
) {
    var workerName by remember { mutableStateOf("") }
    var selectedChannelId by remember { mutableStateOf(appState.channels.firstOrNull()?.id ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var showChannelPicker by remember { mutableStateOf(false) }

    val device = mockNearbyDevices.find { it.id == deviceId } ?: mockNearbyDevices.firstOrNull()
    val selectedChannel = appState.channels.find { it.id == selectedChannelId }

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
                Text(
                    "The device has been configured with Wi-Fi credentials and worker profile via BLE.",
                    color = VOnSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SetupInfoRow("Device", device?.name ?: "Unknown")
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
                        device?.address ?: "-",
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
                        "Device Status",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = VOnSurface
                    )
                    Text(
                        "-",
                        style = MaterialTheme.typography.bodySmall,
                        color = VOnSurfaceSecondary
                    )
                }
            }
        }

        // Save button — pinned to bottom
        Button(
            onClick = {
                isLoading = true
                val newWorker = Worker(
                    id = "w_${System.currentTimeMillis()}",
                    name = workerName.ifBlank { "New Worker" },
                    channelId = selectedChannelId,
                    zone = "",
                    deviceAddress = device?.address ?: "Unknown",
                    macAddress = device?.address ?: "00:00:00:00:00:00",
                    batteryLevel = 100,
                    isOnline = true
                )
                appState.addWorker(newWorker)
                isLoading = false
                showSuccess = true
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
                text = if (isLoading) "Saving..." else "Save",
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