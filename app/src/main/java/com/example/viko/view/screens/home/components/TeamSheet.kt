package com.example.viko.view.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.components.AddPillButton
import com.example.viko.components.AnnouncementWaveBackground
import com.example.viko.components.AnnouncementWaveHeight
import com.example.viko.components.ConfirmDeleteSheetContent
import com.example.viko.components.VikoDragHandle
import com.example.viko.domain.team.model.Worker
import com.example.viko.view.screens.home.ChannelSummary
import com.example.viko.view.screens.previewChannelSummary
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.screens.team.components.EditableMemberListItem
import com.example.viko.view.screens.team.components.MemberListItem
import com.example.viko.view.screens.team.components.MemberPickerSheetContent
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VButtonBlue
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/**
 * The roster behind a channel card: who is on this channel, call one of them,
 * talk to all of them, or switch to edit mode to rename, delete and pull in
 * members from elsewhere.
 *
 * Renames are held as drafts until **Save** — typing into a row does not touch
 * the database, so backing out of edit mode by closing the sheet loses nothing
 * that was not meant to be kept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamSheetContent(
    summary: ChannelSummary,
    assignableWorkers: List<Worker>,
    onDirectCall: (String) -> Unit,
    onRenameWorker: (workerId: String, name: String) -> Unit,
    onRemoveWorker: (String) -> Unit,
    onAssignWorker: (String) -> Unit,
    onPairNewMember: () -> Unit
) {
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    var isPickingMember by rememberSaveable { mutableStateOf(false) }
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    // Not saveable: it stands in for holding the mic, which ends with the sheet.
    var isTalking by remember { mutableStateOf(false) }
    val draftNames = remember { mutableStateMapOf<String, String>() }

    // A member holding their own button has the channel — no talking over them,
    // and a press that was live when they cut in ends there.
    val isMemberSpeaking = summary.hasSpeakingMember
    val isTeamTalking = isTalking && !isMemberSpeaking

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        if (isTeamTalking) {
            AnnouncementWaveBackground(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(AnnouncementWaveHeight)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            TeamSheetHeader(
                title = if (isEditMode) {
                    summary.channel.name
                } else {
                    "${summary.channel.name} (${summary.onlineCount}/${summary.totalCount})"
                },
                isEditMode = isEditMode,
                onEdit = {
                    draftNames.clear()
                    isEditMode = true
                },
                onSave = {
                    summary.members.forEach { worker ->
                        val draft = draftNames[worker.id]?.trim()
                        if (!draft.isNullOrEmpty() && draft != worker.name) onRenameWorker(worker.id, draft)
                    }
                    draftNames.clear()
                    isEditMode = false
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isEditMode) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(summary.members.sortedBy { it.name }, key = { it.id }) { worker ->
                        EditableMemberListItem(
                            name = draftNames[worker.id] ?: worker.name,
                            onNameChange = { draftNames[worker.id] = it },
                            onDelete = { deleteTargetId = worker.id }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        AddPillButton(text = "Add Member", onClick = { isPickingMember = true })
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reachable members first: the top of the list is who you can
                    // actually talk to right now.
                    items(summary.members.sortedByDescending { it.isOnline }, key = { it.id }) { worker ->
                        MemberListItem(worker = worker, onDirectCall = { onDirectCall(worker.id) })
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TalkToTeamButton(
                    isTalking = isTeamTalking,
                    enabled = summary.isActive && !isMemberSpeaking,
                    onToggle = { isTalking = !isTeamTalking }
                )
            }
        }
    }

    if (isPickingMember) {
        ModalBottomSheet(
            onDismissRequest = { isPickingMember = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground,
            dragHandle = { VikoDragHandle() }
        ) {
            MemberPickerSheetContent(
                workers = assignableWorkers,
                onPick = { workerId ->
                    onAssignWorker(workerId)
                    isPickingMember = false
                },
                onPairNew = {
                    isPickingMember = false
                    onPairNewMember()
                },
                onBack = { isPickingMember = false }
            )
        }
    }

    deleteTargetId?.let { workerId ->
        ModalBottomSheet(
            onDismissRequest = { deleteTargetId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground,
            dragHandle = null
        ) {
            ConfirmDeleteSheetContent(
                onConfirm = {
                    onRemoveWorker(workerId)
                    draftNames.remove(workerId)
                    deleteTargetId = null
                },
                onDismiss = { deleteTargetId = null }
            )
        }
    }
}

/** `Operations (3/5)  ✎`, or `Operations  [Save]` while editing. */
@Composable
private fun TeamSheetHeader(
    title: String,
    isEditMode: Boolean,
    onEdit: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = VOnSurface,
            modifier = Modifier.weight(1f)
        )
        if (isEditMode) {
            Button(
                onClick = onSave,
                modifier = Modifier.padding(end = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VMicBlue, contentColor = Color.White)
            ) {
                Text("Save", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        } else {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit team", tint = VOnSurface)
            }
        }
    }
}

/**
 * Dark card with a mic while idle; bright blue *Speaking...* while held. Fades
 * out and stops responding while a member has the channel, or nobody is on it.
 */
@Composable
private fun TalkToTeamButton(isTalking: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(56.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(if (isTalking) VButtonBlue else VSurface)
            .then(if (isTalking) Modifier else Modifier.border(1.dp, VBorder, shape))
            .clickable(enabled = enabled, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (isTalking) "Speaking..." else "Talk to Team",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}

private const val DISABLED_ALPHA = 0.35f

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20, heightDp = 760)
@Composable
private fun TeamSheetContentPreview() {
    VikoTheme {
        TeamSheetContent(
            summary = previewChannelSummary(),
            assignableWorkers = listOf(previewWorker("09", "Dewi", "")),
            onDirectCall = {},
            onRenameWorker = { _, _ -> },
            onRemoveWorker = {},
            onAssignWorker = {},
            onPairNewMember = {}
        )
    }
}
