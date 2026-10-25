package com.example.viko.view.screens.directcall

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.R
import com.example.viko.components.ExpandingVoicePulse
import com.example.viko.components.MissingWorker
import com.example.viko.di.AppViewModelProvider
import com.example.viko.domain.team.model.Worker
import com.example.viko.type.CallState
import com.example.viko.type.WorkerStatus
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.theme.*

@Composable
fun DirectCallScreen(
    onBack: () -> Unit,
    viewModel: DirectCallViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionHint by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // The permission dialog interrupts the gesture. Granting access must not
        // open the mic after the finger has already been lifted.
        permissionHint = if (granted) "Microphone ready — hold to speak" else "Microphone permission needed to talk"
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.cancelTalking() }
    DirectCallScreen(
        uiState = uiState.copy(talkError = permissionHint ?: uiState.talkError),
        onBack = { viewModel.cancelTalking(); onBack() },
        onStartTalking = {
            permissionHint = null
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                viewModel.startTalking()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onStopTalking = viewModel::stopTalking,
        onCancelTalking = viewModel::cancelTalking
    )
}

/** One screen for idle, manager speech, and worker speech; identity stays in the same place. */
@Composable
fun DirectCallScreen(
    uiState: DirectCallUiState,
    onBack: () -> Unit,
    onStartTalking: () -> Unit,
    onStopTalking: () -> Unit,
    onCancelTalking: () -> Unit = onStopTalking
) {
    val cancel by rememberUpdatedState(onCancelTalking)
    DisposableEffect(Unit) { onDispose { cancel() } }
    val worker = uiState.worker
    if (worker == null) {
        MissingWorker(onBack = onBack)
        return
    }
    val listening = uiState.callState == CallState.LISTENING
    val speaking = uiState.callState == CallState.SPEAKING
    // A completed translation often arrives after the member releases their mic.
    val showTranscript = !speaking && (listening || uiState.isTranslating ||
        uiState.translation != null || uiState.transcript != null)

    Column(
        Modifier.fillMaxSize().background(VBackground)
            .statusBarsPadding().navigationBarsPadding().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(24.dp)) {
            Text("DIRECT CALL", fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp,
                color = VOnSurfaceSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center))
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterEnd).size(24.dp)) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface, modifier = Modifier.size(24.dp))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val contentHeight = maxHeight
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = contentHeight),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(contentHeight * .25f))
                CallerIdentity(worker, listening, speaking)
                if (uiState.voiceOnly) {
                    Text("Voice only — translation is off", fontSize = 14.sp, lineHeight = 20.sp,
                        color = VOnSurfaceSecondary, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
                }
                if (showTranscript) {
                    Spacer(Modifier.height(24.dp))
                    TranscriptCard(uiState.translation, uiState.transcript, uiState.isTranslating)
                    Spacer(Modifier.height(48.dp))
                } else {
                    Spacer(Modifier.height(16.dp))
                }
                MicButton(
                    speaking = speaking,
                    compact = showTranscript,
                    enabled = worker.isOnline && !listening,
                    onStart = onStartTalking, onStop = onStopTalking, onCancel = onCancelTalking
                )
                uiState.talkError?.let { error ->
                    Text(error, fontSize = 14.sp, lineHeight = 20.sp,
                        color = VWarning, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 24.dp))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CallerIdentity(worker: Worker, listening: Boolean, speaking: Boolean) {
    Box(Modifier.fillMaxWidth().testTag("direct-call-identity"), contentAlignment = Alignment.Center) {
        if (listening) {
            ExpandingVoicePulse(
                Modifier.matchParentSize().testTag("direct-call-listening-pulse"),
                color = VMemberSpeakingSurface, innerRadius = 120.dp, outerRadius = 170.dp, maxAlpha = .5f
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(worker.name, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold,
                color = VOnSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            val status = when {
                listening -> "Speaking..."
                speaking -> "Listening..."
                !worker.isOnline -> "Not Available"
                else -> "Available"
            }
            val statusColor = when {
                listening -> VMemberSpeakingText
                speaking -> VAccentBlue
                !worker.isOnline -> VOnSurfaceSecondary
                else -> VOnline
            }
            Text(buildAnnotatedString {
                append("${worker.zone} • ")
                withStyle(SpanStyle(color = statusColor, fontWeight = FontWeight.Bold)) { append(status) }
            }, fontSize = 14.sp, lineHeight = 20.sp, color = VOnSurfaceSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            // Reserve the waveform slot in every state so the title and mic do not jump.
            Canvas(Modifier.size(52.dp, 20.dp).alpha(if (listening) 1f else 0f)) {
                val levels = listOf(.25f, .5f, .85f, .4f, .65f, 1f, .7f, .9f, .5f, .65f, .9f, .35f, .6f, .25f)
                levels.forEachIndexed { index, level ->
                    val x = size.width * (index + .5f) / levels.size
                    drawLine(VOnSurfaceSecondary, Offset(x, size.height * (1f - level) / 2f),
                        Offset(x, size.height * (1f + level) / 2f), strokeWidth = 1.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun TranscriptCard(text: String?, source: String?, isTranslating: Boolean) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(220.dp)
            .testTag("direct-call-transcript").clip(RoundedCornerShape(12.dp)).background(VSurface)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (text != null) Text(text, fontSize = 16.sp, lineHeight = 24.sp, color = VOnSurface, textAlign = TextAlign.Center)
            if (source != null) {
                if (text != null) Spacer(Modifier.height(12.dp))
                Text(source, fontSize = 14.sp, lineHeight = 20.sp, color = VOnSurfaceSecondary, textAlign = TextAlign.Center)
            }
            if (isTranslating) {
                if (text != null || source != null) Spacer(Modifier.height(16.dp))
                Text("Translating…", fontSize = 14.sp, lineHeight = 20.sp, color = VOnSurfaceSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Press/release owns capture. Recomposition during a press must not restart the gesture. */
@Composable
private fun MicButton(
    speaking: Boolean, compact: Boolean, enabled: Boolean,
    onStart: () -> Unit, onStop: () -> Unit, onCancel: () -> Unit
) {
    val start by rememberUpdatedState(onStart)
    val stop by rememberUpdatedState(onStop)
    val cancel by rememberUpdatedState(onCancel)
    Box(Modifier.size(if (compact) 88.dp else 168.dp).zIndex(-1f), contentAlignment = Alignment.Center) {
        if (speaking) {
            ExpandingVoicePulse(Modifier.matchParentSize().testTag("direct-call-speaking-pulse"), outerRadius = 210.dp)
        }
        Box(
            Modifier.matchParentSize().testTag("direct-call-mic").clip(CircleShape)
                .background(when { !enabled -> VSurface; speaking -> VMicBlue; else -> Color.White })
                .semantics {
                    role = Role.Button
                    contentDescription = if (!enabled) "Microphone unavailable" else "Hold to speak"
                    stateDescription = if (speaking) "Speaking" else if (!enabled) "Unavailable" else "Ready"
                    if (!enabled) disabled()
                    else onClick(label = if (speaking) "Stop speaking" else "Start speaking") {
                        // Accessibility users can explicitly start and stop without holding a gesture.
                        if (speaking) stop() else start()
                        true
                    }
                }
                .pointerInput(enabled) {
                    if (enabled) detectTapGestures(onPress = {
                        var finished = false
                        try {
                            start()
                            val released = tryAwaitRelease()
                            finished = true
                            if (released) stop() else cancel()
                        } finally {
                            if (!finished) cancel()
                        }
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = null,
                tint = when { !enabled -> VMicBlue.copy(alpha = .25f); speaking -> Color.White; else -> VMicBlue },
                modifier = Modifier.size(if (compact) 28.dp else 44.dp))
        }
    }
}

@Preview(device = "id:pixel_7", showBackground = true)
@Composable
private fun DirectCallScreenIdlePreview() {
    VikoTheme { DirectCallScreen(DirectCallUiState(worker = previewWorker(name = "Nguyen Minh Tam", zone = "Central Building"), voiceOnly = false), {}, {}, {}) }
}

@Preview(device = "id:pixel_7", showBackground = true)
@Composable
private fun DirectCallScreenTalkingPreview() {
    VikoTheme { DirectCallScreen(DirectCallUiState(worker = previewWorker(name = "Nguyen Minh Tam", zone = "Central Building"), callState = CallState.SPEAKING, voiceOnly = false), {}, {}, {}) }
}

@Preview(device = "id:pixel_7", showBackground = true)
@Composable
private fun DirectCallScreenListeningPreview() {
    VikoTheme { DirectCallScreen(DirectCallUiState(worker = previewWorker(name = "Nguyen Minh Tam", zone = "Central Building", status = WorkerStatus.SPEAKING), callState = CallState.LISTENING,
        translation = "Yes boss, I think we’ve got them handled already now. So it’s fine now.", voiceOnly = false), {}, {}, {}) }
}

@Preview(device = "id:pixel_7", showBackground = true)
@Composable
private fun DirectCallScreenMissingWorkerPreview() {
    VikoTheme { DirectCallScreen(DirectCallUiState(), {}, {}, {}) }
}
