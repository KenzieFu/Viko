package com.example.viko.view.screens.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.components.VikoDragHandle
import com.example.viko.domain.team.model.Worker
import com.example.viko.view.screens.previewChannel
import com.example.viko.view.screens.team.AddMemberFlow
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VBrand
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/**
 * One channel's members, as a sheet over Manage Channel.
 *
 * Pairing starts from here rather than from a route of its own, so the badge
 * being added is always added to the channel the supervisor is looking at —
 * the channel is context, not another field to fill in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailSheet(
    channel: ChannelRowUiState,
    onMemberClick: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddMember by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = VBackground,
        contentColor = VOnSurface,
        dragHandle = { VikoDragHandle() }
    ) {
        ChannelDetailSheetContent(
            channel = channel,
            onSave = onDismiss,
            onAddMember = { showAddMember = true },
            onMemberClick = onMemberClick,
            onRemoveMember = onRemoveMember
        )
    }

    if (showAddMember) {
        AddMemberFlow(onDismiss = { showAddMember = false })
    }
}

@Composable
fun ChannelDetailSheetContent(
    channel: ChannelRowUiState,
    onSave: () -> Unit,
    onAddMember: () -> Unit,
    onMemberClick: (String) -> Unit,
    onRemoveMember: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = channel.channel.name,
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VBrand)
                    .border(1.dp, VBorder, RoundedCornerShape(12.dp))
                    .clickable(onClick = onSave)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            channel.members.forEach { member ->
                MemberRow(
                    member = member,
                    onClick = { onMemberClick(member.id) },
                    onRemove = { onRemoveMember(member.id) }
                )
            }

            // Wide while the channel is empty, because then it is the only
            // thing to do here; a small pill once there is a roster above it.
            val isFirstMember = channel.members.isEmpty()
            Row(
                modifier = Modifier
                    .then(if (isFirstMember) Modifier.fillMaxWidth() else Modifier)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VSurface)
                    .border(1.dp, VBorder, RoundedCornerShape(12.dp))
                    .clickable(onClick = onAddMember)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = VBrand,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (isFirstMember) "Add New Member" else "Add New",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun MemberRow(member: Worker, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VSurface)
            .border(1.dp, VBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = member.name,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove ${member.name}",
                tint = VAccentRed,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ChannelDetailSheetContentPreview() {
    VikoTheme {
        ChannelDetailSheetContent(
            channel = ChannelRowUiState(previewChannel("c1", "Operations"), emptyList()),
            onSave = {},
            onAddMember = {},
            onMemberClick = {},
            onRemoveMember = {}
        )
    }
}
