package com.example.viko.view.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.R
import com.example.viko.components.SpeakingRipple
import com.example.viko.domain.channel.model.Channel
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun ChannelCardPreview() {
    VikoTheme {
        ChannelCard(
            channel = Channel(id = "c1", name = "Kitchen"),
            onlineCount = 3,
            totalCount = 5,
            isActive = true,
            audioMode = ChannelAudioMode.Listening,
            onMicTap = {},
            onChipTap = {}
        )
    }
}

@Composable
fun ChannelCard(
    channel: Channel,
    onlineCount: Int,
    totalCount: Int,
    isActive: Boolean,
    audioMode: ChannelAudioMode,
    onMicTap: () -> Unit,
    onChipTap: () -> Unit
) {
    val contentAlpha = if (isActive) 1f else 0.35f
    val isSpeaking = audioMode != ChannelAudioMode.Idle
    val isListening = audioMode == ChannelAudioMode.Listening

    // Figma splits the two active states:
    //   TALK    → navy panel, blue circle, white mic icon, BLUE label
    //   SPEAKER → neutral dark panel, white circle, blue icon, WHITE label
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
            // Member count chip (tap → team sheet)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VSurfaceVariant)
                    .border(1.dp, VBorder, RoundedCornerShape(20.dp))
                    .clickable(enabled = isActive, onClick = onChipTap)
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

            Spacer(modifier = Modifier.height(12.dp))

            // Mic + name area (tap mic → talk). Active state shows a blue panel,
            // a filled blue mic with ripple rings, and a blue channel name.
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
                    }
                    if (isSpeaking) {
                        // Filled circle: blue for talk, white for speaker
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(activeCircleColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isListening) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Listening to ${channel.name}",
                                    tint = activeIconColor,
                                    modifier = Modifier.size(30.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_mic),
                                    contentDescription = "Talking to ${channel.name}",
                                    tint = activeIconColor,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    } else {
                        // Outlined mic
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
                                contentDescription = "Talk to ${channel.name}",
                                tint = VOnSurface.copy(alpha = contentAlpha),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = channel.name,
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
