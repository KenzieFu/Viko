package com.example.viko.view.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.R
import com.example.viko.components.*
import com.example.viko.di.AppViewModelProvider
import com.example.viko.view.screens.team.AddMemberFlow
import com.example.viko.view.theme.*

@Composable
fun OnboardingFlow(
    onFinished: () -> Unit,
    replay: Boolean = false,
    viewModel: OnboardingViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    LaunchedEffect(replay) { viewModel.setReplay(replay) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pairing by rememberSaveable { mutableStateOf(false) }
    OnboardingFlowContent(state, viewModel::nameChanged, viewModel::createChannel,
        viewModel::openMembers, { pairing = true }, viewModel::saveRoster, viewModel::removeMember, onFinished)
    BackHandler(state.stage == OnboardingStage.Members && !pairing) { viewModel.backToChannels() }
    if (pairing) {
        if (replay) ReplayMemberFlow(state.channelName, { viewModel.addDemoMember(it); pairing = false }, { pairing = false })
        else AddMemberFlow(onDismiss = { pairing = false }, presetChannelId = state.channelId, closeAfterSave = true)
    }
}

/** Shared real/replay UI: the host chooses persistence and pairing behavior. */
@Composable
internal fun OnboardingFlowContent(
    state: OnboardingUiState,
    onName: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenChannel: () -> Unit,
    onAddMember: () -> Unit,
    onSaveRoster: () -> Unit,
    onRemove: (String) -> Unit,
    onFinished: () -> Unit
) {
    if (state.stage == OnboardingStage.CreateChannel) {
        Box {
            FirstChannelScreen(FirstChannelUiState(name = state.channelName, isSaving = state.saving), onName, onCreate)
            state.error?.let { Text(it, color = VWarning, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp)) }
        }
        return
    }
    var target by remember(state.stage) { mutableStateOf<Rect?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val roster = state.stage == OnboardingStage.Members
    val highlight = state.stage == OnboardingStage.ManageChannel || (roster && state.members.isEmpty())
    Box(Modifier.fillMaxSize().background(VBackground)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (roster) state.channelName else "Manage Channel", fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold, color = VOnSurface, modifier = Modifier.weight(1f))
                if (roster) {
                    VikoBlueButton("Save", onSaveRoster, Modifier.width(84.dp), enabled = state.members.isNotEmpty())
                } else {
                    // During guided setup the first channel is the only destination.
                    IconButton(onClick = onOpenChannel, modifier = Modifier.size(width = 64.dp, height = 56.dp)) {
                        Box(Modifier.size(32.dp).background(VBrand, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, "Open team channel", tint = VOnSurface, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!roster) {
                    VikoListRow(state.channelName, Modifier.onGloballyPositioned { target = it.boundsInRoot() }, onOpenChannel) {
                        Text("${state.members.size}", fontSize = 12.sp, color = VOnSurfaceSecondary)
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Default.ChevronRight, null, tint = VOnSurfaceSecondary, modifier = Modifier.size(16.dp))
                    }
                } else {
                    state.members.forEach { member ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VikoListRow(member.name, Modifier.weight(1f))
                            IconButton(onClick = { deleting = member.id }) {
                                Icon(painterResource(R.drawable.ic_trash), "Remove ${member.name}", tint = VAccentRed, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    AddPillButton(if (state.members.isEmpty()) "Add New Member" else "Add New", onAddMember,
                        Modifier.onGloballyPositioned { target = it.boundsInRoot() })
                }
                state.error?.let { Text(it, color = VWarning, fontSize = 14.sp) }
            }
        }
        if (highlight) OnboardingSpotlight(target,
            if (roster) "Click to add\nnew member" else "Go to your\nteam channel",
            if (roster) onAddMember else onOpenChannel)
        if (state.stage == OnboardingStage.TeamReady) TeamReadyOverlay(onFinished)
    }
    deleting?.let { id ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Remove member?") },
            text = { Text("Remove this member from the app roster?") },
            confirmButton = { TextButton(onClick = { onRemove(id); deleting = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
private fun OnboardingFlowPreview() {
    VikoTheme { OnboardingFlowContent(OnboardingUiState(OnboardingStage.ManageChannel, "Operations"), {}, {}, {}, {}, {}, {}, {}) }
}
