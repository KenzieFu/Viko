package com.example.viko.view.screens.channel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.R
import com.example.viko.components.VikoDragHandle
import com.example.viko.di.AppViewModelProvider
import com.example.viko.view.screens.onboarding.TeamReadyOverlay
import com.example.viko.view.screens.previewChannel
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VBrandOnDark
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/**
 * The roster of team channels — the screen onboarding lands on, and the way
 * into a channel's members.
 *
 * Members are not listed here: a channel opens its own sheet
 * ([ChannelDetailSheet]) so that pairing a badge happens over the list it will
 * be added to, rather than on a route of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageChannelScreen(
    onBack: (() -> Unit)?,
    onMemberClick: (String) -> Unit,
    showTeamReady: Boolean = false,
    onGoToHomepage: () -> Unit = {},
    viewModel: ManageChannelViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var openChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddChannel by rememberSaveable { mutableStateOf(false) }

    ManageChannelScreen(
        uiState = uiState,
        onBack = onBack,
        onAddChannel = { showAddChannel = true },
        onChannelClick = { openChannelId = it }
    )

    // Resolved against the latest state so the sheet follows members being
    // paired in underneath it.
    val openChannel = openChannelId?.let { id -> uiState.channels.find { it.channel.id == id } }
    if (openChannel != null) {
        ChannelDetailSheet(
            channel = openChannel,
            onMemberClick = onMemberClick,
            onRemoveMember = viewModel::removeMember,
            onDismiss = { openChannelId = null }
        )
    }

    // Last beat of onboarding: the channel the supervisor just filled is behind
    // the card, which is the whole point of showing it here and not on Home.
    if (showTeamReady) {
        TeamReadyOverlay(onGoToHomepage = onGoToHomepage)
    }

    if (showAddChannel) {
        ModalBottomSheet(
            onDismissRequest = { showAddChannel = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground,
            contentColor = VOnSurface,
            dragHandle = { VikoDragHandle() }
        ) {
            AddNewChannelSheetContent(
                onCreate = { name ->
                    viewModel.createChannel(name)
                    showAddChannel = false
                },
                onClose = { showAddChannel = false }
            )
        }
    }
}

@Composable
fun ManageChannelScreen(
    uiState: ManageChannelUiState,
    onBack: (() -> Unit)?,
    onAddChannel: () -> Unit,
    onChannelClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(modifier = Modifier.height(21.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Onboarding arrives here with nothing to go back to, so the arrow
            // only exists when there is somewhere to return to.
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = VOnSurface
                    )
                }
            }
            Text(
                text = "Manage Channel",
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(VBrandOnDark)
                    .clickable(onClick = onAddChannel),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add channel",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.channels, key = { it.channel.id }) { row ->
                ChannelListRow(row = row, onClick = { onChannelClick(row.channel.id) })
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ChannelListRow(row: ChannelRowUiState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = row.channel.name,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${row.memberCount}",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
        Image(
            painter = painterResource(id = R.drawable.ic_chevron_right_small),
            contentDescription = null,
            modifier = Modifier.size(width = 6.dp, height = 10.dp)
        )
    }
}

@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ManageChannelScreenPreview() {
    VikoTheme {
        ManageChannelScreen(
            uiState = ManageChannelUiState(
                channels = listOf(
                    ChannelRowUiState(previewChannel("c1", "Operations"), emptyList())
                ),
                isLoaded = true
            ),
            onBack = null,
            onAddChannel = {},
            onChannelClick = {}
        )
    }
}
