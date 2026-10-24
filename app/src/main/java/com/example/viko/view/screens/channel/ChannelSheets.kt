package com.example.viko.view.screens.channel

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.components.ScreenHeader
import com.example.viko.components.SearchField
import com.example.viko.components.VikoBlueButton
import com.example.viko.components.VikoTextField
import com.example.viko.domain.channel.model.Channel
import com.example.viko.view.screens.channel.components.ChannelRow
import com.example.viko.view.screens.channel.components.CreateChannelRow
import com.example.viko.view.screens.channel.components.NoChannelYet
import com.example.viko.view.screens.channel.components.SelectedCheck
import com.example.viko.view.screens.previewChannel
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VikoTheme

// The two channel-picking sheets from Figma ("Select Channel" and "Add New
// Channel"). They are CONTENT only — no ModalBottomSheet wrapper — so the same
// code serves both the add-member flow and the member-detail screen. Whoever
// shows them owns the sheet and owns the state; these just render and report
// taps upward.

@Composable
fun SelectChannelSheetContent(
    channels: List<Channel>,
    selectedChannelId: String,
    onSelect: (String) -> Unit,
    onRequestAddChannel: (prefill: String) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val result = searchChannels(channels, searchQuery)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
    ) {
        ScreenHeader(
            title = "Select Channel",
            onBack = onBack,
            onAdd = { onRequestAddChannel("") },
            addContentDescription = "Add Channel"
        )

        SearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search Channel",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (result.matches.isEmpty() && result.nameToCreate == null) {
            // Reachable on a fresh install, before anyone has made a channel.
            NoChannelYet(onAddChannel = { onRequestAddChannel("") })
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(result.matches, key = { it.id }) { channel ->
                    ChannelRow(name = channel.name, onClick = { onSelect(channel.id) }) {
                        if (channel.id == selectedChannelId) SelectedCheck()
                    }
                }
                result.nameToCreate?.let { name ->
                    item { CreateChannelRow(name = name, onClick = { onRequestAddChannel(name) }) }
                }
            }
        }
    }
}

@Composable
fun AddNewChannelSheetContent(
    prefill: String = "",
    onCreate: (String) -> Unit,
    onClose: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(prefill) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 24.dp)
            .navigationBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Add New Channel",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = VOnSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        VikoTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Enter Channel Name",
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, VBorder, RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.height(24.dp))

        VikoBlueButton(
            text = "Continue",
            enabled = name.isNotBlank(),
            onClick = { onCreate(name.trim()) }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20, heightDp = 720)
@Composable
private fun SelectChannelSheetContentPreview() {
    VikoTheme {
        SelectChannelSheetContent(
            channels = listOf(previewChannel("c1", "Operations"), previewChannel("c2", "Maintenance")),
            selectedChannelId = "c1",
            onSelect = {},
            onRequestAddChannel = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20, heightDp = 720)
@Composable
private fun SelectChannelSheetContentEmptyPreview() {
    VikoTheme {
        SelectChannelSheetContent(
            channels = emptyList(),
            selectedChannelId = "",
            onSelect = {},
            onRequestAddChannel = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun AddNewChannelSheetContentPreview() {
    VikoTheme { AddNewChannelSheetContent(prefill = "", onCreate = {}, onClose = {}) }
}
