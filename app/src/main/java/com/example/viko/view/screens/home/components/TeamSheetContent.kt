package com.example.viko.view.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.R
import com.example.viko.domain.team.model.Worker
import com.example.viko.mock.AppState
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun TeamSheetContentPreview() {
    val appState = AppState()
    VikoTheme {
        TeamSheetContent(
            channelId = appState.channels.first().id,
            appState = appState,
            onClose = {},
            onDirectCall = {},
            onManageMembers = {}
        )
    }
}

@Composable
fun TeamSheetContent(
    channelId: String,
    appState: AppState,
    onClose: () -> Unit,
    onDirectCall: (String) -> Unit,
    onManageMembers: () -> Unit
) {
    val channel = appState.channels.find { it.id == channelId }

    var isEditMode by remember { mutableStateOf(false) }
    var workerToDelete by remember { mutableStateOf<Worker?>(null) }
    var isTalking by remember { mutableStateOf(false) }
    var isAddMode by remember { mutableStateOf(false) }

    val workers = appState.workers.filter { it.channelId == channelId }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
    ) {

        // ===== SPEAK ANIMATION BACKGROUND =====
        if (isTalking) {
            AnnouncementWaveBackground(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(240.dp)
            )
        }

        Column {

            // ===== HEADER =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "${channel?.name ?: "Team"} (${workers.count { it.isOnline }}/${workers.size})",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f)
                )

                if (!isEditMode) {
                    IconButton(
                        onClick = { isEditMode = true }
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                } else {
                    Button(
                        onClick = {
                            isEditMode = false
                        }
                    ) {
                        Text("Save")
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ===== CONTENT =====
            if (!isEditMode) {

                workers.forEach { worker ->
                    MemberListItem(
                        worker = worker,
                        onDirectCall = { onDirectCall(worker.id) }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // ===== TALK BUTTON =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isTalking) VAccentBlue else VSurface)
                        .clickable { isTalking = !isTalking },
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

            } else {

                Column {

                    workers.forEach { worker ->
                        EditableMemberListItem(
                            worker = worker,
                            onDelete = { workerToDelete = worker },
                            onNameChange = { }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAddMode = true }
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
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = VAccentBlue
                            )
                        }

                        Text(
                            text = "Add member",
                            style = MaterialTheme.typography.titleMedium,
                            color = VOnSurface
                        )
                    }

                    if (isAddMode) {
                        LazyColumn {
                            items(
                                appState.workers.filter { it.channelId != channelId },
                                key = { it.id }
                            ) { worker ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            appState.assignWorkerToChannel(worker.id, channelId)
                                            isAddMode = false
                                        }
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
        }
    }

    // ===== DELETE DIALOG =====
    workerToDelete?.let { worker ->

        AlertDialog(
            onDismissRequest = { workerToDelete = null },
            title = { Text("Delete member?") },
            text = {
                Text("Your paired devices data will be deleted and has to setup a new one later.")
            },
            dismissButton = {
                OutlinedButton(onClick = { workerToDelete = null }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        appState.removeWorker(worker.id)
                        workerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VAccentRed
                    )
                ) {
                    Text("Delete")
                }
            }
        )
    }
}
