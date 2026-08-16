package com.example.viko.view.screens.directcall

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.viko.type.WorkerStatus
import com.example.viko.view.theme.VikoTheme
import com.example.viko.view.screens.previewWorker
import androidx.compose.ui.tooling.preview.Preview
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.components.MissingWorker
import com.example.viko.di.AppViewModelProvider
import com.example.viko.domain.team.model.Worker
import com.example.viko.type.CallState
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VOnline
import com.example.viko.view.theme.VSurfaceVariant

/**
 * Supervisor ↔ one member, with the translated speech shown as text.
 *
 * Layout note — the glow used to be a free-floating layer pinned to the bottom of
 * the screen at a fixed offset, which meant it drew straight over the transcript
 * card whenever both were visible. Now each glow lives *inside* the slot it
 * belongs to (behind the name, or behind the mic), so layout reserves space for it
 * and nothing can overlap anything else. The two glows also never coexist: the
 * name glows while the member talks, the mic glows while the supervisor talks.
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
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = "DIRECT CALL",
                style = MaterialTheme.typography.labelSmall,
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

        Spacer(modifier = Modifier.weight(1f))

        CallerIdentity(worker = worker, isMemberTalking = isMemberTalking)

        // The transcript card has nothing to show until ASR/NMT lands — speech
        // is carried as live audio, not text. Until then the slot is used for
        // the one thing worth saying: why a press to talk did nothing.
        when {
            uiState.translation != null && isMemberTalking ->
                TranslationCard(text = uiState.translation.orEmpty())

            uiState.talkError != null -> TranslationCard(text = uiState.talkError)
        }

        Spacer(modifier = Modifier.weight(1f))

        PushToTalkSlot(
            isSupervisorTalking = isSupervisorTalking,
            isMemberTalking = isMemberTalking,
            onToggle = onToggleTalking
        )

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

/** Name and status. The glow sits behind them while the member holds the channel. */
@Composable
private fun CallerIdentity(worker: Worker, isMemberTalking: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isMemberTalking) {
            RadialGlow(size = 280.dp)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = worker.name,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = VOnSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row {
                Text(
                    text = "${worker.zone} • ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VOnSurfaceSecondary
                )
                Text(
                    text = if (isMemberTalking) "Listening..." else "Available",
                    style = MaterialTheme.typography.bodyMedium,
                    // Green = available, blue = a normal action in progress.
                    // Never red here: red is emergency only.
                    color = if (isMemberTalking) VMicBlue else VOnline
                )
            }
        }
    }
}

@Composable
private fun TranslationCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VSurfaceVariant)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = VOnSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Push-to-talk. The glow lives inside this slot, so it cannot reach the card. */
@Composable
private fun PushToTalkSlot(
    isSupervisorTalking: Boolean,
    isMemberTalking: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSupervisorTalking) {
            RadialGlow(size = 220.dp)
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSupervisorTalking -> VMicBlue    // talking: blue fill
                        isMemberTalking -> VSurfaceVariant // their turn: dimmed
                        else -> Color.White                // idle: white fill
                    }
                )
                // The member holds the channel while talking — no talking over them.
                .clickable(enabled = !isMemberTalking, onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Push to talk",
                tint = when {
                    isSupervisorTalking -> Color.White
                    isMemberTalking -> VOnSurfaceSecondary
                    else -> VMicBlue
                },
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/** Soft navy bloom used behind the name or the mic. */
@Composable
private fun RadialGlow(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        VMicBlue.copy(alpha = 0.28f),
                        VMicBlue.copy(alpha = 0.10f),
                        Color.Transparent
                    )
                )
            )
    )
}

/** Member holding the channel, with a translation coming through. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun DirectCallScreenListeningPreview() {
    VikoTheme {
        DirectCallScreen(
            uiState = DirectCallUiState(
                worker = previewWorker(name = "Budi", status = WorkerStatus.SPEAKING),
                callState = CallState.LISTENING,
                translation = "Mesin nomor tiga berhenti, saya perlu bantuan."
            ),
            onBack = {}, onToggleTalking = {}
        )
    }
}

/** Idle — nobody talking. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun DirectCallScreenIdlePreview() {
    VikoTheme {
        DirectCallScreen(
            uiState = DirectCallUiState(worker = previewWorker(name = "Sari")),
            onBack = {}, onToggleTalking = {}
        )
    }
}

/** Worker not resolved — the escape hatch, easy to break and hard to reach by hand. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun DirectCallScreenMissingWorkerPreview() {
    VikoTheme {
        DirectCallScreen(uiState = DirectCallUiState(worker = null), onBack = {}, onToggleTalking = {})
    }
}
