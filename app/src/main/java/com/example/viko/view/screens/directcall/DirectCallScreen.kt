package com.example.viko.view.screens.directcall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.mock.AppState
import com.example.viko.type.CallState
import com.example.viko.view.theme.*
import kotlinx.coroutines.delay

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
    workerId: String,
    appState: AppState,
    onBack: () -> Unit
) {
    val worker = appState.workers.find { it.id == workerId }
        ?: appState.workers.firstOrNull()
        ?: return

    var callState by remember { mutableStateOf(CallState.IDLE) }
    var translation by remember { mutableStateOf<String?>(null) }

    // Stand-in for the member talking. Replace when real audio lands.
    LaunchedEffect(Unit) {
        delay(2500)
        callState = CallState.LISTENING
        translation = "Yes boss, I think we've got them handled already now. So it's fine now."
        delay(4000)
        callState = CallState.IDLE
    }

    val isMemberTalking = callState == CallState.LISTENING
    val isSupervisorTalking = callState == CallState.SPEAKING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        // Top bar — label centred, close on the right
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
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Name + status, with the glow behind them while the member is talking
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
                        text = if (callState == CallState.IDLE) "Available" else "Listening...",
                        style = MaterialTheme.typography.bodyMedium,
                        // green = available, blue = a normal action in progress.
                        // Never red here: red is emergency only.
                        color = if (callState == CallState.IDLE) VOnline else VMicBlue
                    )
                }
            }
        }

        // Translated speech from the member
        if (translation != null && isMemberTalking) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VSurfaceVariant)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(
                    text = translation!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VOnSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Push-to-talk. Glow lives inside this slot, so it cannot reach the card.
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
                            isSupervisorTalking -> VMicBlue   // talking: blue fill
                            isMemberTalking -> VSurfaceVariant // their turn: dimmed
                            else -> Color.White               // idle: white fill
                        }
                    )
                    .clickable(enabled = !isMemberTalking) {
                        callState =
                            if (isSupervisorTalking) CallState.IDLE else CallState.SPEAKING
                    },
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

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

/** Soft navy bloom used behind the name or the mic. */
@Composable
private fun RadialGlow(size: androidx.compose.ui.unit.Dp) {
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
