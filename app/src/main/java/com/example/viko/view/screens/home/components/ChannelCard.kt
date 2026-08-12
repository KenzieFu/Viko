package com.example.viko.view.screens.home.components

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
import androidx.compose.material.icons.outlined.People
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
import com.example.viko.view.theme.VAccentBlue
import com.example.viko.view.theme.VActivePanel
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VListeningPanel
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VShadow
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VSurfaceVariant

/**
 * Figma splits the two active states:
 *   TALK    → navy panel, blue circle, white mic icon, BLUE label
 *   SPEAKER → neutral dark panel, white circle, blue icon, WHITE label
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
                        ActiveMicCircle(
                            channelName = summary.channel.name,
                            isListening = isListening,
                            circleColor = activeCircleColor,
                            iconColor = activeIconColor
                        )
                    } else {
                        IdleMicCircle(
                            channelName = summary.channel.name,
                            contentAlpha = contentAlpha
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = summary.channel.name,
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
            .background(circleColor),
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
            Icon(
                painter = painterResource(id = R.drawable.ic_mic),
                contentDescription = "Talking to $channelName",
                tint = iconColor,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun IdleMicCircle(channelName: String, contentAlpha: Float) {
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
            contentDescription = "Talk to $channelName",
            tint = VOnSurface.copy(alpha = contentAlpha),
            modifier = Modifier.size(28.dp)
        )
    }
}

/** How far an offline channel's contents fade back. */
private const val INACTIVE_ALPHA = 0.35f
