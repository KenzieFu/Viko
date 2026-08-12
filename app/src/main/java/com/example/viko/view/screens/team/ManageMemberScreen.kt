package com.example.viko.view.screens.team

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.components.MemberAvatar
import com.example.viko.components.StatusDot
import com.example.viko.components.VikoTextField
import com.example.viko.di.AppViewModelProvider
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VSurface

/** The full roster, searchable and filterable, with add and delete. */
@Composable
fun ManageMemberScreen(
    onBack: () -> Unit,
    onMemberClick: (String) -> Unit,
    viewModel: ManageMemberViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Saveable, so a rotation does not dismiss the dialog or the sheet. The id,
    // not the Worker — a data class is not saveable.
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }

    // Adding a member is a stack of bottom sheets over this screen, not a
    // separate route — see AddMemberFlow for why.
    var showAddMember by rememberSaveable { mutableStateOf(false) }

    if (showAddMember) {
        AddMemberFlow(onDismiss = { showAddMember = false })
    }

    if (deleteTargetId != null) {
        DeleteMemberDialog(
            onConfirm = {
                deleteTargetId?.let(viewModel::removeWorker)
                deleteTargetId = null
            },
            onDismiss = { deleteTargetId = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VOnSurface)
            }
            Text(
                text = "Manage Member",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f)
            )
            // Blue circle, white glyph — the same control as the one in Select
            // Channel. It was a VOnSurface circle with a VOnPrimary icon, which
            // the dark palette turned into near-white on near-white.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(VMicBlue)
                    .clickable { showAddMember = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    "Add Member",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VSurface)
                .border(1.dp, VBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = VOnSurfaceSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            VikoTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = "Search Member",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Channel filter chips must scroll: with four channels the last one ran
        // off the right edge and rendered as a column of single letters.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.filterChannelId == null,
                onClick = { viewModel.onChannelFilterChange(null) },
                label = { Text("All", fontSize = 12.sp) },
                shape = RoundedCornerShape(8.dp)
            )
            uiState.channels.forEach { channel ->
                FilterChip(
                    selected = uiState.filterChannelId == channel.id,
                    onClick = {
                        viewModel.onChannelFilterChange(
                            channel.id.takeUnless { it == uiState.filterChannelId }
                        )
                    },
                    label = { Text(channel.name, fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = VBorder)

        if (uiState.members.isEmpty()) {
            EmptyRoster(onAddMember = { showAddMember = true })
        } else {
            LazyColumn {
                items(uiState.members, key = { it.worker.id }) { row ->
                    MemberRosterItem(
                        row = row,
                        onClick = { onMemberClick(row.worker.id) },
                        onDelete = { deleteTargetId = row.worker.id }
                    )
                    HorizontalDivider(color = VBorder, modifier = Modifier.padding(start = 80.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddMember = true },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, VBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VOnSurface)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Member")
                        }
                    }
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun MemberRosterItem(
    row: MemberRow,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MemberAvatar(name = row.worker.name, size = 48.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.worker.name,
                style = MaterialTheme.typography.titleMedium,
                color = VOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusDot(isOnline = row.worker.isOnline)
                Text(
                    text = "${row.channelName} • ${row.worker.zone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VOnSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete ${row.worker.name}",
                tint = VOnSurfaceSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyRoster(onAddMember: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Person, null, tint = VOnSurfaceSecondary, modifier = Modifier.size(48.dp))
            Text("No members found", color = VOnSurfaceSecondary)
            OutlinedButton(
                onClick = onAddMember,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VBorder)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Member", color = VOnSurface)
            }
        }
    }
}

@Composable
private fun DeleteMemberDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VSurface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Delete member?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "Your paired devices data will be deleted and has to setup a new one later.",
                style = MaterialTheme.typography.bodyMedium,
                color = VOnSurfaceSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = VAccentRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VBorder)
            ) {
                Text("Cancel", color = VOnSurface)
            }
        }
    )
}
