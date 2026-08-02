package com.example.viko.view.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.team.model.Worker
import com.example.viko.mock.AppState
import com.example.viko.type.WorkerStatus
import com.example.viko.navigation.Screen
import com.example.viko.components.*
import com.example.viko.view.theme.*
import kotlinx.coroutines.delay
import com.example.viko.R // Replace with your actual package name
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(appState: AppState, navController: NavController) {
    var showSOSSheet by remember { mutableStateOf(false) }
    var showAnnouncementSheet by remember { mutableStateOf(false) }
    var showTeamSheet by remember { mutableStateOf<String?>(null) }
    var talkingChannelId by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(VBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 44.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SOS button (top-left)
                Row(
                    modifier = Modifier
                        .shadow(2.dp, RoundedCornerShape(14.dp), spotColor = VShadow)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VSurface)
                        .clickable { showSOSSheet = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sos),
                        contentDescription = "SOS Emergency Button",
                        tint = VAccentRed, // Changes the icon color dynamically
                        modifier = Modifier.size(24.dp) // Sets custom sizing
                    )
                    Text(
                        text = "SOS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = VAccentRed
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Notifications with blue badge
                Box {
                    IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bell),
                            contentDescription = "Notification Button",
                            tint = VOnSurface, // untinted defaulted to dark, invisible on dark bg
                            modifier = Modifier.size(24.dp) // Sets custom sizing
                        )

                    }
                    if (appState.activeAlertsCount() > 0) {
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
                                text = "${appState.activeAlertsCount()}",
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                IconButton(onClick = { navController.navigate(Screen.ManageMembers.route) }) {
                    Icon(Icons.Outlined.Settings, "Settings", tint = VOnSurface)
                }
            }

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

enum class ChannelAudioMode {
    Idle,
    Talking,
    Listening
}

@Composable
fun ChannelCard(
    channel: Channel,
    onlineCount: Int,
    totalCount: Int,
    isActive: Boolean,
    audioMode: ChannelAudioMode,
    onMicTap: () -> Unit,
    onChipTap: () -> Unit
) {
    val contentAlpha = if (isActive) 1f else 0.35f
    val isSpeaking = audioMode != ChannelAudioMode.Idle
    val isListening = audioMode == ChannelAudioMode.Listening

    // Figma splits the two active states:
    //   TALK    → navy panel, blue circle, white mic icon, BLUE label
    //   SPEAKER → neutral dark panel, white circle, blue icon, WHITE label
    val activePanelColor = if (isListening) VListeningPanel else VActivePanel
    val activeCircleColor = if (isListening) Color.White else VMicBlue
    val activeIconColor = if (isListening) VMicBlue else Color.White
    val activeLabelColor = if (isListening) Color.White else VAccentBlue

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = VShadow)
            .clip(RoundedCornerShape(16.dp))
            .background(VSurface)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Member count chip (tap → team sheet)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VSurfaceVariant)
                    .border(1.dp, VBorder, RoundedCornerShape(20.dp))
                    .clickable(enabled = isActive, onClick = onChipTap)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.People,
                    contentDescription = null,
                    tint = VOnSurface.copy(alpha = contentAlpha),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$onlineCount/$totalCount",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = VOnSurface.copy(alpha = contentAlpha)
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = VOnSurfaceSecondary.copy(alpha = contentAlpha),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mic + name area (tap mic → talk). Active state shows a blue panel,
            // a filled blue mic with ripple rings, and a blue channel name.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSpeaking) activePanelColor else Color.Transparent)
                    .clickable(enabled = isActive, onClick = onMicTap)
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(if (isSpeaking) 104.dp else 72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSpeaking) {
                        SpeakingRipple(size = 104.dp, color = activeCircleColor)
                    }
                    if (isSpeaking) {
                        // Filled circle: blue for talk, white for speaker
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(activeCircleColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isListening) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Listening to ${channel.name}",
                                    tint = activeIconColor,
                                    modifier = Modifier.size(30.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_mic),
                                    contentDescription = "Talking to ${channel.name}",
                                    tint = activeIconColor,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    } else {
                        // Outlined mic
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(VSurface)
                                .border(1.dp, VBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_mic),
                                contentDescription = "Talk to ${channel.name}",
                                tint = VOnSurface.copy(alpha = contentAlpha),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (isSpeaking) activeLabelColor else VOnSurface.copy(alpha = contentAlpha),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class EmergencyType(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: Color
)

@Composable
fun SOSSheetContent(
    appState: AppState,
    onClose: () -> Unit,
    onDirectCall: (String) -> Unit
) {
    val types = listOf(
        EmergencyType("fire", "Fire", Icons.Default.LocalFireDepartment, VAccentRed),
        EmergencyType("gas", "Gas Leak", Icons.Default.PropaneTank, VAccentRed),
        EmergencyType("evacuate", "Evacuate", Icons.AutoMirrored.Filled.ExitToApp, VAccentRed),
        EmergencyType("clear", "All Clear", Icons.Default.VerifiedUser, VAccentBlue),
    )

    var selectedType by remember { mutableStateOf<String?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    // Speaking auto-stops after a short broadcast
    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            delay(3500)
            isSpeaking = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSpeaking) VAccentRed.copy(alpha = 0.05f) else Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            // Header — centered title, close on right
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SOS",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = VOnSurface,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = VOnSurface)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2×2 emergency type grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(330.dp),
                userScrollEnabled = false
            ) {
                items(types) { type ->
                    EmergencyTypeCard(
                        type = type,
                        isSelected = selectedType == type.id,
                        dimmed = isSpeaking,
                        onClick = {
                            if (!isSpeaking) {
                                selectedType = if (selectedType == type.id) null else type.id
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Announce button (red) — shows ripple + "Speaking..." while broadcasting
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.size(if (isSpeaking) 132.dp else 88.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSpeaking) {
                        SpeakingRipple(size = 132.dp, color = VAccentRed)
                    }
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(VAccentRed)
                            .clickable { isSpeaking = !isSpeaking },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Campaign,
                            contentDescription = "Announce",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isSpeaking) "Speaking..." else "Announce",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSpeaking) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSpeaking) VAccentRed else VOnSurface
                )
            }
        }
    }
}

