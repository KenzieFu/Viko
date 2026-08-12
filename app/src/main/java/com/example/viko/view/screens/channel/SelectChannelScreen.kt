package com.example.viko.view.screens.channel

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
import androidx.compose.runtime.remember
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectChannelScreen(
    onBack: () -> Unit,
    onChannelSelected: () -> Unit,
    viewModel: SelectChannelViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var addChannelPrefill by remember { mutableStateOf<String?>(null) }

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
                viewModel.selectChannel(channelId)
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
                    viewModel.createChannelAndSelect(name)
                    addChannelPrefill = null
                    onChannelSelected()
                },
                onClose = { addChannelPrefill = null }
            )
        }
    }
}
