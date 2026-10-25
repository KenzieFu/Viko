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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Three mic states, dimmest to loudest:
 *   EMPTY → nobody on the channel: no panel, whole tile faded to INACTIVE_ALPHA
 *   READY → members present, nobody talking: no panel, grey rim, WHITE mic + label
 *   TALK  → navy panel, blue circle ringed in VMicRing, outline mic, BLUE label
 *
 * EMPTY and READY are the same drawing at different opacity — only TALK gets a
 * panel. SPEAKER (someone else holding the channel) reuses TALK's layout with a
 * neutral panel, white circle and blue icon.
 */
enum class ChannelAudioMode { Idle, Talking, Listening }

/**
 * One channel tile on the dashboard: member count chip on top, mic below.
 *
 * [enabled] is false while the supervisor holds another channel's mic — you can
 * only talk on one at a time, so the rest fade back and stop responding, the same
 * treatment an empty channel gets.
 */
@Composable
fun ChannelCard(
    summary: ChannelSummary,
    audioMode: ChannelAudioMode,
    onMicTap: () -> Unit,
    onChipTap: () -> Unit,
    enabled: Boolean = true,
    /**
     * Reports where the mic and the member chip ended up, so the first-run tour
     * can cut a hole around them. Only the first card on Home bothers.
     */
    onMicBounds: (Rect) -> Unit = {},
    onChipBounds: (Rect) -> Unit = {},
    onCardBounds: (Rect) -> Unit = {}
) {
    val isActive = summary.isActive && enabled
    val contentAlpha = if (isActive) 1f else INACTIVE_ALPHA
    val isSpeaking = audioMode != ChannelAudioMode.Idle
    val isListening = audioMode == ChannelAudioMode.Listening

    // Staffed but nobody holding the mic — looks the same as an empty channel,
    // only at full opacity. Tracked separately because the mic IS tappable here,
    // which the accessibility label has to reflect.
    val isReady = isActive && !isSpeaking

    val activePanelColor = if (isListening) VListeningPanel else VActivePanel
    val activeCircleColor = if (isListening) Color.White else VMicBlue
    val activeIconColor = if (isListening) VMicBlue else Color.White
    val activeLabelColor = if (isListening) Color.White else VMicRing

    // Only a live channel gets a panel. A staffed-but-quiet one is separated from
    // an empty one by opacity alone, which is what the Figma grid shows.
    val panelColor = if (isSpeaking) activePanelColor else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .onGloballyPositioned { onCardBounds(it.boundsInRoot()) }
            .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = VShadow)
            .clip(RoundedCornerShape(16.dp))
            .background(VSurface)
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MemberCountChip(
                modifier = Modifier.onGloballyPositioned { onChipBounds(it.boundsInRoot()) },
                onlineCount = summary.onlineCount,
                totalCount = summary.totalCount,
                contentAlpha = contentAlpha,
                enabled = isActive,
                onClick = onChipTap
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                // Keep the complete mic control at 48 dp in every audio state.
                Box(
                    modifier = Modifier
                        .size(MIC_SLOT)
                        .onGloballyPositioned { onMicBounds(it.boundsInRoot()) },
                    contentAlignment = Alignment.Center
                ) {
                    // Every branch below draws into the same MIC_SLOT box at the
                    // same MIC_CIRCLE size, so mic and label hold their position.
                    if (isSpeaking) {
                        // Draw the ripple outside the fixed circle without moving the label.
                        SpeakingRipple(modifier = Modifier.requiredSize(72.dp), size = 72.dp, color = activeCircleColor)
                        ActiveMicCircle(
                            channelName = summary.channel.name,
                            isListening = isListening,
                            circleColor = activeCircleColor,
                            iconColor = activeIconColor
                        )
                    } else {
                        IdleMicCircle(
                            contentAlpha = contentAlpha,
                            // The mic is inert unless the channel is staffed and
                            // free, so do not tell a screen reader to tap it.
                            micLabel = when {
                                isReady -> "Talk to ${summary.channel.name}"
                                !enabled -> "${summary.channel.name} unavailable while talking"
                                else -> "${summary.channel.name} has no members"
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MIC_LABEL_GAP))

                Text(
                    text = summary.channel.name,
                    style = MaterialTheme.typography.headlineSmall,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VSurfaceVariant)
            .border(1.dp, VBorder, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.member_channel),
            contentDescription = null,
            tint = VOnSurface.copy(alpha = contentAlpha),
            modifier = Modifier.size(10.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$onlineCount/$totalCount",
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            color = VOnSurface.copy(alpha = contentAlpha)
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = VOnSurfaceSecondary.copy(alpha = contentAlpha),
            modifier = Modifier.size(10.dp)
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
            .size(MIC_CIRCLE)
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
                modifier = Modifier.size(MIC_ICON)
            )
        } else {
            // ic_mic is stroke-only (fillColor #00000000), so the talking icon is
            // an outline mic — filled would lose the shape against a solid circle.
            Icon(
                painter = painterResource(id = R.drawable.ic_mic),
                contentDescription = "Talking to $channelName",
                tint = iconColor,
                modifier = Modifier.size(MIC_ICON)
            )
        }
    }
}

