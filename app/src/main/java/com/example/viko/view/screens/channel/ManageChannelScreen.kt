package com.example.viko.view.screens.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.components.ScreenHeader
import com.example.viko.components.SearchField
import com.example.viko.components.VikoDragHandle
import com.example.viko.di.AppViewModelProvider
import com.example.viko.view.screens.onboarding.TeamReadyOverlay
import com.example.viko.view.screens.channel.components.ChannelRow
import com.example.viko.view.screens.channel.components.CreateChannelRow
import com.example.viko.view.screens.channel.components.NoChannelYet
import com.example.viko.view.screens.previewChannel
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VikoTheme

/**
 * Settings → Manage Channels: every channel with its member count. Tapping one
 * opens Manage Member filtered to it; `+` (or a search miss) creates one.
 */
@Composable
fun ManageChannelScreen(
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    showTeamReady: Boolean = false,
    onGoToHomepage: () -> Unit = {},
    viewModel: ManageChannelViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ManageChannelScreen(
        uiState = uiState,
        onBack = onBack,
        onChannelClick = onChannelClick,
        onCreateChannel = viewModel::createChannel
    )

    // Last beat of onboarding: the channel the supervisor just filled is behind
    // the card, which is the whole point of showing it here and not on Home.
    if (showTeamReady) {
        TeamReadyOverlay(onGoToHomepage = onGoToHomepage)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageChannelScreen(
    uiState: ManageChannelUiState,
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    onCreateChannel: (String) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var addChannelPrefill by rememberSaveable { mutableStateOf<String?>(null) }
    val result = searchChannels(uiState.channels, searchQuery)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(modifier = Modifier.height(16.dp))

        ScreenHeader(
            title = "Manage Channel",
            onBack = onBack,
            onAdd = { addChannelPrefill = "" },
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
            NoChannelYet(onAddChannel = { addChannelPrefill = "" })
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(result.matches, key = { it.id }) { channel ->
                    ChannelRow(name = channel.name, onClick = { onChannelClick(channel.id) }) {
                        Text(
                            text = "${uiState.memberCounts[channel.id] ?: 0}",
                            fontSize = 12.sp,
                            color = VOnSurfaceSecondary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = VOnSurfaceSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                result.nameToCreate?.let { name ->
                    item { CreateChannelRow(name = name, onClick = { addChannelPrefill = name }) }
                }
            }
        }
    }

    addChannelPrefill?.let { prefill ->
        ModalBottomSheet(
            onDismissRequest = { addChannelPrefill = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground,
            contentColor = VOnSurface,
            dragHandle = { VikoDragHandle() }
        ) {
            AddNewChannelSheetContent(
                prefill = prefill,
                onCreate = { name ->
                    onCreateChannel(name)
                    addChannelPrefill = null
                    searchQuery = ""
                },
                onClose = { addChannelPrefill = null }
            )
        }
    }
}

@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ManageChannelScreenPreview() {
    VikoTheme {
        ManageChannelScreen(
            uiState = ManageChannelUiState(
                channels = listOf(
                    previewChannel("c1", "Operations"),
                    previewChannel("c2", "Maintenance"),
                    previewChannel("c3", "Store"),
                    previewChannel("c4", "Facilities")
                ),
                memberCounts = mapOf("c1" to 5, "c2" to 5, "c3" to 5, "c4" to 5)
            ),
            onBack = {}, onChannelClick = {}, onCreateChannel = {}
        )
    }
}

@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ManageChannelScreenEmptyPreview() {
    VikoTheme {
        ManageChannelScreen(
            uiState = ManageChannelUiState(),
            onBack = {}, onChannelClick = {}, onCreateChannel = {}
        )
    }
}
