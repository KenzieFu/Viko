package com.example.viko.ui.screens.team

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
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
import com.example.viko.data.*
import com.example.viko.ui.components.*
import com.example.viko.ui.theme.*

@Composable
fun ManageMemberScreen(
    appState: AppState,
    onBack: () -> Unit,
    onMemberClick: (String) -> Unit,
    onAddMember: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Worker?>(null) }
    var filterChannel by remember { mutableStateOf<String?>(null) }

    val filteredWorkers = appState.workers.filter { worker ->
        val matchesSearch = searchQuery.isEmpty() ||
                worker.name.contains(searchQuery, ignoreCase = true)
        val matchesChannel = filterChannel == null || worker.channelId == filterChannel
        matchesSearch && matchesChannel
    }

    // Delete confirmation dialog
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
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { deleteTarget = null },
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
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = VOnSurface
                )
            }
            Text(
                text = "Manage Member",
                style = MaterialTheme.typography.headlineLarge,
                color = VOnSurface,
                modifier = Modifier.weight(1f)
            )
            // Blue filled circle + button — matches image
            IconButton(
                onClick = onAddMember,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(VAccentBlue)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Member",
                    tint = VOnPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Search bar — full-width white pill, no outer border
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VSurface)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = VOnSurfaceSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search Member",
                        color = VOnSurfaceSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
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
            IconButton(
                onClick = { /* show channel filter */ },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "Filter",
                    tint = VOnSurfaceSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Member list or empty state
        if (filteredWorkers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = VOnSurfaceSecondary,
                        modifier = Modifier.size(48.dp)
                    )
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
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredWorkers, key = { it.id }) { worker ->
                    MemberCard(
                        worker = worker,
                        channel = appState.channels.find { it.id == worker.channelId },
                        onClick = { onMemberClick(worker.id) },
                        onDelete = { deleteTarget = worker }
                    )
                }
                item {
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun MemberCard(
    worker: Worker,
    channel: Channel?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = VSurface,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Worker name
                Text(
                    text = worker.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = VOnSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Channel badge + location/status row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Outlined channel pill — matches image exactly
                    Box(
                        modifier = Modifier
                            .border(1.dp, VBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = (channel?.name ?: "UNKNOWN").uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = VOnSurfaceSecondary,
                            letterSpacing = 0.3.sp
                        )
                    }

                    // Zone + battery or "Not Available"
                    Text(
                        text = workerSubtitle(worker),
                        style = MaterialTheme.typography.bodySmall,
                        color = VOnSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Chevron — matches the image (not a delete bin icon)
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = VBorder,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Returns "Zone • Battery%" when the device is available, else "Not Available". */
private fun workerSubtitle(worker: Worker): String {
    val hasLocation = worker.zone.isNotBlank()
    val hasBattery = worker.batteryLevel != null
    return when {
        hasLocation && hasBattery -> "${worker.zone} • ${worker.batteryLevel}%"
        hasLocation -> worker.zone
        else -> "Not Available"
    }
}