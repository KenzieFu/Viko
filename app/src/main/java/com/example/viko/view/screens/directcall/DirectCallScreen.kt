package com.example.viko.view.screens.directcall

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
        // Permission approval must not open the mic automatically.
        permissionHint = if (granted) "Microphone ready — tap to speak" else "Microphone permission needed to talk"
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
    Column(
        Modifier.fillMaxSize().background(VBackground)
            .statusBarsPadding().navigationBarsPadding().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(24.dp).zIndex(1f)) {
            Text("DIRECT CALL", fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp,
                color = VOnSurfaceSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center))
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterEnd).size(24.dp)) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface, modifier = Modifier.size(24.dp))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val compact = maxHeight < 600.dp
            val fontScale = LocalDensity.current.fontScale
            val scrollLayout = maxHeight < 400.dp || (fontScale > 1.3f && maxHeight < 500.dp)
            val cardHeight = (maxHeight * .33f).coerceIn(112.dp, 220.dp)
            val pulseRadius = (maxWidth * .49f).coerceIn(140.dp, 218.dp)
            val feedback = uiState.talkError ?: uiState.translationProblem
            Column(
                Modifier.fillMaxWidth().then(
                    if (scrollLayout) Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxHeight()
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (scrollLayout) Spacer(Modifier.height(24.dp))
                else Spacer(Modifier.weight(if (uiState.voiceOnly) 1f else 3f))
                CallerIdentity(worker, listening, speaking, pulseRadius)
                if (uiState.voiceOnly) {
                    Spacer(Modifier.height(16.dp))
                    Text("Voice only — translation is off", fontSize = 14.sp, lineHeight = 20.sp,
                        color = VOnSurfaceSecondary, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp))
                } else {
                    Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
                    TranslationCard(uiState, Modifier.height(cardHeight))
                }
                Spacer(Modifier.height(if (compact || uiState.voiceOnly) 24.dp else 48.dp))
                MicButton(
                    speaking = speaking,
                    listening = listening,
                    enabled = speaking || (worker.isOnline && !listening),
                    onStart = onStartTalking, onStop = onStopTalking
                )
                if (!scrollLayout) Spacer(Modifier.weight(1f))
                Box(
                    Modifier.fillMaxWidth().heightIn(min = if (scrollLayout) 24.dp else 40.dp)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    feedback?.let {
                        Text(it, fontSize = 14.sp, lineHeight = 20.sp, color = VWarning,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
        }
    }
}

@Composable
private fun CallerIdentity(worker: Worker, listening: Boolean, speaking: Boolean, pulseRadius: Dp) {
    Box(Modifier.fillMaxWidth().testTag("direct-call-identity"), contentAlignment = Alignment.Center) {
        if (listening) {
            ExpandingVoicePulse(
                Modifier.matchParentSize().offset(y = (-26).dp).testTag("direct-call-listening-pulse"),
                color = VMemberSpeakingSurface, innerRadius = pulseRadius * .78f,
                outerRadius = pulseRadius, maxAlpha = .32f
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(worker.name, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold,
                color = VOnSurface, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            }, fontSize = 14.sp, lineHeight = 20.sp, color = VOnSurfaceSecondary, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun TranslationCard(state: DirectCallUiState, modifier: Modifier = Modifier) {
    val text = if (state.isTranslating) "Translating…" else state.translation
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).then(modifier)
            .testTag("direct-call-transcript").clip(shape).background(VSurface).border(1.dp, VBorder, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center
    ) {
        if (text != null) {
            // A new result starts at the top even when it repeats the last phrase.
            key(state.translationMessageId, state.isTranslating) {
                Text(text, fontSize = 16.sp, lineHeight = 24.sp, color = VOnSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
            }
        }
    }
}

/** A tap starts capture; the next tap submits the turn. Screen disposal cancels it. */
@Composable
private fun MicButton(
    speaking: Boolean, listening: Boolean, enabled: Boolean,
    onStart: () -> Unit, onStop: () -> Unit
) {
    Box(Modifier.size(88.dp).zIndex(if (speaking) -1f else 0f), contentAlignment = Alignment.Center) {
        if (speaking) {
            ExpandingVoicePulse(Modifier.matchParentSize().testTag("direct-call-speaking-pulse"), outerRadius = 210.dp)
        }
        Box(
            Modifier.matchParentSize().testTag("direct-call-mic").clip(CircleShape)
                .background(when {
                    !enabled -> VSurface
                    speaking -> VMicBlue
                    else -> Color.White
                })
                .semantics {
                    contentDescription = when {
                        listening -> "Badge microphone active"
                        !enabled -> "Microphone unavailable"
                        speaking -> "Tap to stop speaking"
                        else -> "Tap to speak"
                    }
                    stateDescription = when {
                        listening -> "Listening to badge"
                        speaking -> "Speaking"
                        !enabled -> "Unavailable"
                        else -> "Ready"
                    }
                }
                .clickable(enabled = enabled, role = Role.Button,
                    onClickLabel = if (speaking) "Stop speaking" else "Start speaking") {
                    if (speaking) onStop() else onStart()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = null,
                tint = when {
                    !enabled -> VMicBlue.copy(alpha = .25f)
                    speaking -> Color.White
                    else -> VMicBlue
                },
                modifier = Modifier.size(24.dp))
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
