package com.example.viko.view.screens.directcall

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.components.MissingWorker
import com.example.viko.di.AppViewModelProvider
import com.example.viko.domain.team.model.Worker
import com.example.viko.type.CallState
import com.example.viko.type.WorkerStatus
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.theme.VAccentBlue
import com.example.viko.view.theme.VActivePanel
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VMemberSpeaking
import com.example.viko.view.theme.VMemberSpeakingSurface
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VOnline
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VWarning
import com.example.viko.view.theme.VikoTheme

/**
 * Supervisor ↔ one member, with the translated speech shown as text.
 *
 * Layout note — each glow is drawn by the thing it belongs to (behind the name,
 * or around the mic), never as a free-floating layer: the old version pinned one
 * to the bottom of the screen and it drew straight over the transcript card. The
 * two never coexist either — the name glows teal while the member talks, the mic
 * rings blue while the supervisor talks, and the transcript card only exists in
 * the first case.
 */
@Composable
fun DirectCallScreen(
    onBack: () -> Unit,
    viewModel: DirectCallViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Asked for on first press rather than on entry: the supervisor may only
    // ever listen, and a permission dialog thrown at a screen they opened to
    // hear someone is noise. Granting it starts the stream immediately, so the
    // press that triggered the prompt isn't wasted.
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) viewModel.toggleTalking()
    }

    DirectCallScreen(
        uiState = uiState,
        onBack = onBack,
        onToggleTalking = {
            if (hasMicPermission) viewModel.toggleTalking()
            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    )
}

@Composable
fun DirectCallScreen(
    uiState: DirectCallUiState,
    onBack: () -> Unit,
    onToggleTalking: () -> Unit
) {
    // Room answers a frame or two after navigation, and the member could be
    // removed while this is open. Either way the close button has to stay
    // reachable — an early return here leaves a black screen with no way out.
    val worker = uiState.worker
    if (worker == null) {
        MissingWorker(onBack = onBack)
        return
    }

    val isMemberTalking = uiState.callState == CallState.LISTENING
    val isSupervisorTalking = uiState.callState == CallState.SPEAKING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "DIRECT CALL",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                color = VOnSurfaceSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
            )
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        Spacer(modifier = Modifier.weight(0.7f))

        CallerIdentity(worker = worker, isMemberTalking = isMemberTalking, isSupervisorTalking = isSupervisorTalking)

        // Models are not on this phone, so the line is a plain relay. Saying so
        // is the difference between "no Korean yet" and "no Korean coming".
        if (uiState.voiceOnly) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Voice only \u2014 translation is off",
                style = MaterialTheme.typography.bodyMedium,
                color = VOnSurfaceSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }

        if (isMemberTalking || uiState.isTranslating) {
            // The member has the channel: their words, translated, take the
            // middle of the screen and the mic shrinks out of the way.
            Spacer(modifier = Modifier.height(40.dp))
            TranscriptCard(
                text = uiState.translation,
                source = uiState.transcript,
                isTranslating = uiState.isTranslating
            )
            Spacer(modifier = Modifier.weight(0.4f))
            MicButton(state = MicState.Disabled, onClick = onToggleTalking)
            Spacer(modifier = Modifier.height(48.dp))
        } else {
            Spacer(modifier = Modifier.height(48.dp))
            MicButton(
                state = if (isSupervisorTalking) MicState.Talking else MicState.Idle,
                onClick = onToggleTalking
            )
            // Speech is carried live, so there is no transcript of the
            // supervisor's side — this slot only ever explains why a press to
            // talk did nothing.
            uiState.talkError?.let { error ->
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VWarning,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1.3f))
        }
    }
}

/**
 * Name and status line. While the member talks, a teal glow sits behind them and
 * a waveform appears under the status — drawn behind the text, so it takes no
 * layout space and nothing moves when it appears.
 */
@Composable
private fun CallerIdentity(worker: Worker, isMemberTalking: Boolean, isSupervisorTalking: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isMemberTalking) Modifier.drawBehind { drawMemberGlow() } else Modifier)
            .padding(horizontal = 32.dp)
    ) {
        Text(
            text = worker.name,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = VOnSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Text(
                text = "${worker.zone} • ",
                style = MaterialTheme.typography.bodyLarge,
                color = VOnSurfaceSecondary
            )
            Text(
                // Three states, three colours: green = reachable, blue = the
                // supervisor is talking, teal = the member is. Never red here.
                text = when {
                    isMemberTalking -> "Speaking..."
                    isSupervisorTalking -> "Listening..."
                    else -> "Available"
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = when {
                    isMemberTalking -> VMemberSpeaking
                    isSupervisorTalking -> VAccentBlue
                    else -> VOnline
                }
            )
        }
        // Always laid out, only shown while the member talks, so the name does
        // not jump when they start.
        Spacer(modifier = Modifier.height(12.dp))
        Icon(
            Icons.Default.GraphicEq,
            contentDescription = null,
            tint = VOnSurfaceSecondary,
            modifier = Modifier
                .size(28.dp)
                .alpha(if (isMemberTalking) 1f else 0f)
        )
    }
}

