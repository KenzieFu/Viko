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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.domain.channel.model.Channel
import com.example.viko.mock.AppState
import com.example.viko.components.*
import com.example.viko.view.theme.*

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
    var showRemoveConfirm by remember { mutableStateOf(false) }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            containerColor = VSurface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Remove ${worker.name}?") },
            text = { Text("This will unpair the device and remove the member.", color = VOnSurfaceSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        appState.removeWorker(worker.id)
                        showRemoveConfirm = false
                        onBack()
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

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        // Profile avatar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(116.dp)
                .clip(CircleShape)
                .background(VSurface)
                .border(2.dp, VBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = worker.name.split(" ").map { it.first() }.take(2).joinToString(""),
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                color = VOnSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = worker.name,
            style = MaterialTheme.typography.headlineMedium,
            color = VOnSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = worker.nameKorean,
            style = MaterialTheme.typography.bodyLarge,
            color = VOnSurfaceSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Details card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .shadow(1.dp, RoundedCornerShape(16.dp), spotColor = VShadow)
                .clip(RoundedCornerShape(16.dp))
                .background(VSurface)
        ) {
            // Name field
            DetailField(label = "Name", value = worker.name)
            HorizontalDivider(color = VBorder)

            // Channel field (tappable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectChannel(worker.id) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Channel", fontSize = 11.sp, color = VOnSurfaceSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = channel?.name ?: "Not assigned",
                        style = MaterialTheme.typography.titleMedium,
                        color = VOnSurface
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = VOnSurfaceSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            HorizontalDivider(color = VBorder)

            // Device info
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Device Address", fontSize = 12.sp, color = VOnSurfaceSecondary)
                    Text(
                        worker.macAddress,
                        fontSize = 12.sp,
                        color = VOnSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Firmware", fontSize = 12.sp, color = VOnSurfaceSecondary)
                    Text(
                        worker.firmwareVersion,
                        fontSize = 12.sp,
                        color = VOnSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Device Status", fontSize = 12.sp, color = VOnSurfaceSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusDot(isOnline = worker.isOnline)
                        Text(
                            text = "${worker.zone}",
                            fontSize = 12.sp,
                            color = VOnSurface
                        )
                        BatteryBadge(level = worker.batteryLevel)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent messages
        val workerMessages = appState.messages.filter { it.workerId == worker.id }
        if (workerMessages.isNotEmpty()) {
            Text(
                "Recent Messages",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            workerMessages.take(3).forEach { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VSurface)
                        .border(1.dp, VBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(msg.translatedText, style = MaterialTheme.typography.bodyMedium, color = VOnSurface)
                        Text(msg.originalText, style = MaterialTheme.typography.bodySmall, color = VOnSurfaceSecondary)
                        Text(msg.timestamp, fontSize = 10.sp, color = VOnSurfaceSecondary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrimaryButton(
                text = "Direct Call",
                onClick = { onDirectCall(worker.id) }
            )
            TextButton(
                onClick = { showRemoveConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Remove Member",
                    color = VAccentRed,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun DetailField(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(label, fontSize = 11.sp, color = VOnSurfaceSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = VOnSurface)
    }
}
