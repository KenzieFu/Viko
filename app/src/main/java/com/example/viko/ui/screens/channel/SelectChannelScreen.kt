package com.example.viko.ui.screens.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    var showAddSheet by remember { mutableStateOf(false) }
    var newChannelName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredChannels = appState.channels.filter {
        searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true)
    }

    // Add New Channel bottom sheet
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            containerColor = VBackground,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sheet header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add New Channel",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = VOnSurface
                    )
                    IconButton(onClick = { showAddSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = VOnSurface)
                    }
                }

                // Text field — standalone white card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VSurface)
                ) {
                    TextField(
                        value = newChannelName,
                        onValueChange = { newChannelName = it },
                        placeholder = {
                            Text(
                                "Enter Channel Name",
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

                // Continue button — full width, blue
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
                            showAddSheet = false
                        }
                    },
                    enabled = newChannelName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VAccentBlue,
                        disabledContainerColor = VAccentBlue.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        "Continue",
                        style = MaterialTheme.typography.titleMedium,
                        color = VOnPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // Main screen — bottom sheet style
    Box(modifier = Modifier.fillMaxSize()) {

        // Dark scrim at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.12f)
                .background(Color(0xFF1A1A1A))
        )

        // White sheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(VBackground)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VBorder)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
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
                    text = "Select Channel",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = VOnSurface,
                    modifier = Modifier.weight(1f)
                )
                // Blue + button
                IconButton(
                    onClick = { showAddSheet = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(VAccentBlue)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Channel",
                        tint = VOnPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
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
                            "Search Channel",
                            color = VOnSurfaceSecondary,
                            fontSize = 15.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = VOnSurface,
                        unfocusedTextColor = VOnSurface,
                        cursorColor = VAccentBlue
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Channel list or empty state
            if (filteredChannels.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "No channel yet",
                        color = VOnSurfaceSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { showAddSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VBorder)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = VAccentBlue
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Add Channel", color = VAccentBlue, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredChannels, key = { it.id }) { channel ->
                        // Standalone white card per channel — bold name only
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(VSurface)
                                .clickable {
                                    selectedChannelId = channel.id
                                    appState.updateWorkerChannel(workerId, channel.id)
                                    onChannelSelected(channel.id)
                                }
                                .padding(horizontal = 20.dp, vertical = 20.dp)
                        ) {
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = VOnSurface
                            )
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }
}