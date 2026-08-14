package com.example.viko.view.screens.channel

import com.example.viko.view.theme.VikoTheme
import com.example.viko.view.screens.previewChannel
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.components.VikoDragHandle
import com.example.viko.di.AppViewModelProvider
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VSurface

/**
 * Full-screen host for the channel picker, used when changing the channel of a
 * member that already exists (from MemberDetailScreen).
 *
 * The add-member flow shows the very same content in a bottom sheet instead —
 * see AddMemberFlow. Both share [SelectChannelSheetContent] so the list, search,
 * blue check and "Add New" row only exist in one place.
 */
@Composable
fun SelectChannelScreen(
    onBack: () -> Unit,
    onChannelSelected: () -> Unit,
    viewModel: SelectChannelViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SelectChannelScreen(
        uiState = uiState,
        onBack = onBack,
        onChannelSelected = onChannelSelected,
        onSelectChannel = viewModel::selectChannel,
        onCreateChannelAndSelect = viewModel::createChannelAndSelect
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectChannelScreen(
    uiState: SelectChannelUiState,
    onBack: () -> Unit,
    onChannelSelected: () -> Unit,
    onSelectChannel: (String) -> Unit,
    onCreateChannelAndSelect: (String) -> Unit
) {
    var addChannelPrefill by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        SelectChannelSheetContent(
            channels = uiState.channels,
            selectedChannelId = uiState.selectedChannelId,
            memberCounts = uiState.memberCounts,
            onSelect = { channelId ->
                onSelectChannel(channelId)
                onChannelSelected()
            },
            onRequestAddChannel = { prefill -> addChannelPrefill = prefill },
            onBack = onBack
        )
    }

    addChannelPrefill?.let { prefill ->
        ModalBottomSheet(
            onDismissRequest = { addChannelPrefill = null },
            containerColor = VSurface,
            contentColor = VOnSurface,
            dragHandle = { VikoDragHandle() }
        ) {
            AddNewChannelSheetContent(
                prefill = prefill,
                onCreate = { name ->
                    onCreateChannelAndSelect(name)
                    addChannelPrefill = null
                    onChannelSelected()
                },
                onClose = { addChannelPrefill = null }
            )
        }
    }
}

@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun SelectChannelScreenPreview() {
    VikoTheme {
        SelectChannelScreen(
            uiState = SelectChannelUiState(
                channels = listOf(
                    previewChannel("c1", "Warehouse"),
                    previewChannel("c2", "Assembly"),
                    previewChannel("c3", "Loading Bay")
                ),
                memberCounts = mapOf("c1" to 3, "c2" to 1),
                selectedChannelId = "c1"
            ),
            onBack = {}, onChannelSelected = {}, onSelectChannel = {}, onCreateChannelAndSelect = {}
        )
    }
}
