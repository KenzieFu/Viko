package com.example.viko.view.screens.team

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.team.model.Worker
import com.example.viko.mock.AppState
import com.example.viko.components.*
import com.example.viko.view.theme.*

@Composable
fun ManageMemberScreen(
    appState: AppState,
    onBack: () -> Unit,
    onMemberClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Worker?>(null) }
    var filterChannel by remember { mutableStateOf<String?>(null) }

    // Adding a member is a stack of bottom sheets over this screen, not a
    // separate route — see AddMemberFlow for why.
    var showAddMember by remember { mutableStateOf(false) }
    val onAddMember = { showAddMember = true }

    if (showAddMember) {
        AddMemberFlow(appState = appState, onDismiss = { showAddMember = false })
    }

    val filteredWorkers = appState.workers.filter { worker ->
        val matchesSearch = searchQuery.isEmpty() ||
                worker.name.contains(searchQuery, ignoreCase = true)
        val matchesChannel = filterChannel == null || worker.channelId == filterChannel
        matchesSearch && matchesChannel
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = VSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Delete member?", style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Text(
                    "Your paired devices data will be deleted and has to setup a new one later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VOnSurfaceSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        appState.removeWorker(deleteTarget!!.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VAccentRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { deleteTarget = null },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VBorder)
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
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VOnSurface)
            }
            Text(
                text = "Manage Member",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f)
            )
            // Blue circle, white glyph — same control as the one in Select Channel.
            // Was a VOnSurface circle with a VOnPrimary icon, which the dark palette
            // turned into near-white on near-white: an invisible icon on a white blob.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(VMicBlue)
                    .clickable(onClick = onAddMember),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    "Add Member",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VSurface)
                .border(1.dp, VBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = VOnSurfaceSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("Search Member", color = VOnSurfaceSecondary, fontSize = 15.sp)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = VOnSurface,
                    focusedTextColor = VOnSurface,
                    unfocusedTextColor = VOnSurface
                )
            )
            IconButton(onClick = { /* show channel filter */ }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.FilterList, "Filter", tint = VOnSurfaceSecondary, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Channel filter chips — must scroll: with four channels the last chip ran
        // off the right edge and rendered as a column of single letters.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterChannel == null,
                onClick = { filterChannel = null },
                label = { Text("All", fontSize = 12.sp) },
                shape = RoundedCornerShape(8.dp)
            )
            appState.channels.forEach { ch ->
                FilterChip(
                    selected = filterChannel == ch.id,
                    onClick = { filterChannel = if (filterChannel == ch.id) null else ch.id },
                    label = { Text(ch.name, fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = VBorder)

        if (filteredWorkers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Person, null, tint = VOnSurfaceSecondary, modifier = Modifier.size(48.dp))
                    Text("No members found", color = VOnSurfaceSecondary)
                    OutlinedButton(
                        onClick = onAddMember,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VBorder)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Member", color = VOnSurface)
                    }
                }
            }
        } else {
            LazyColumn {
                items(filteredWorkers, key = { it.id }) { worker ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMemberClick(worker.id) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(VBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = worker.name.first().toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = VOnSurface
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = worker.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = VOnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val channel = appState.channels.find { it.id == worker.channelId }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StatusDot(isOnline = worker.isOnline)
                                Text(
                                    text = "${channel?.name ?: "Unknown"} • ${worker.zone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VOnSurfaceSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { deleteTarget = worker },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete",
                                tint = VOnSurfaceSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = VBorder, modifier = Modifier.padding(start = 80.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAddMember,
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VOnSurface)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Member")
                        }
                    }
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }
}