/** Two soft teal discs centred on the name — the member's voice, not ours. */
private fun DrawScope.drawMemberGlow() {
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(color = VMemberSpeakingSurface.copy(alpha = 0.35f), radius = 170.dp.toPx(), center = center)
    drawCircle(color = VMemberSpeakingSurface.copy(alpha = 0.55f), radius = 150.dp.toPx(), center = center)
}

/**
 * The translated speech. The slot is kept while the member talks even before
 * any text arrives, so the mic does not slide up and back as lines come in.
 */
@Composable
private fun TranscriptCard(
    text: String?,
    source: String? = null,
    isTranslating: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VSurface)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = VOnSurface,
                    textAlign = TextAlign.Center
                )
            }
            // What they actually said, under the Korean. The supervisor cannot
            // read it, but seeing it is how they know the badge was heard.
            if (source != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = source,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VOnSurfaceSecondary,
                    textAlign = TextAlign.Center
                )
            }
            // The cascade runs on the whole turn, so the Korean lands seconds
            // after the member lets go. Without this the gap reads as a dead link.
            if (isTranslating) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Translating\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VOnSurfaceSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private enum class MicState { Idle, Talking, Disabled }

/**
 * Push-to-talk. White with a blue mic when idle, blue with a white mic and
 * rings while the supervisor talks, small and greyed while the member holds
 * the channel — half duplex, no talking over them.
 *
 * The rings are drawn behind the circle and outside its bounds, so they cost no
 * layout space; zIndex puts them under the name rather than over it.
 */
@Composable
private fun MicButton(state: MicState, onClick: () -> Unit) {
    val diameter = if (state == MicState.Disabled) SMALL_MIC else LARGE_MIC
    Box(
        modifier = Modifier
            .zIndex(-1f)
            .size(diameter)
            .then(if (state == MicState.Talking) Modifier.drawBehind { drawTalkRings() } else Modifier)
            .clip(CircleShape)
            .background(
                when (state) {
                    MicState.Idle -> Color.White
                    MicState.Talking -> VMicBlue
                    MicState.Disabled -> VSurface
                }
            )
            .clickable(enabled = state != MicState.Disabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = when (state) {
                MicState.Talking -> "Stop talking"
                MicState.Idle -> "Push to talk"
                MicState.Disabled -> "Member is speaking"
            },
            tint = when (state) {
                MicState.Idle -> VMicBlue
                MicState.Talking -> Color.White
                MicState.Disabled -> VMicBlue.copy(alpha = 0.25f)
            },
            modifier = Modifier.size(if (state == MicState.Disabled) 28.dp else 44.dp)
        )
    }
}

/** Three navy rings around the talking mic, darkest outermost. */
private fun DrawScope.drawTalkRings() {
    val center = Offset(size.width / 2f, size.height / 2f)
    val base = size.minDimension / 2f
    drawCircle(color = VActivePanel.copy(alpha = 0.35f), radius = base * 2.5f, center = center)
    drawCircle(color = VActivePanel.copy(alpha = 0.6f), radius = base * 1.9f, center = center)
    drawCircle(color = VActivePanel, radius = base * 1.45f, center = center)
}

private val LARGE_MIC = 168.dp
private val SMALL_MIC = 88.dp

/** Member holding the channel, with a translation coming through. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun DirectCallScreenListeningPreview() {
    VikoTheme {
        DirectCallScreen(
            uiState = DirectCallUiState(
                worker = previewWorker(name = "Nguyen Minh Tam", zone = "Central Building", status = WorkerStatus.SPEAKING),
                callState = CallState.LISTENING,
                translation = "Yes boss, I think we've got them handled already now. So it's fine now."
            ),
            onBack = {}, onToggleTalking = {}
        )
    }
}

/** Supervisor holding the mic. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun DirectCallScreenTalkingPreview() {
    VikoTheme {
        DirectCallScreen(
            uiState = DirectCallUiState(
                worker = previewWorker(name = "Nguyen Minh Tam", zone = "Central Building"),
                callState = CallState.SPEAKING
            ),
            onBack = {}, onToggleTalking = {}
        )
    }
}

/** Idle — nobody talking. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun DirectCallScreenIdlePreview() {
    VikoTheme {
        DirectCallScreen(
            uiState = DirectCallUiState(worker = previewWorker(name = "Nguyen Minh Tam", zone = "Central Building")),
            onBack = {}, onToggleTalking = {}
        )
    }
}

/** Worker not resolved — the escape hatch, easy to break and hard to reach by hand. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun DirectCallScreenMissingWorkerPreview() {
    VikoTheme {
        DirectCallScreen(uiState = DirectCallUiState(worker = null), onBack = {}, onToggleTalking = {})
    }
}
