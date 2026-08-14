package com.example.viko.view.screens.home.components

import com.example.viko.view.theme.VikoTheme
import com.example.viko.view.screens.previewChannel
import com.example.viko.view.screens.previewChannelSummary
import com.example.viko.view.screens.previewWorker
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.viko.R
import com.example.viko.components.SpeakingRipple
import com.example.viko.view.screens.home.ChannelSummary
import com.example.viko.view.theme.VActivePanel
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VListeningPanel
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VMicRing
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VShadow
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VSurfaceVariant

/**
 * Three steps, dimmest to loudest:
 *   OFFLINE → no panel, everything faded to INACTIVE_ALPHA, mic not tappable
 *   READY   → staffed but quiet: 40% talk panel, mic rimmed in half-strength blue
 *   TALK    → navy panel, blue circle ringed in VMicRing, outline mic, BLUE label
 *   SPEAKER → neutral dark panel, white circle, blue icon, WHITE label, no ring
 */
enum class ChannelAudioMode { Idle, Talking, Listening }

/** One channel tile on the dashboard: member count chip on top, mic below. */
@Composable
fun ChannelCard(
    summary: ChannelSummary,
    audioMode: ChannelAudioMode,
    onMicTap: () -> Unit,
    onChipTap: () -> Unit
) {
    val isActive = summary.isActive
    val contentAlpha = if (isActive) 1f else INACTIVE_ALPHA
    val isSpeaking = audioMode != ChannelAudioMode.Idle
    val isListening = audioMode == ChannelAudioMode.Listening

    // Staffed but nobody holding the mic. Its own treatment so the grid reads as
    // three steps rather than two: nobody there, ready to talk to, live right now.
    val isReady = isActive && !isSpeaking

    val activePanelColor = if (isListening) VListeningPanel else VActivePanel
    val activeCircleColor = if (isListening) Color.White else VMicBlue
    val activeIconColor = if (isListening) VMicBlue else Color.White
    val activeLabelColor = if (isListening) Color.White else VMicRing

    // Derived from the talking panel rather than a colour of its own, so the
    // ready state stays the same hue if VActivePanel is ever re-sampled.
    val panelColor = when {
        isSpeaking -> activePanelColor
        isReady -> VActivePanel.copy(alpha = READY_PANEL_ALPHA)
        else -> Color.Transparent
    }

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
            MemberCountChip(
                onlineCount = summary.onlineCount,
                totalCount = summary.totalCount,
                contentAlpha = contentAlpha,
                enabled = isActive,
                onClick = onChipTap
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(panelColor)
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
                        ActiveMicCircle(
                            channelName = summary.channel.name,
                            isListening = isListening,
                            circleColor = activeCircleColor,
                            iconColor = activeIconColor
                        )
                    } else {
                        IdleMicCircle(
                            channelName = summary.channel.name,
                            contentAlpha = contentAlpha,
                            isReady = isReady
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = summary.channel.name,
                    // headlineLarge (25sp) overflowed a half-width card:
                    // "Maintenance" rendered as "Maintenan…".
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isSpeaking) activeLabelColor else VOnSurface.copy(alpha = contentAlpha),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MemberCountChip(
    onlineCount: Int,
    totalCount: Int,
    contentAlpha: Float,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VSurfaceVariant)
            .border(1.dp, VBorder, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.People,
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
}

@Composable
private fun ActiveMicCircle(
    channelName: String,
    isListening: Boolean,
    circleColor: Color,
    iconColor: Color
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(circleColor)
            // Ring only while talking: on the speaker state the circle is already
            // white, and a blue ring around white reads as a stray outline.
            .then(
                if (isListening) Modifier
                else Modifier.border(2.dp, VMicRing, CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isListening) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Listening to $channelName",
                tint = iconColor,
                modifier = Modifier.size(30.dp)
            )
        } else {
            // ic_mic is stroke-only (fillColor #00000000), so the talking icon is
            // an outline mic — filled would lose the shape against a solid circle.
            Icon(
                painter = painterResource(id = R.drawable.ic_mic),
                contentDescription = "Talking to $channelName",
                tint = iconColor,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

/**
 * The mic when nobody is holding the channel. [isReady] means there are people
 * on it: the rim picks up the talk blue at half strength, so a staffed channel
 * looks tappable without competing with one that is actually live.
 */
@Composable
private fun IdleMicCircle(channelName: String, contentAlpha: Float, isReady: Boolean) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(VSurface)
            .border(
                width = if (isReady) 2.dp else 1.dp,
                color = if (isReady) VMicRing.copy(alpha = READY_RIM_ALPHA) else VBorder,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_mic),
            contentDescription = if (isReady) "Talk to $channelName" else "$channelName is offline",
            tint = if (isReady) VMicRing else VOnSurface.copy(alpha = contentAlpha),
            modifier = Modifier.size(28.dp)
        )
    }
}

/** How far an offline channel's contents fade back. */
private const val INACTIVE_ALPHA = 0.35f

/** Ready sits between transparent and the full talking panel. */
private const val READY_PANEL_ALPHA = 0.40f

/** ...and its rim between the grey idle border and the solid talking ring. */
private const val READY_RIM_ALPHA = 0.55f

/** Half-width, the way the card actually sits in Home's two-column grid. */
@Composable
private fun ChannelCardPreviewFrame(content: @Composable () -> Unit) {
    VikoTheme {
        Box(modifier = Modifier.width(200.dp).padding(12.dp)) { content() }
    }
}

/** Talking: navy panel, blue ring, outline mic, blue label — the Figma state. */
@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun ChannelCardTalkingPreview() {
    ChannelCardPreviewFrame {
        ChannelCard(previewChannelSummary(name = "Operations"), ChannelAudioMode.Talking, {}, {})
    }
}

/** Speaker: neutral panel, white circle, blue icon, white label. */
@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun ChannelCardListeningPreview() {
    ChannelCardPreviewFrame {
        ChannelCard(previewChannelSummary(name = "Operations"), ChannelAudioMode.Listening, {}, {})
    }
}

/** Ready: people on the channel, nobody talking — the new middle state. */
@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun ChannelCardReadyPreview() {
    ChannelCardPreviewFrame {
        ChannelCard(previewChannelSummary(name = "Warehouse"), ChannelAudioMode.Idle, {}, {})
    }
}

/** All three steps together — the only way to check they actually read apart. */
@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun ChannelCardAllStatesPreview() {
    VikoTheme {
        Row(modifier = Modifier.padding(12.dp)) {
            listOf<Pair<ChannelSummary, ChannelAudioMode>>(
                ChannelSummary(
                    channel = previewChannel("c9", "Loading Bay"),
                    members = listOf(previewWorker("07", "Rina", "c9", isOnline = false))
                ) to ChannelAudioMode.Idle,
                previewChannelSummary(name = "Warehouse") to ChannelAudioMode.Idle,
                previewChannelSummary(name = "Operations") to ChannelAudioMode.Talking
            ).forEach { (summary, mode) ->
                Box(modifier = Modifier.width(160.dp).padding(horizontal = 4.dp)) {
                    ChannelCard(summary, mode, {}, {})
                }
            }
        }
    }
}

/** Nobody online — the whole tile fades back and the mic stops responding. */
@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun ChannelCardOfflinePreview() {
    ChannelCardPreviewFrame {
        ChannelCard(
            ChannelSummary(
                channel = previewChannel("c9", "Loading Bay"),
                members = listOf(previewWorker("07", "Rina", "c9", isOnline = false))
            ),
            ChannelAudioMode.Idle, {}, {}
        )
    }
}

/** A name long enough to test the ellipsis at half width. */
@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun ChannelCardLongNamePreview() {
    ChannelCardPreviewFrame {
        ChannelCard(previewChannelSummary(name = "Maintenance Crew"), ChannelAudioMode.Talking, {}, {})
    }
}
