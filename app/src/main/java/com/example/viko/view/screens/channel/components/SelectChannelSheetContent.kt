package com.example.viko.view.screens.channel.components

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.components.VikoTextField
import com.example.viko.domain.channel.model.Channel
import com.example.viko.view.theme.*

// The two channel-picking sheets from Figma ("Select Channel" and "Add New
// Channel"). They are CONTENT only — no ModalBottomSheet wrapper — so the same
// code serves both the add-member flow and the member-detail screen. Whoever
// shows them owns the sheet and owns the state; these just render and report
// taps upward.

@Preview(showBackground = true)
@Composable
private fun SelectChannelSheetContentPreview() {
    VikoTheme {
        SelectChannelSheetContent(
            channels = listOf(Channel(id = "c1", name = "Kitchen"), Channel(id = "c2", name = "Front Desk")),
            selectedChannelId = "c1",
            onSelect = {},
            onRequestAddChannel = {},
            onBack = {},
            memberCountFor = { 3 }
        )
    }
}

@Composable
fun SelectChannelSheetContent(
    channels: List<Channel>,
    selectedChannelId: String,
    onSelect: (String) -> Unit,
    onRequestAddChannel: (prefill: String) -> Unit,
    onBack: () -> Unit,
    memberCountFor: (String) -> Int = { 0 }
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = channels.filter {
        searchQuery.isBlank() || it.name.contains(searchQuery.trim(), ignoreCase = true)
    }
    // Figma: typing a name that does not exist yet offers to create it inline
    // ("Store   Add New +") instead of dead-ending on an empty list.
    val exactMatch = channels.any { it.name.equals(searchQuery.trim(), ignoreCase = true) }
    val offerCreate = searchQuery.isNotBlank() && !exactMatch

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 440.dp)
            .navigationBarsPadding()
    ) {
        // Header: back chevron, title, blue add button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VOnSurface)
            }
            Text(
                text = "Select Channel",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VOnSurface,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(VMicBlue)
                    .clickable { onRequestAddChannel("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    "Add Channel",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Search
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VSurfaceVariant)
                .border(1.dp, VBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = VOnSurfaceSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            VikoTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search Channel",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filtered.isEmpty() && !offerCreate) {
            // "No channel yet" — reachable once every channel has been deleted
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "No channel yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VOnSurfaceSecondary
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onRequestAddChannel(searchQuery.trim()) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = VMicBlue, modifier = Modifier.size(16.dp))
                    Text("Add Channel", color = VMicBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { channel ->
                    ChannelRow(
                        name = channel.name,
                        memberCount = memberCountFor(channel.id),
                        isSelected = channel.id == selectedChannelId,
                        onClick = { onSelect(channel.id) }
                    )
                }

                if (offerCreate) {
                    item {
                        // "Store   Add New +"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VSurfaceVariant)
                                .clickable { onRequestAddChannel(searchQuery.trim()) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = searchQuery.trim(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = VOnSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text("Add New", color = VOnSurfaceSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Add,
                                null,
                                tint = VOnSurfaceSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