/**
 * The mic when nobody is holding the channel: hollow circle, grey rim, white mic.
 *
 * An empty channel is the same drawing faded back — the rim fades with the icon,
 * which an unfaded [VBorder] would not do, leaving a crisp ring around a ghost.
 */
@Composable
private fun IdleMicCircle(contentAlpha: Float, micLabel: String) {
    Box(
        modifier = Modifier
            .size(MIC_CIRCLE)
            .clip(CircleShape)
            .background(VSurface)
            .border(1.dp, Color(0xFF707174).copy(alpha = contentAlpha), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = micLabel,
            tint = VOnSurface.copy(alpha = contentAlpha),
            modifier = Modifier.size(MIC_ICON)
        )
    }
}

/** How far an empty channel's contents fade back. */
private const val INACTIVE_ALPHA = 0.35f

/** All audio states use the same 48 dp circle and slot, keeping the label fixed. */
private val MIC_SLOT = 48.dp
private val MIC_CIRCLE = 48.dp
private val MIC_ICON = 24.dp

/** Space between the mic and the channel name. */
private val MIC_LABEL_GAP = 16.dp

/** Half-width, the way the card actually sits in Home's two-column grid. */
@Composable
private fun ChannelCardPreviewFrame(content: @Composable () -> Unit) {
    VikoTheme {
        Box(modifier = Modifier.width(200.dp).padding(12.dp)) { content() }
    }
}

/** Talking: navy panel, blue ring, outline mic, blue label — the Figma state. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ChannelCardTalkingPreview() {
    ChannelCardPreviewFrame {
        ChannelCard(previewChannelSummary(name = "Operations"), ChannelAudioMode.Talking, {}, {})
    }
}

/** Speaker: neutral panel, white circle, blue icon, white label. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ChannelCardListeningPreview() {
    ChannelCardPreviewFrame {
        ChannelCard(previewChannelSummary(name = "Operations"), ChannelAudioMode.Listening, {}, {})
    }
}

/** Ready: people on the channel, nobody talking — the new middle state. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ChannelCardReadyPreview() {
    ChannelCardPreviewFrame {
        ChannelCard(previewChannelSummary(name = "Warehouse"), ChannelAudioMode.Idle, {}, {})
    }
}

/** A channel with 5 members and nobody online — renders 0/5 and fully faded. */
private fun previewEmptyChannelSummary(name: String) = ChannelSummary(
    channel = previewChannel("c9", name),
    members = (1..5).map { previewWorker("1$it", "Member $it", "c9", isOnline = false) }
)

/**
 * The Figma grid, nobody holding a mic: one empty channel among three ready ones.
 * This is the layout the states have to be told apart in — checking them one at a
 * time hides the fact that empty and ready are the same drawing at different
 * opacity.
 */
@Preview(widthDp = 380, showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ChannelCardGridPreview() {
    ChannelCardGrid(talkingOnOperations = false)
}

/**
 * Holding Operations' mic: every other channel is faded and inert, so the grid
 * shows one live card among disabled ones — the state Home puts them in.
 */
@Preview(widthDp = 380, showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ChannelCardGridTalkingPreview() {
    ChannelCardGrid(talkingOnOperations = true)
}

@Composable
private fun ChannelCardGrid(talkingOnOperations: Boolean) {
    VikoTheme {
        Column(modifier = Modifier.padding(12.dp)) {
            listOf(
                previewChannelSummary(name = "Operations") to true,
                previewEmptyChannelSummary("Facilities") to false,
                previewChannelSummary(name = "Maintenance") to false,
                previewChannelSummary(name = "Security") to false
            ).chunked(2).forEach { row ->
                Row {
                    row.forEach { (summary, isOperations) ->
                        val talking = talkingOnOperations && isOperations
                        Box(modifier = Modifier.width(170.dp).padding(4.dp)) {
                            ChannelCard(
                                summary = summary,
                                audioMode = if (talking) ChannelAudioMode.Talking else ChannelAudioMode.Idle,
                                onMicTap = {},
                                onChipTap = {},
                                enabled = !talkingOnOperations || isOperations
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 0/5 — nobody on the channel. The whole tile fades and the mic stops responding. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ChannelCardEmptyPreview() {
    ChannelCardPreviewFrame {
        ChannelCard(previewEmptyChannelSummary("Facilities"), ChannelAudioMode.Idle, {}, {})
    }
}

/** A name long enough to test the ellipsis at half width. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ChannelCardLongNamePreview() {
    ChannelCardPreviewFrame {
        ChannelCard(previewChannelSummary(name = "Maintenance Crew"), ChannelAudioMode.Talking, {}, {})
    }
}
