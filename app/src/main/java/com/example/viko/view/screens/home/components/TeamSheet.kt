package com.example.viko.view.screens.home.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.viko.R
import com.example.viko.components.AnnouncementWaveBackground
import com.example.viko.components.EditableMemberListItem
import com.example.viko.components.MemberListItem
import com.example.viko.domain.team.model.Worker
import com.example.viko.view.screens.home.ChannelSummary
import com.example.viko.view.theme.VAccentBlue
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VSurface

/**
 * The roster behind a channel card: who is on this channel, talk to all of
 * them, or switch to edit mode to rename, remove and pull in members from
 * elsewhere.
 */
@Composable
fun TeamSheetContent(
    summary: ChannelSummary,
    assignableWorkers: List<Worker>,
    onClose: () -> Unit,
    onDirectCall: (String) -> Unit,
    onRenameWorker: (workerId: String, name: String) -> Unit,
    onRemoveWorker: (String) -> Unit,
    onAssignWorker: (String) -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var isPickingMember by remember { mutableStateOf(false) }
    var workerToDelete by remember { mutableStateOf<Worker?>(null) }
    var isTalking by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
    ) {
        if (isTalking) {
            AnnouncementWaveBackground(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(240.dp)
            )
        }

        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${summary.channel.name} (${summary.onlineCount}/${summary.totalCount})",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f)
                )

                if (isEditMode) {
                    Button(onClick = {
                        isEditMode = false
                        isPickingMember = false
                    }) {
                        Text("Save")
                    }
                } else {
                    IconButton(onClick = { isEditMode = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit team")
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isEditMode) {
                EditableRoster(
                    members = summary.members,
                    assignableWorkers = assignableWorkers,
                    isPickingMember = isPickingMember,
                    onStartPicking = { isPickingMember = true },
                    onPickWorker = { workerId ->
                        onAssignWorker(workerId)
                        isPickingMember = false
                    },
                    onRenameWorker = onRenameWorker,
                    onRequestDelete = { workerToDelete = it }
                )
            } else {
                summary.members.forEach { worker ->
                    MemberListItem(worker = worker, onDirectCall = { onDirectCall(worker.id) })
                }

                Spacer(modifier = Modifier.weight(1f))

                TalkToTeamButton(isTalking = isTalking, onToggle = { isTalking = !isTalking })
            }
        }
    }

    workerToDelete?.let { worker ->
        AlertDialog(
            onDismissRequest = { workerToDelete = null },
            title = { Text("Delete member?") },
            text = { Text("Your paired devices data will be deleted and has to setup a new one later.") },
            dismissButton = {
                OutlinedButton(onClick = { workerToDelete = null }) { Text("Cancel") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveWorker(worker.id)
                        workerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VAccentRed)
                ) {
                    Text("Delete")
                }
            }
        )
    }
}

@Composable
private fun EditableRoster(
    members: List<Worker>,
    assignableWorkers: List<Worker>,
    isPickingMember: Boolean,
    onStartPicking: () -> Unit,
    onPickWorker: (String) -> Unit,
    onRenameWorker: (workerId: String, name: String) -> Unit,
    onRequestDelete: (Worker) -> Unit
) {
    Column {
        members.forEach { worker ->
            EditableMemberListItem(
                worker = worker,
                onNameChange = { onRenameWorker(worker.id, it) },
                onDelete = { onRequestDelete(worker) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onStartPicking)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(VBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = VAccentBlue)
            }
            Text(
                text = "Add member",
                style = MaterialTheme.typography.titleMedium,
                color = VOnSurface
            )
        }

        if (isPickingMember) {
            LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                items(assignableWorkers, key = { it.id }) { worker ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickWorker(worker.id) }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(worker.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun TalkToTeamButton(isTalking: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isTalking) VAccentBlue else VSurface)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_mic),
            contentDescription = null,
            tint = if (isTalking) Color.White else VAccentBlue
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Talk to Team",
            color = if (isTalking) Color.White else VOnSurface
        )
    }
}
