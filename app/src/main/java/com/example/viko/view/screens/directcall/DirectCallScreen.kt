package com.example.viko.view.screens.directcall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.components.SpeakingRipple
import com.example.viko.di.AppViewModelProvider
import com.example.viko.domain.team.model.Worker
import com.example.viko.type.CallState
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VShadow
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VSurfaceVariant

/** One-to-one push-to-talk with a single worker, with live translation. */
@Composable
fun DirectCallScreen(
    onBack: () -> Unit,
    viewModel: DirectCallViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val worker = uiState.worker ?: return
    val callState = uiState.callState

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        if (callState != CallState.IDLE) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            ) {
                SpeakingRipple(
                    size = 280.dp,
                    color = if (callState == CallState.SPEAKING) VOnSurface else VAccentRed.copy(alpha = 0.5f)
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, "Back", tint = VOnSurface)
                }
                Text(
                    text = "Direct call",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VOnSurfaceSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            CallerIdentity(
                worker = worker,
                callState = callState,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.weight(1f))

            if (uiState.transcript != null && callState != CallState.SPEAKING) {
                TranscriptCard(
                    isLive = callState == CallState.LISTENING,
                    translation = uiState.translation.orEmpty(),
                    transcript = uiState.transcript.orEmpty()
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            PushToTalkButton(
                callState = callState,
                onToggle = viewModel::toggleTalking
            )

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FloatingActionButton(
                    onClick = onBack,
                    containerColor = VAccentRed,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Default.CallEnd, "End Call", modifier = Modifier.size(28.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun CallerIdentity(
    worker: Worker,
    callState: CallState,
    modifier: Modifier = Modifier
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(VSurface)
                .border(2.dp, VBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = worker.initials(),
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                color = VOnSurface
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = worker.name,
            style = MaterialTheme.typography.displaySmall,
            color = VOnSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (callState) {
                CallState.LISTENING -> "${worker.zone} • Speaking..."
                CallState.SPEAKING -> "${worker.zone} • Listening..."
                CallState.IDLE -> "${worker.zone} • Available"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (callState == CallState.LISTENING) VAccentRed else VOnSurfaceSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TranscriptCard(isLive: Boolean, translation: String, transcript: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = VShadow)
            .clip(RoundedCornerShape(16.dp))
            .background(VSurface)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isLive) VAccentRed else VOnSurfaceSecondary)
                )
                Text(
                    text = if (isLive) "Translating..." else "Translated",
                    fontSize = 11.sp,
                    color = if (isLive) VAccentRed else VOnSurfaceSecondary
                )
            }
            Text(
                text = translation,
                style = MaterialTheme.typography.bodyLarge,
                color = VOnSurface
            )
            HorizontalDivider(color = VBorder)
            Text(
                text = transcript,
                style = MaterialTheme.typography.bodySmall,
                color = VOnSurfaceSecondary
            )
        }
    }
}

@Composable
private fun PushToTalkButton(callState: CallState, onToggle: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    when (callState) {
                        CallState.SPEAKING -> VOnSurface
                        CallState.LISTENING -> VAccentRed.copy(alpha = 0.1f)
                        CallState.IDLE -> VSurfaceVariant
                    }
                )
                .border(
                    width = 1.5.dp,
                    color = when (callState) {
                        CallState.SPEAKING -> VOnSurface
                        CallState.LISTENING -> VAccentRed.copy(alpha = 0.3f)
                        CallState.IDLE -> VBorder
                    },
                    shape = CircleShape
                )
                // The worker holds the channel while LISTENING — no talking over them.
                .clickable(enabled = callState != CallState.LISTENING, onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (callState == CallState.SPEAKING) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = "Push to talk",
                tint = when (callState) {
                    CallState.SPEAKING -> Color.White
                    CallState.LISTENING -> VAccentRed
                    CallState.IDLE -> VOnSurface
                },
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (callState) {
                CallState.SPEAKING -> "Release to send"
                CallState.LISTENING -> "Worker speaking"
                CallState.IDLE -> "Tap to speak"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = VOnSurfaceSecondary
        )
    }
}

/** "Nguyen Minh Tam" → "NM". */
private fun Worker.initials(): String =
    name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("")
