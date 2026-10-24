package com.example.viko.view.screens.team

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.components.AddPillButton
import com.example.viko.components.ScreenHeader
import com.example.viko.components.SearchField
import com.example.viko.di.AppViewModelProvider
import com.example.viko.domain.channel.model.Channel
import com.example.viko.view.screens.previewChannel
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.screens.team.components.MemberStatusLine
import com.example.viko.view.theme.VAccentBlue
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VSurfaceVariant
import com.example.viko.view.theme.VikoTheme

/**
 * The full roster, searchable and filterable by channel. Deliberately no delete
 * on a row — a member is removed from Member Details or a team sheet's edit
 * mode, where the confirmation lives.
 */
@Composable
fun ManageMemberScreen(
    onBack: () -> Unit,
    onMemberClick: (String) -> Unit,
    viewModel: ManageMemberViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ManageMemberScreen(
        uiState = uiState,
        onBack = onBack,
        onMemberClick = onMemberClick,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onChannelFilterChange = viewModel::onChannelFilterChange
    )
}

@Composable
fun ManageMemberScreen(
    uiState: ManageMemberUiState,
    onBack: () -> Unit,
    onMemberClick: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onChannelFilterChange: (String?) -> Unit
) {
    // Adding a member is a stack of bottom sheets over this screen, not a
    // separate route — see AddMemberFlow for why.
    var showAddMember by rememberSaveable { mutableStateOf(false) }

    if (showAddMember) {
        AddMemberFlow(onDismiss = { showAddMember = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(modifier = Modifier.height(16.dp))

        ScreenHeader(
            title = "Manage Member",
            onBack = onBack,
            onAdd = { showAddMember = true },
            addContentDescription = "Add Member"
        )

        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Search Member",
                modifier = Modifier.weight(1f)
            )
            ChannelFilterButton(
                channels = uiState.channels,
                selectedChannelId = uiState.filterChannelId,
                onSelect = onChannelFilterChange
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        when {
            uiState.hasNoMembers -> Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "No member yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = VOnSurfaceSecondary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                AddPillButton(text = "Add Member", onClick = { showAddMember = true })
            }

            uiState.isLoaded && uiState.members.isEmpty() -> Text(
                text = "No member found",
                style = MaterialTheme.typography.bodyLarge,
                color = VOnSurfaceSecondary.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                items(uiState.members, key = { it.worker.id }) { row ->
                    MemberRosterItem(row = row, onClick = { onMemberClick(row.worker.id) })
                }
            }
        }
    }
}

/** The funnel beside the search: narrows the roster to one channel. */
@Composable
private fun ChannelFilterButton(
    channels: List<Channel>,
    selectedChannelId: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.FilterAlt,
                contentDescription = "Filter by channel",
                // Blue while a filter is on, so a short list never looks like the
                // whole roster.
                tint = if (selectedChannelId != null) VAccentBlue else VOnSurface
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = VSurface
        ) {
            FilterMenuItem(label = "All Channels", isSelected = selectedChannelId == null) {
                onSelect(null)
                expanded = false
            }
            channels.forEach { channel ->
                FilterMenuItem(label = channel.name, isSelected = selectedChannelId == channel.id) {
                    onSelect(channel.id)
                    expanded = false
                }
            }
        }
    }
}

@Composable
private fun FilterMenuItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = VOnSurface) },
        onClick = onClick,
        trailingIcon = if (isSelected) {
            { Icon(Icons.Default.Check, contentDescription = "Selected", tint = VAccentBlue) }
        } else {
            null
        }
    )
}

@Composable
private fun MemberRosterItem(row: MemberRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VSurface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.worker.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = VOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelChip(name = row.channelName)
                Spacer(modifier = Modifier.width(8.dp))
                MemberStatusLine(worker = row.worker)
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = VOnSurfaceSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** `OPERATIONS` — the member's channel as a small outlined tag. */
@Composable
private fun ChannelChip(name: String) {
    Text(
        text = name.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = VOnSurfaceSecondary,
        maxLines = 1,
        modifier = Modifier
            .border(1.dp, VSurfaceVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ManageMemberScreenPreview() {
    VikoTheme {
        ManageMemberScreen(
            uiState = ManageMemberUiState(
                members = listOf(
                    MemberRow(previewWorker("01", "Tran Hoang Nam", "c1", zone = "Central Building", batteryLevel = 70), "Operations"),
                    MemberRow(previewWorker("02", "Nguyen Minh Tam", "c1", zone = "Central Building", batteryLevel = 10), "Operations"),
                    MemberRow(previewWorker("03", "Pham Bao Anh", "c1", isOnline = false), "Operations")
                ),
                channels = listOf(previewChannel("c1", "Operations"), previewChannel("c2", "Maintenance")),
                isLoaded = true
            ),
            onBack = {}, onMemberClick = {}, onSearchQueryChange = {}, onChannelFilterChange = {}
        )
    }
}

/** Search that matched nothing — distinct from having no members at all. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ManageMemberScreenNoResultsPreview() {
    VikoTheme {
        ManageMemberScreen(
            uiState = ManageMemberUiState(
                channels = listOf(previewChannel("c1", "Warehouse")),
                searchQuery = "zzz",
                isLoaded = true
            ),
            onBack = {}, onMemberClick = {}, onSearchQueryChange = {}, onChannelFilterChange = {}
        )
    }
}
