package com.example.viko.ui.screens.team

import androidx.compose.foundation.Image  // ← this was the missing one
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.example.viko.ui.components.*
import com.example.viko.ui.theme.*

@Composable
fun MemberDetailScreen(
    workerId: String,
    appState: AppState,
    onBack: () -> Unit,
    onDirectCall: (String) -> Unit,
    onSelectChannel: (String) -> Unit,
    onRemove: () -> Unit
) {
    val worker = appState.workers.find { it.id == workerId } ?: return
    val channel = appState.channels.find { it.id == worker.channelId }

    var editedName by remember { mutableStateOf(worker.name) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            containerColor = VSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Remove ${worker.name}?", style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Text(
                    "This will unpair the device and remove the member.",
                    color = VOnSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        appState.removeWorker(worker.id)
                        showRemoveConfirm = false
                        onRemove()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VAccentRed),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Remove") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRemoveConfirm = false },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VBorder)
                ) { Text("Cancel", color = VOnSurface) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        // Top bar — X only, top-right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = VOnSurface)
            }
        }

        // Device image — centered, large
        Image(
            painter = painterResource(id = R.drawable.device),
            contentDescription = "Device",
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.CenterHorizontally),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Name — label above, standalone card
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
                    value = editedName,
                    onValueChange = { editedName = it },
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

        // Channel — label above, standalone card with chevron
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
                    .clickable { onSelectChannel(worker.id) }
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel?.name ?: "Select Channel",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (channel != null) VOnSurface else VOnSurfaceSecondary,
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

        // Device info — muted card, two rows
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
                    worker.macAddress,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = worker.zone,
                        style = MaterialTheme.typography.bodySmall,
                        color = VOnSurfaceSecondary
                    )
                    if (worker.batteryLevel != null) {
                        Text("•", style = MaterialTheme.typography.bodySmall, color = VOnSurfaceSecondary)
                        BatteryBadge(level = worker.batteryLevel)
                        Text(
                            text = "${worker.batteryLevel}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = VOnSurfaceSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(32.dp))

        // Save Changes — full width, blue
        Button(
            onClick = {
                appState.updateWorkerName(worker.id, editedName)
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VAccentBlue)
        ) {
            Text(
                text = "Save Changes",
                style = MaterialTheme.typography.titleMedium,
                color = VOnPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Remove Member — red text, centered
        TextButton(
            onClick = { showRemoveConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Remove Member",
                color = VAccentRed,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
        Spacer(modifier = Modifier.height(20.dp))
    }
}