package com.example.viko.view.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.R
import com.example.viko.components.VikoBlueButton
import com.example.viko.components.VikoDragHandle
import com.example.viko.di.AppViewModelProvider
import com.example.viko.view.screens.previewChannel
import com.example.viko.view.screens.previewChannelSummary
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.screens.team.AddMemberFlow
import com.example.viko.view.screens.home.components.AnnouncementSheetContent
import com.example.viko.view.screens.home.components.ChannelAudioMode
import com.example.viko.view.screens.home.components.ChannelCard
import com.example.viko.view.screens.home.components.SosSheetContent
import com.example.viko.view.screens.home.components.TeamSheetContent
import com.example.viko.view.theme.VAccentBlue
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VShadow
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/**
 * The manager's dashboard: one card per channel, an SOS broadcast, and a
 * site-wide announcement. Everything it shows comes from [HomeViewModel];
 * which sheet is open is view state and stays here.
 *
 * This overload only resolves the ViewModel and delegates to the stateless one
 * below — a @Preview has no Application or database, so it cannot build a
 * HomeViewModel, and all the layout has to live where a preview can reach it.
 */
@Composable
fun HomeScreen(
    onOpenNotifications: () -> Unit,
    onOpenManageMembers: () -> Unit,
    onDirectCall: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onOpenNotifications = onOpenNotifications,
        onOpenManageMembers = onOpenManageMembers,
        onDirectCall = onDirectCall,
        onRenameWorker = viewModel::renameWorker,
        onRemoveWorker = viewModel::removeWorker,
        onAssignWorkerToChannel = viewModel::assignWorkerToChannel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenNotifications: () -> Unit,
    onOpenManageMembers: () -> Unit,
    onDirectCall: (String) -> Unit,
    onRenameWorker: (String, String) -> Unit,
    onRemoveWorker: (String) -> Unit,
    onAssignWorkerToChannel: (String, String) -> Unit
) {
    // Which sheet is open is view state, but it still has to survive an Activity
    // recreation — a rotation should not close the roster the supervisor opened.
    var showSosSheet by rememberSaveable { mutableStateOf(false) }
    var showAnnouncementSheet by rememberSaveable { mutableStateOf(false) }
    var openTeamSheetChannelId by rememberSaveable { mutableStateOf<String?>(null) }

    // Talking is deliberately NOT saveable: it stands in for holding the mic, and
    // that ends when the screen goes away.
    var talkingChannelId by remember { mutableStateOf<String?>(null) }

    // First run: nothing is paired, so pairing IS the first step. The sheet opens
    // over an empty Home, which is what the design shows — Home is the result of
    // onboarding, not a screen you land on with a factory already configured.
    // Saveable, and offered only once: with plain remember an Activity recreation
    // reset the flag and onboarding re-opened over whatever the supervisor was
    // doing.
    var showAddMember by rememberSaveable { mutableStateOf(false) }
    var onboardingOffered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.needsOnboarding) {
        if (uiState.needsOnboarding && !onboardingOffered) {
            onboardingOffered = true
            showAddMember = true
        }
    }
    if (showAddMember) {
        AddMemberFlow(onDismiss = { showAddMember = false })
    }

    Box(modifier = Modifier.fillMaxSize().background(VBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            HomeTopBar(
                activeAlertCount = uiState.activeAlertCount,
                onSosTap = { showSosSheet = true },
                onNotificationsTap = onOpenNotifications,
                onSettingsTap = onOpenManageMembers
            )

            if (uiState.hasNoChannels) {
                // Nothing paired yet. No phantom channels — the grid stays empty
                // until onboarding creates real ones.
                NoTeamYet(
                    onPairDevice = { showAddMember = true },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.channels, key = { it.channel.id }) { summary ->
                        val isManagerTalking = talkingChannelId == summary.channel.id
                        ChannelCard(
                            summary = summary,
                            audioMode = when {
                                isManagerTalking -> ChannelAudioMode.Talking
                                summary.hasSpeakingMember -> ChannelAudioMode.Listening
                                else -> ChannelAudioMode.Idle
                            },
                            onMicTap = {
                                talkingChannelId = if (isManagerTalking) null else summary.channel.id
                            },
                            onChipTap = { openTeamSheetChannelId = summary.channel.id }
                        )
                    }
                }
            }

            MakeAnnouncementButton(onClick = { showAnnouncementSheet = true })
        }
    }

    if (showSosSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSosSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VSurface,
            dragHandle = { VikoDragHandle() }
        ) {
            SosSheetContent(onClose = { showSosSheet = false })
        }
    }

    if (showAnnouncementSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAnnouncementSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            // The sheet recolours itself while speaking, so it owns its background.
            containerColor = Color.Transparent,
            dragHandle = null
        ) {
            AnnouncementSheetContent(onClose = { showAnnouncementSheet = false })
        }
    }

    // Resolved against the latest state so the sheet follows edits made inside it.
    val teamSummary = openTeamSheetChannelId?.let { id ->
        uiState.channels.find { it.channel.id == id }
    }
    if (teamSummary != null) {
        ModalBottomSheet(
            onDismissRequest = { openTeamSheetChannelId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VSurface,
            dragHandle = { VikoDragHandle() }
        ) {
            TeamSheetContent(
                summary = teamSummary,
                assignableWorkers = uiState.unassignedToChannel[teamSummary.channel.id].orEmpty(),
                onClose = { openTeamSheetChannelId = null },
                onDirectCall = { workerId ->
                    openTeamSheetChannelId = null
                    onDirectCall(workerId)
                },
                onRenameWorker = onRenameWorker,
                onRemoveWorker = onRemoveWorker,
                onAssignWorker = { workerId ->
                    onAssignWorkerToChannel(workerId, teamSummary.channel.id)
                }
            )
        }
    }
}