@Composable
private fun EmergencyTypeCard(
    type: EmergencyType,
    isSelected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (dimmed) 0.4f else 1f
    val bgColor = if (isSelected) type.accent.copy(alpha = 0.08f) else VSurface
    val borderColor = if (isSelected) type.accent.copy(alpha = 0.4f) else VBorder

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(157.dp)
            .alpha(alpha)
            .shadow(1.dp, RoundedCornerShape(16.dp), spotColor = VShadow)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = !dimmed, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) type.accent else type.accent.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    type.icon,
                    contentDescription = type.label,
                    tint = if (isSelected) Color.White else type.accent,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = type.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) type.accent else VOnSurface
            )
        }
    }
}

@Composable
fun AnnouncementSheetContent(
    appState: AppState,
    onClose: () -> Unit
) {
    var isSpeaking by remember { mutableStateOf(false) }
    val sheetBackground = if (isSpeaking) VListeningPanel else VSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(384.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(sheetBackground)
            .navigationBarsPadding()
    ) {
        if (isSpeaking) {
            AnnouncementWaveBackground(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(236.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VikoDragHandle()
            Spacer(modifier = Modifier.height(20.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Announce",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = VOnSurface,
                    modifier = Modifier.align(Alignment.Center)
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = VOnSurface,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (isSpeaking) "Speaking..." else "From Lee Suyeon (수연이)",
                style = MaterialTheme.typography.headlineMedium,
                color = VOnSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(if (isSpeaking) 64.dp else 66.dp))

            Box(
                modifier = Modifier.size(if (isSpeaking) 118.dp else 120.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isSpeaking) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .shadow(2.dp, CircleShape, spotColor = VShadow)
                            .clip(CircleShape)
                            .background(VSurface)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(if (isSpeaking) 118.dp else 120.dp)
                        .clip(CircleShape)
                        .background(if (isSpeaking) VAccentBlue else VSurface)
                        .border(
                            width = if (isSpeaking) 0.dp else 1.dp,
                            color = VBorder,
                            shape = CircleShape
                        )
                        .clickable { isSpeaking = !isSpeaking },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_announce),
                        contentDescription = if (isSpeaking) "Stop announcement" else "Start announcement",
                        tint = if (isSpeaking) Color.White else VAccentBlue,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            if (!isSpeaking) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AnnouncementWaveBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "announcementWave")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveProgress"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height * 1.24f)
        val baseRadii = listOf(0.39f, 0.56f, 0.72f)
        baseRadii.forEachIndexed { index, baseRadius ->
            val shiftedProgress = (progress + index * 0.22f) % 1f
            val alpha = (0.08f * (1f - shiftedProgress)).coerceAtLeast(0.02f)
            drawCircle(
                color = VAccentBlue.copy(alpha = alpha),
                radius = size.width * (baseRadius + shiftedProgress * 0.09f),
                center = center
            )
        }
    }
}

@Composable
fun TeamSheetContent(
    channelId: String,
    appState: AppState,
    onClose: () -> Unit,
    onDirectCall: (String) -> Unit,
    onManageMembers: () -> Unit
) {
    val channel = appState.channels.find { it.id == channelId }

    var isEditMode by remember { mutableStateOf(false) }
    var workerToDelete by remember { mutableStateOf<Worker?>(null) }
    var isTalking by remember { mutableStateOf(false) }
    var isAddMode by remember { mutableStateOf(false) }

    val workers = appState.workers.filter { it.channelId == channelId }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
    ) {

        // ===== SPEAK ANIMATION BACKGROUND =====
        if (isTalking) {
            AnnouncementWaveBackground(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(240.dp)
            )
        }

        Column {

            // ===== HEADER =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "${channel?.name ?: "Team"} (${workers.count { it.isOnline }}/${workers.size})",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f)
                )

                if (!isEditMode) {
                    IconButton(
                        onClick = { isEditMode = true }
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                } else {
                    Button(
                        onClick = {
                            isEditMode = false
                        }
                    ) {
                        Text("Save")
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ===== CONTENT =====
            if (!isEditMode) {

                workers.forEach { worker ->
                    MemberListItem(
                        worker = worker,
                        onDirectCall = { onDirectCall(worker.id) }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // ===== TALK BUTTON =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isTalking) VAccentBlue else VSurface)
                        .clickable { isTalking = !isTalking },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = null,
                        tint = if (isTalking) Color.White else VAccentBlue
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Talk to Team",
                        color = if (isTalking) Color.White else VOnSurface
                    )
                }

            } else {

                Column {

                    workers.forEach { worker ->
                        EditableMemberListItem(
                            worker = worker,
                            onDelete = { workerToDelete = worker },
                            onNameChange = { }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAddMode = true }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(VBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = VAccentBlue
                            )
                        }

                        Text(
                            text = "Add member",
                            style = MaterialTheme.typography.titleMedium,
                            color = VOnSurface
                        )
                    }

                    if (isAddMode) {
                        LazyColumn {
                            items(
                                appState.workers.filter { it.channelId != channelId },
                                key = { it.id }
                            ) { worker ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            appState.assignWorkerToChannel(worker.id, channelId)
                                            isAddMode = false
                                        }
                                        .padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(worker.name)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== DELETE DIALOG =====
    workerToDelete?.let { worker ->

        AlertDialog(
            onDismissRequest = { workerToDelete = null },
            title = { Text("Delete member?") },
            text = {
                Text("Your paired devices data will be deleted and has to setup a new one later.")
            },
            dismissButton = {
                OutlinedButton(onClick = { workerToDelete = null }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        appState.removeWorker(worker.id)
                        workerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VAccentRed
                    )
                ) {
                    Text("Delete")
                }
            }
        )
    }
}

@Composable
fun MemberListItem(
    worker: Worker,
    onDirectCall: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDirectCall?.invoke() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(VBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = worker.name.first().toString(),
                style = MaterialTheme.typography.titleLarge,
                color = VOnSurface
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = worker.name,
                style = MaterialTheme.typography.titleMedium,
                color = VOnSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WorkerStatusIndicator(worker = worker)
                Text(
                    text = when (worker.status) {
                        WorkerStatus.SPEAKING -> "Speaking..."
                        WorkerStatus.LISTENING -> "Listening"
                        WorkerStatus.OFFLINE -> "Offline"
                        WorkerStatus.AVAILABLE -> "${worker.zone} • Available"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (worker.status == WorkerStatus.SPEAKING) VAccentRed else VOnSurfaceSecondary
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        } else {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BatteryBadge(level = worker.batteryLevel)
                if (onDirectCall != null && worker.isOnline) {
                    IconButton(
                        onClick = { onDirectCall() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Phone, "Call", tint = VOnSurface, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EditableMemberListItem(
    worker: Worker,
    onDelete: () -> Unit,
    onNameChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp,
                vertical = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        OutlinedTextField(
            value = worker.name,
            onValueChange = onNameChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        IconButton(
            onClick = onDelete
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = VAccentRed
            )
        }
    }
}
@Composable
private fun WorkerStatusIndicator(worker: Worker) {
    val animatedColor = when (worker.status) {
        WorkerStatus.SPEAKING -> VAccentRed
        WorkerStatus.LISTENING -> VAccentBlue
        else -> null
    }

    if (animatedColor == null || !worker.isOnline) {
        StatusDot(isOnline = worker.isOnline)
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "workerStatus")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "workerStatusProgress"
    )

    Canvas(modifier = Modifier.size(14.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = animatedColor.copy(alpha = 0.22f * (1f - progress)),
            radius = size.minDimension * (0.25f + progress * 0.25f),
            center = center
        )
        drawCircle(
            color = animatedColor,
            radius = size.minDimension * 0.24f,
            center = center
        )
    }
}
