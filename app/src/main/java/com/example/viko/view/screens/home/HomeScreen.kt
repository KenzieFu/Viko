package com.example.viko.view.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.viko.R
import com.example.viko.components.VikoDragHandle
import com.example.viko.mock.AppState
import com.example.viko.navigation.Screen
import com.example.viko.type.WorkerStatus
import com.example.viko.view.screens.home.components.AnnouncementSheetContent
import com.example.viko.view.screens.home.components.ChannelAudioMode
import com.example.viko.view.screens.home.components.ChannelCard
import com.example.viko.view.screens.home.components.HomeTopBar
import com.example.viko.view.screens.home.components.SOSSheetContent
import com.example.viko.view.screens.home.components.TeamSheetContent
import com.example.viko.view.theme.*

@OptIn(ExperimentalMa    terial3Api::class)
@Composable
fun HomeScreen(appState: AppState, navController: NavController) {
    var showSOSSheet by remember { mutableStateOf(false) }
    var showAnnouncementSheet by remember { mutableStateOf(false) }
    var showTeamSheet by remember { mutableStateOf<String?>(null) }
    var talkingChannelId by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(VBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            HomeTopBar(
                appState = appState,
                onSosClick = { showSOSSheet = true },
                onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                onSettingsClick = { navController.navigate(Screen.ManageMembers.route) }
            )

            // Channel grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 24.dp, end = 24.dp, top = 28.dp, bottom = 100.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(appState.channels) { channel ->
                    val workers = appState.workersForChannel(channel.id)
                    val onlineCount = workers.count { it.isOnline }
                    val isActive = onlineCount > 0
                    val workerSpeaking = workers.any {
                        it.status == WorkerStatus.SPEAKING
                    }

                    val isManagerTalking = talkingChannelId == channel.id
                    val audioMode = when {
                        isManagerTalking -> ChannelAudioMode.Talking
                        workerSpeaking -> ChannelAudioMode.Listening
                        else -> ChannelAudioMode.Idle
                    }
                    ChannelCard(
                        channel = channel,
                        onlineCount = onlineCount,
                        totalCount = workers.size,
                        isActive = isActive,
                        audioMode = audioMode,
                        onMicTap = {
                            if (isActive) {
                                talkingChannelId = if (isManagerTalking) null else channel.id
                            }
                        },
                        onChipTap = { showTeamSheet = channel.id }
                    )
                }
            }

            // Make Announcement bottom button (dark card, white megaphone + label)
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
                        .clickable { showAnnouncementSheet = true },
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
    }

    // SOS Bottom Sheet
    if (showSOSSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSOSSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VSurface,
            dragHandle = { VikoDragHandle() }
        ) {
            SOSSheetContent(
                appState = appState,
                onClose = { showSOSSheet = false },
                onDirectCall = { workerId ->
                    showSOSSheet = false
                    navController.navigate(Screen.DirectCall.createRoute(workerId))
                }
            )
        }
    }

    // Announcement Bottom Sheet
    if (showAnnouncementSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAnnouncementSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.Transparent,
            dragHandle = null
        ) {
            AnnouncementSheetContent(
                appState = appState,
                onClose = { showAnnouncementSheet = false }
            )
        }
    }

    // Team Bottom Sheet
    showTeamSheet?.let { channelId ->
        ModalBottomSheet(
            onDismissRequest = { showTeamSheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VSurface,
            dragHandle = { VikoDragHandle() }
        ) {
            TeamSheetContent(
                channelId = channelId,
                appState = appState,
                onClose = { showTeamSheet = null },
                onDirectCall = { workerId ->
                    showTeamSheet = null
                    navController.navigate(Screen.DirectCall.createRoute(workerId))
                },
                onManageMembers = {
                    showTeamSheet = null
                    navController.navigate(Screen.ManageMembers.route)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    VikoTheme {
        HomeScreen(appState = AppState(), navController = rememberNavController())
    }
}