@Composable
private fun NoTeamYet(onPairDevice: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No team yet",
            style = MaterialTheme.typography.headlineSmall,
            color = VOnSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pair an earpiece to add your first member.",
            style = MaterialTheme.typography.bodyMedium,
            color = VOnSurfaceSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        VikoBlueButton(text = "Pair Device", onClick = onPairDevice)
    }
}

@Composable
private fun HomeTopBar(
    activeAlertCount: Int,
    onSosTap: () -> Unit,
    onNotificationsTap: () -> Unit,
    onSettingsTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 44.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .shadow(2.dp, RoundedCornerShape(14.dp), spotColor = VShadow)
                .clip(RoundedCornerShape(14.dp))
                .background(VSurface)
                .clickable(onClick = onSosTap)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_sos),
                contentDescription = "SOS Emergency Button",
                tint = VAccentRed,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "SOS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = VAccentRed
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box {
            IconButton(onClick = onNotificationsTap) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_bell),
                    contentDescription = "Notification Button",
                    tint = VOnSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            if (activeAlertCount > 0) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 22.dp)
                        .clip(CircleShape)
                        .background(VAccentBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$activeAlertCount",
                        fontSize = 9.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        IconButton(onClick = onSettingsTap) {
            Icon(Icons.Outlined.Settings, contentDescription = "Manage members", tint = VOnSurface)
        }
    }
}

@Composable
private fun MakeAnnouncementButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(VBackground)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = VShadow)
                .clip(RoundedCornerShape(16.dp))
                .background(VSurface)
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_announce),
                contentDescription = null,
                tint = VOnSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Make Announcement",
                style = MaterialTheme.typography.titleLarge,
                color = VOnSurface
            )
        }
    }
}

/** The populated dashboard: two channels, one of them with a member talking. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun HomeScreenPreview() {
    VikoTheme {
        HomeScreen(
            uiState = HomeUiState(
                channels = listOf(
                    previewChannelSummary("c1", "Warehouse"),
                    ChannelSummary(
                        channel = previewChannel("c2", "Assembly"),
                        members = listOf(previewWorker("04", "Rina", "c2", batteryLevel = 15))
                    )
                ),
                activeAlertCount = 2,
                hasMembers = true,
                isLoaded = true
            ),
            onOpenNotifications = {},
            onOpenManageMembers = {},
            onDirectCall = {},
            onRenameWorker = { _, _ -> },
            onRemoveWorker = {},
            onAssignWorkerToChannel = { _, _ -> }
        )
    }
}

/** Loaded, but nothing paired yet — the empty state, hard to reach in a running app. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun HomeScreenEmptyPreview() {
    VikoTheme {
        HomeScreen(
            uiState = HomeUiState(hasMembers = true, isLoaded = true),
            onOpenNotifications = {},
            onOpenManageMembers = {},
            onDirectCall = {},
            onRenameWorker = { _, _ -> },
            onRemoveWorker = {},
            onAssignWorkerToChannel = { _, _ -> }
        )
    }
}
