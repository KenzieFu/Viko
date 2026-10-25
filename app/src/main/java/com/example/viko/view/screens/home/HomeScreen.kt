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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.viko.view.screens.home.tutorial.TutorialOverlay
import com.example.viko.view.screens.home.tutorial.TutorialStep
import com.example.viko.view.screens.home.tutorial.TutorialTargets
import com.example.viko.view.screens.onboarding.ReadyToGoOverlay
import kotlinx.coroutines.delay
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.view.screens.previewChannel
import com.example.viko.view.screens.previewChannelSummary
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.screens.team.AddMemberFlow
import com.example.viko.view.screens.home.components.AnnouncementSheetContent
import com.example.viko.view.screens.home.components.ChannelAudioMode
import com.example.viko.view.screens.home.components.ChannelCard
import com.example.viko.view.screens.home.components.SosSheetContent
import com.example.viko.view.screens.home.components.TeamSheetContent
import com.example.viko.view.screens.notifications.components.AlertCard
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
    onOpenSettings: () -> Unit,
    onDirectCall: (String) -> Unit,
    startTutorial: Boolean = false,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        startTutorial = startTutorial,
        onOpenNotifications = onOpenNotifications,
        onOpenSettings = onOpenSettings,
        onDirectCall = onDirectCall,
        onRenameWorker = viewModel::renameWorker,
        onRemoveWorker = viewModel::removeWorker,
        onAssignWorkerToChannel = viewModel::assignWorkerToChannel,
        onResolveAlert = viewModel::resolveAlert
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onDirectCall: (String) -> Unit,
    onRenameWorker: (String, String) -> Unit,
    onRemoveWorker: (String) -> Unit,
    onAssignWorkerToChannel: (String, String) -> Unit,
    onResolveAlert: (String) -> Unit,
    startTutorial: Boolean = false
) {
    // Which sheet is open is view state, but it still has to survive an Activity
    // recreation — a rotation should not close the roster the supervisor opened.
    var showSosSheet by rememberSaveable { mutableStateOf(false) }
    var showAnnouncementSheet by rememberSaveable { mutableStateOf(false) }
    var openTeamSheetChannelId by rememberSaveable { mutableStateOf<String?>(null) }

    // Talking is deliberately NOT saveable: it stands in for holding the mic, and
    // that ends when the screen goes away.
    var talkingChannelId by remember { mutableStateOf<String?>(null) }

    var showAddMember by rememberSaveable { mutableStateOf(false) }
    // Set when pairing was started from a team sheet's Add Member: the new
    // earpiece joins that channel rather than asking which one.
    var addMemberChannelId by rememberSaveable { mutableStateOf<String?>(null) }

    // First run is no longer Home's job: the splash routes a fresh install to
    // onboarding, which names a channel and lands on Manage Channel. Home
    // opening its own pairing sheet here would stack a second one underneath
    // that. What Home does own is the tour, handed over by route argument.
    var introStage by rememberSaveable(startTutorial) {
        mutableStateOf(if (startTutorial) IntroStage.ReadyToGo else IntroStage.None)
    }
    var tutorialStep by rememberSaveable { mutableStateOf(TutorialStep.PressAndHold) }
    var targets by remember { mutableStateOf(TutorialTargets()) }

    // The design note: the SOS shows for a few seconds, then Notifications owns
    // it. Dismissing only hides the banner — the alert stays active.
    var dismissedAlertId by rememberSaveable { mutableStateOf<String?>(null) }
    val bannerAlert = uiState.activeAlert?.takeIf { it.id != dismissedAlertId }
    LaunchedEffect(bannerAlert?.id) {
        val id = bannerAlert?.id ?: return@LaunchedEffect
        delay(ALERT_BANNER_MS)
        dismissedAlertId = id
    }
    if (showAddMember) {
        AddMemberFlow(
            onDismiss = {
                showAddMember = false
                addMemberChannelId = null
            },
            presetChannelId = addMemberChannelId
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(VBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            HomeTopBar(
                activeAlertCount = uiState.activeAlertCount,
                onSosTap = { showSosSheet = true },
                onNotificationsTap = onOpenNotifications,
                onSettingsTap = onOpenSettings,
                onSosBounds = { targets = targets.copy(sos = it) }
            )

            // Home — Notified: a member's SOS sits above the grid until someone
            // calls them or resolves it, so it cannot scroll out of sight.
            bannerAlert?.let { alert ->
                AlertCard(
                    alert = alert,
                    onDirectCall = { onDirectCall(alert.workerId) },
                    onResolve = {
                        dismissedAlertId = alert.id
                        onResolveAlert(alert.id)
                    },
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp)
                        .clickable(onClick = onOpenNotifications)
                )
            }

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
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(uiState.channels, key = { _, it -> it.channel.id }) { index, summary ->
                        val isManagerTalking = talkingChannelId == summary.channel.id
                        val isTourTarget = index == 0
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
                            onChipTap = { openTeamSheetChannelId = summary.channel.id },
                            // One mic, one channel: while a channel is held, every
                            // other card goes inert so the only tap available is
                            // letting go of the one you are on.
                            enabled = talkingChannelId == null || isManagerTalking,
                            onMicBounds = { bounds ->
                                if (isTourTarget) targets = targets.copy(channelMic = bounds)
                            },
                            onChipBounds = { bounds ->
                                if (isTourTarget) targets = targets.copy(channelChip = bounds)
                            },
                            onCardBounds = { bounds ->
                                if (isTourTarget) targets = targets.copy(channelCard = bounds)
                            }
                        )
                    }
                }
            }

            MakeAnnouncementButton(
                onClick = { showAnnouncementSheet = true },
                modifier = Modifier.onGloballyPositioned {
                    targets = targets.copy(announcement = it.boundsInRoot())
                }
            )
        }
    }

    if (showSosSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSosSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground,
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
            onDismissRequest = {
                openTeamSheetChannelId = null
                if (introStage == IntroStage.Tutorial && tutorialStep == TutorialStep.DirectCall) {
                    introStage = IntroStage.None
                }
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground,
            dragHandle = { VikoDragHandle() }
        ) {
            TeamSheetContent(
                summary = teamSummary,
                assignableWorkers = uiState.unassignedToChannel[teamSummary.channel.id].orEmpty(),
                onDirectCall = { workerId ->
                    openTeamSheetChannelId = null
                    onDirectCall(workerId)
                },
                onRenameWorker = onRenameWorker,
                onRemoveWorker = onRemoveWorker,
                onAssignWorker = { workerId ->
                    onAssignWorkerToChannel(workerId, teamSummary.channel.id)
                },
                onPairNewMember = {
                    addMemberChannelId = teamSummary.channel.id
                    openTeamSheetChannelId = null
                    showAddMember = true
                },
                showDirectCallTutorial = introStage == IntroStage.Tutorial && tutorialStep == TutorialStep.DirectCall,
                onFinishTutorial = {
                    openTeamSheetChannelId = null
                    introStage = IntroStage.None
                }
            )
        }
    }

    when (introStage) {
        IntroStage.None -> Unit

        IntroStage.ReadyToGo -> ReadyToGoOverlay(
            onStartTutorial = {
                tutorialStep = TutorialStep.PressAndHold
                introStage = IntroStage.Tutorial
            },
            onSkip = { introStage = IntroStage.None }
        )

        IntroStage.Tutorial -> if (tutorialStep != TutorialStep.DirectCall || teamSummary == null) {
            TutorialOverlay(
                step = tutorialStep,
                bounds = targets.boundsFor(tutorialStep),
                cursorBounds = if (tutorialStep == TutorialStep.PressAndHold) targets.channelMic else targets.boundsFor(tutorialStep),
                onNext = {
                    val next = tutorialStep.next()
                    if (next == null) {
                        openTeamSheetChannelId = null
                        introStage = IntroStage.None
                    } else {
                        // The final spotlight is rendered inside the team sheet.
                        if (next == TutorialStep.DirectCall) {
                            openTeamSheetChannelId = uiState.channels.firstOrNull()?.channel?.id
                        }
                        tutorialStep = next
                    }
                },
                onSkipAll = {
                    openTeamSheetChannelId = null
                    introStage = IntroStage.None
                }
            )
        }
    }
}

