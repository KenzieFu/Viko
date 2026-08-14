package com.example.viko.view.screens.directcall

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.domain.team.model.Worker
import com.example.viko.mock.AppState
import com.example.viko.type.CallState
import com.example.viko.components.*
import com.example.viko.view.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
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
    var transcript by remember { mutableStateOf<String?>(null) }
    var translation by remember { mutableStateOf<String?>(null) }

    // Simulate incoming speech after a few seconds
    LaunchedEffect(Unit) {
        delay(2500)
        callState = CallState.LISTENING
        transcript = "Vâng thủ trưởng, tôi nghĩ chúng tôi đã xử lý xong rồi. Vậy là ổn rồi ạ."
        translation = "Yes boss, I think we've got them handled already now. So it's fine now."
        delay(4000)
        callState = CallState.IDLE
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        // Ripple animation behind mic button when speaking/listening
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

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // Top bar
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

            // Worker avatar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(VSurface)
                    .border(2.dp, VBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = worker.name.split(" ").map { it.first() }.take(2).joinToString(""),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Medium,
                    color = VOnSurface
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Worker name and status
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

            Spacer(modifier = Modifier.weight(1f))

            // Transcript card (when listening to worker)
            if (transcript != null && (callState == CallState.LISTENING || callState == CallState.IDLE)) {
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
                                    .background(if (callState == CallState.LISTENING) VAccentRed else VOnSurfaceSecondary)
                            )
                            Text(
                                text = if (callState == CallState.LISTENING) "Translating..." else "Translated",
                                fontSize = 11.sp,
                                color = if (callState == CallState.LISTENING) VAccentRed else VOnSurfaceSecondary
                            )
                        }
                        Text(
                            text = translation ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = VOnSurface
                        )
                        if (transcript != null) {
                            HorizontalDivider(color = VBorder)
                            Text(
                                text = transcript!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = VOnSurfaceSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // PTT Mic button
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
                        .clickable(enabled = callState != CallState.LISTENING) {
                            callState = if (callState == CallState.SPEAKING) CallState.IDLE else CallState.SPEAKING
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (callState == CallState.SPEAKING) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "PTT",
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

            Spacer(modifier = Modifier.height(40.dp))

            // End call button
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

@Preview(showBackground = true)
@Composable
private fun DirectCallScreenPreview() {
    val appState = AppState()
    VikoTheme {
        DirectCallScreen(
            workerId = appState.workers.first().id,
            appState = appState,
            onBack = {}
        )
    }
}
