package com.example.viko.ui.screens.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.data.*
import com.example.viko.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectChannelScreen(
    workerId: String,
    appState: AppState,
    onBack: () -> Unit,
    onChannelSelected: (String) -> Unit
) {
    val worker = appState.workers.find { it.id == workerId }
    var selectedChannelId by remember { mutableStateOf(worker?.channelId ?: "") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newChannelName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredChannels = appState.channels.filter {
        searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true)
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = VSurface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Add New Channel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newChannelName,
                        onValueChange = { newChannelName = it },
                        placeholder = { Text("Enter Channel Name", color = VOnSurfaceSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VOnSurface,
                            unfocusedBorderColor = VBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newChannelName.isNotBlank()) {
                            val newChannel = Channel(
                                id = "ch_${System.currentTimeMillis()}",
                                name = newChannelName.trim()
                            )
                            appState.channels.add(newChannel)
                            selectedChannelId = newChannel.id
                            newChannelName = ""
                            showAddDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VOnSurface)
                ) { Text("Add") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAddDialog = false },
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VOnSurface)
            }
            Text(
                "Select Channel",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(VOnSurface)
            ) {
                Icon(Icons.Default.Add, "Add Channel", tint = VOnPrimary, modifier = Modifier.size(20.dp))
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
                placeholder = { Text("Search Channel", color = VOnSurfaceSecondary, fontSize = 15.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedTextColor = VOnSurface,
                    unfocusedTextColor = VOnSurface,
                    cursorColor = VOnSurface
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = VBorder)

        if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("No channel yet", color = VOnSurfaceSecondary)
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VBorder)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Channel", color = VOnSurface)
                    }
                }
            }
        } else {
            LazyColumn {
                items(filteredChannels) { channel ->
                    val isSelected = channel.id == selectedChannelId
                    val memberCount = appState.workersForChannel(channel.id).size

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedChannelId = channel.id
                                appState.updateWorkerChannel(workerId, channel.id)
                                onChannelSelected(channel.id)
                            }
                            .background(if (isSelected) VOnSurface.copy(alpha = 0.04f) else VSurface)
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (isSelected) VOnSurface else VOnSurface,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (memberCount > 0) {
                                Text(
                                    text = "$memberCount member${if (memberCount != 1) "s" else ""}",
                                    fontSize = 12.sp,
                                    color = VOnSurfaceSecondary
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                "Selected",
                                tint = VOnSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = VBorder, modifier = Modifier.padding(start = 20.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddDialog = true }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(VOnSurface.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, null, tint = VOnSurface, modifier = Modifier.size(20.dp))
                        }
                        Text("Add New Channel", style = MaterialTheme.typography.titleMedium, color = VOnSurface)
                    }
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }
}
