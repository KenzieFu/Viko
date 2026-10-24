package com.example.viko.view.screens.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.components.ConfirmDeleteSheetContent
import com.example.viko.components.DeviceIllustration
import com.example.viko.components.DeviceInfoCard
import com.example.viko.components.LabeledSelectField
import com.example.viko.components.LabeledTextField
import com.example.viko.components.MissingWorker
import com.example.viko.components.VikoBlueButton
import com.example.viko.components.VikoDragHandle
import com.example.viko.di.AppViewModelProvider
import com.example.viko.domain.team.model.Worker
import com.example.viko.view.screens.channel.AddNewChannelSheetContent
import com.example.viko.view.screens.channel.SelectChannelSheetContent
import com.example.viko.view.screens.previewChannel
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.screens.team.components.MemberStatusLine
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VikoTheme

/**
 * One member as an editable form, the same layout as Setup Member: the earpiece,
 * Name and Channel, the badge's address and status, then **Save Changes** and a
 * red **Remove Member**.
 */
@Composable
fun MemberDetailScreen(
    onBack: () -> Unit,
    viewModel: MemberDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MemberDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onSelectChannel = viewModel::selectChannel,
        onCreateChannelAndSelect = viewModel::createChannelAndSelect,
        onSaveChanges = { viewModel.saveChanges(onSaved = onBack) },
        onRemoveWorker = viewModel::removeWorker
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    uiState: MemberDetailUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onSelectChannel: (String) -> Unit,
    onCreateChannelAndSelect: (String) -> Unit,
    /** Saves, then leaves — the caller decides when "then" is. */
    onSaveChanges: () -> Unit,
    onRemoveWorker: () -> Unit
) {
    // See MissingWorker: an early return here would leave a black screen with no
    // close button while Room answers, or after the member has been removed.
    val worker = uiState.worker
    if (worker == null) {
        MissingWorker(onBack = onBack)
        return
    }

    var showChannelPicker by rememberSaveable { mutableStateOf(false) }
    var addChannelPrefill by rememberSaveable { mutableStateOf<String?>(null) }
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
            .navigationBarsPadding()
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            DeviceIllustration(modifier = Modifier.align(Alignment.CenterHorizontally))

            Spacer(modifier = Modifier.height(20.dp))

            LabeledTextField(
                label = "Name",
                value = uiState.name,
                onValueChange = onNameChange,
                placeholder = "Enter Member Name"
            )

            Spacer(modifier = Modifier.height(16.dp))

            LabeledSelectField(
                label = "Channel",
                value = uiState.channelName,
                placeholder = "Select Channel",
                onClick = { showChannelPicker = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            DeviceInfoCard(
                // The Wi-Fi MAC arrives with device_register; until the badge has
                // joined the network the BLE address is the only one we have.
                address = worker.macAddress.ifEmpty { worker.deviceAddress },
                status = { MemberStatusLine(worker = worker) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            VikoBlueButton(
                text = "Save Changes",
                enabled = uiState.canSave,
                onClick = onSaveChanges
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { showRemoveConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Remove Member",
                    color = VAccentRed,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    if (showChannelPicker) {
        ModalBottomSheet(
            onDismissRequest = { showChannelPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground,
            contentColor = VOnSurface,
            dragHandle = { VikoDragHandle() }
        ) {
            SelectChannelSheetContent(
                channels = uiState.channels,
                selectedChannelId = uiState.channelId,
                onSelect = { channelId ->
                    onSelectChannel(channelId)
                    showChannelPicker = false
                },
                onRequestAddChannel = { prefill -> addChannelPrefill = prefill },
                onBack = { showChannelPicker = false }
            )
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
                    onCreateChannelAndSelect(name)
                    addChannelPrefill = null
                    showChannelPicker = false
                },
                onClose = { addChannelPrefill = null }
            )
        }
    }

    if (showRemoveConfirm) {
        ModalBottomSheet(
            onDismissRequest = { showRemoveConfirm = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground,
            dragHandle = null
        ) {
            ConfirmDeleteSheetContent(
                onConfirm = {
                    onRemoveWorker()
                    showRemoveConfirm = false
                    onBack()
                },
                onDismiss = { showRemoveConfirm = false }
            )
        }
    }
}

private fun previewState(worker: Worker) = MemberDetailUiState(
    worker = worker,
    channels = listOf(previewChannel("c1", "Operations")),
    name = worker.name,
    channelId = worker.channelId
)

@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun MemberDetailScreenPreview() {
    VikoTheme {
        MemberDetailScreen(
            uiState = previewState(previewWorker(name = "Le Khanh Linh", zone = "Central Building", batteryLevel = 70)),
            onBack = {}, onNameChange = {}, onSelectChannel = {}, onCreateChannelAndSelect = {},
            onSaveChanges = {}, onRemoveWorker = {}
        )
    }
}

/** Earpiece unreachable — the status line says so instead of a stale battery. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun MemberDetailScreenOfflinePreview() {
    VikoTheme {
        MemberDetailScreen(
            uiState = previewState(previewWorker(name = "Pham Bao Anh", isOnline = false)),
            onBack = {}, onNameChange = {}, onSelectChannel = {}, onCreateChannelAndSelect = {},
            onSaveChanges = {}, onRemoveWorker = {}
        )
    }
}
