package com.example.viko.view.screens.team

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.components.BatteryBadge
import com.example.viko.components.DetailField
import com.example.viko.components.InfoRow
import com.example.viko.components.MissingWorker
import com.example.viko.components.PrimaryButton
import com.example.viko.components.StatusDot
import com.example.viko.di.AppViewModelProvider
import com.example.viko.domain.message.model.TranslationMessage
import com.example.viko.domain.team.model.Worker
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VShadow
import com.example.viko.view.theme.VSurface

/** Everything known about one worker: profile, badge status, recent traffic. */
@Composable
fun MemberDetailScreen(
    onBack: () -> Unit,
    onDirectCall: (String) -> Unit,
    onSelectChannel: (String) -> Unit,
    viewModel: MemberDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // See MissingWorker: an early return here would leave a black screen with no
    // close button while Room answers, or after the member has been removed.
    val worker = uiState.worker
    if (worker == null) {
        MissingWorker(onBack = onBack)
        return
    }

    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            containerColor = VSurface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Remove ${worker.name}?") },
            text = {
                Text(
                    "This will unpair the device and remove the member.",
                    color = VOnSurfaceSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeWorker()
                        showRemoveConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VAccentRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRemoveConfirm = false },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, VBorder)
                ) {
                    Text("Cancel", color = VOnSurface)
                }
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

        ProfileHeader(worker = worker)

        Spacer(modifier = Modifier.height(24.dp))

        DetailsCard(
            worker = worker,
            channelName = uiState.channelName,
            onSelectChannel = { onSelectChannel(worker.id) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.recentMessages.isNotEmpty()) {
            RecentMessages(messages = uiState.recentMessages)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrimaryButton(text = "Direct Call", onClick = { onDirectCall(worker.id) })
            TextButton(
                onClick = { showRemoveConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Remove Member", color = VAccentRed, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun ColumnScope.ProfileHeader(worker: Worker) {
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
            text = worker.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""),
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
}

@Composable
private fun DetailsCard(
    worker: Worker,
    channelName: String?,
    onSelectChannel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(1.dp, RoundedCornerShape(16.dp), spotColor = VShadow)
            .clip(RoundedCornerShape(16.dp))
            .background(VSurface)
    ) {
        DetailField(label = "Name", value = worker.name)
        HorizontalDivider(color = VBorder)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelectChannel)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Channel", fontSize = 11.sp, color = VOnSurfaceSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = channelName ?: "Not assigned",
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

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoRow("Device Address", worker.macAddress)
            InfoRow("Firmware", worker.firmwareVersion)
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
                    Text(worker.zone, fontSize = 12.sp, color = VOnSurface)
                    BatteryBadge(level = worker.batteryLevel)
                }
            }
        }
    }
}

@Composable
private fun RecentMessages(messages: List<TranslationMessage>) {
    Text(
        text = "Recent Messages",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
    Spacer(modifier = Modifier.height(8.dp))

    messages.forEach { message ->
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
                Text(
                    text = message.translatedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VOnSurface
                )
                Text(
                    text = message.originalText,
                    style = MaterialTheme.typography.bodySmall,
                    color = VOnSurfaceSecondary
                )
                Text(text = message.timestamp, fontSize = 10.sp, color = VOnSurfaceSecondary)
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}
