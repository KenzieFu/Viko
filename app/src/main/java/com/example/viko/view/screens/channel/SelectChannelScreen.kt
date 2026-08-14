package com.example.viko.view.screens.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.viko.components.VikoDragHandle
import com.example.viko.domain.channel.model.Channel
import com.example.viko.mock.AppState
import com.example.viko.view.screens.channel.components.AddNewChannelSheetContent
import com.example.viko.view.screens.channel.components.SelectChannelSheetContent
import com.example.viko.view.theme.*

/**
 * Full-screen host for the channel picker, used when changing the channel of a
 * member that already exists (from MemberDetailScreen).
 *
 * The add-member flow shows the very same content in a bottom sheet instead —
 * see AddMemberFlow. Both share SelectChannelSheetContent so the list, search,
 * blue check and "Add New" row only exist in one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectChannelScreen(
    workerId: String,
    appState: AppState,
    onBack: () -> Unit,
    onChannelSelected: (String) -> Unit
) {
    val worker = appState.workers.find { it.id == workerId }
    var addChannelPrefill by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        SelectChannelSheetContent(
            channels = appState.channels,
            selectedChannelId = worker?.channelId ?: "",
            onSelect = { channelId ->
                appState.updateWorkerChannel(workerId, channelId)
                onChannelSelected(channelId)
            },
            onRequestAddChannel = { prefill -> addChannelPrefill = prefill },
            onBack = onBack,
            memberCountFor = { id -> appState.workersForChannel(id).size }
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
                    val channel = Channel(id = "ch_${appState.channels.size + 1}", name = name)
                    appState.channels.add(channel)
                    appState.updateWorkerChannel(workerId, channel.id)
                    addChannelPrefill = null
                    onChannelSelected(channel.id)
                },
                onClose = { addChannelPrefill = null }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectChannelScreenPreview() {
    val appState = AppState()
    VikoTheme {
        SelectChannelScreen(
            workerId = appState.workers.first().id,
            appState = appState,
            onBack = {},
            onChannelSelected = {}
        )
    }
}