/** Which first-run card, if any, is sitting over Home. */
private enum class IntroStage { None, ReadyToGo, Tutorial }

/** How long the SOS banner holds the screen before Notifications owns it. */
private const val ALERT_BANNER_MS = 5_000L

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
    onSettingsTap: () -> Unit,
    onSosBounds: (Rect) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .onGloballyPositioned { onSosBounds(it.boundsInRoot()) }
                .shadow(2.dp, RoundedCornerShape(14.dp), spotColor = VShadow)
                .clip(RoundedCornerShape(14.dp))
                .background(VSurface)
                .clickable(onClick = onSosTap)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_sos),
                contentDescription = "SOS Emergency Button",
                tint = VAccentRed,
                modifier = Modifier.size(20.dp)
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
            IconButton(onClick = onNotificationsTap, modifier = Modifier.size(20.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_bell),
                    contentDescription = "Notification Button",
                    tint = VOnSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (activeAlertCount > 0) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
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

        Spacer(Modifier.width(16.dp))
        IconButton(onClick = onSettingsTap, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = VOnSurface,
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MakeAnnouncementButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(VBackground)
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
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
                painter = painterResource(id = R.drawable.ic_announces),
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
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
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
            onOpenSettings = {},
            onDirectCall = {},
            onRenameWorker = { _, _ -> },
            onRemoveWorker = {},
            onAssignWorkerToChannel = { _, _ -> },
            onResolveAlert = {}
        )
    }
}

/** Home — Notified: a member's SOS pinned above the grid. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun HomeScreenNotifiedPreview() {
    VikoTheme {
        HomeScreen(
            uiState = HomeUiState(
                channels = listOf(previewChannelSummary("c1", "Operations")),
                activeAlertCount = 1,
                activeAlert = EmergencyAlert(
                    id = "a1",
                    workerId = "01",
                    workerName = "Minh Luat Nguyen",
                    zone = "Zone 3",
                    team = "Operations Team",
                    timeAgo = "1m ago"
                ),
                hasMembers = true,
                isLoaded = true
            ),
            onOpenNotifications = {},
            onOpenSettings = {},
            onDirectCall = {},
            onRenameWorker = { _, _ -> },
            onRemoveWorker = {},
            onAssignWorkerToChannel = { _, _ -> },
            onResolveAlert = {}
        )
    }
}

/** Loaded, but nothing paired yet — the empty state, hard to reach in a running app. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun HomeScreenEmptyPreview() {
    VikoTheme {
        HomeScreen(
            uiState = HomeUiState(hasMembers = true, isLoaded = true),
            onOpenNotifications = {},
            onOpenSettings = {},
            onDirectCall = {},
            onRenameWorker = { _, _ -> },
            onRemoveWorker = {},
            onAssignWorkerToChannel = { _, _ -> },
            onResolveAlert = {}
        )
    }
}
