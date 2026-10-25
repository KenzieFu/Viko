package com.example.viko.view.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.viko.components.VikoDragHandle
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.view.screens.team.NearbyDeviceSheetContent
import com.example.viko.view.screens.team.SetupMemberScreenContent
import com.example.viko.view.theme.*

/** Replay has a visibly labelled demo badge and never opens the Bluetooth scanner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplayMemberFlow(channelName: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var setup by rememberSaveable { mutableStateOf(false) }
    var found by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var zone by rememberSaveable { mutableStateOf("") }
    val demo = NearbyDevice("demo", "ViKo Demo (replay)", "Demo device", -45)
    if (!setup) {
        ModalBottomSheet(onDismissRequest = onClose,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground, contentColor = VOnSurface,
            scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = .5f),
            dragHandle = { VikoDragHandle() }) {
            NearbyDeviceSheetContent(if (found) listOf(demo) else emptyList(), false, true, true, { false }, 0,
                { setup = true }, { found = true }, onClose, onClose,
                replay = true)
        }
    } else {
        Dialog(onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Column(Modifier.fillMaxSize().background(VBackground).statusBarsPadding().imePadding()) {
                SetupMemberScreenContent(demo, name, { name = it }, zone, { zone = it }, true, {}, channelName,
                    true, name.isNotBlank(), false, null, {}, { onSave(name) }, onClose)
            }
        }
    }
}

@Preview
@Composable
private fun ReplayMemberFlowPreview() { VikoTheme { ReplayMemberFlow("Operations", {}, {}) } }
