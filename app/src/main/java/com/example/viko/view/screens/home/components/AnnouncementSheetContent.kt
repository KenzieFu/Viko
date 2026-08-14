package com.example.viko.view.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.R
import com.example.viko.components.VikoDragHandle
import com.example.viko.mock.AppState
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun AnnouncementSheetContentPreview() {
    VikoTheme {
        AnnouncementSheetContent(appState = AppState(), onClose = {})
    }
}

@Composable
fun AnnouncementSheetContent(
    appState: AppState,
    onClose: () -> Unit
) {
    var isSpeaking by remember { mutableStateOf(false) }
    val sheetBackground = if (isSpeaking) VListeningPanel else VSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(384.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(sheetBackground)
            .navigationBarsPadding()
    ) {
        if (isSpeaking) {
            AnnouncementWaveBackground(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(236.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VikoDragHandle()
            Spacer(modifier = Modifier.height(20.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Announce",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = VOnSurface,
                    modifier = Modifier.align(Alignment.Center)
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = VOnSurface,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (isSpeaking) "Speaking..." else "From Lee Suyeon (수연이)",
                style = MaterialTheme.typography.headlineMedium,
                color = VOnSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(if (isSpeaking) 64.dp else 66.dp))

            Box(
                modifier = Modifier.size(if (isSpeaking) 118.dp else 120.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isSpeaking) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .shadow(2.dp, CircleShape, spotColor = VShadow)
                            .clip(CircleShape)
                            .background(VSurface)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(if (isSpeaking) 118.dp else 120.dp)
                        .clip(CircleShape)
                        .background(if (isSpeaking) VAccentBlue else VSurface)
                        .border(
                            width = if (isSpeaking) 0.dp else 1.dp,
                            color = VBorder,
                            shape = CircleShape
                        )
                        .clickable { isSpeaking = !isSpeaking },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_announce),
                        contentDescription = if (isSpeaking) "Stop announcement" else "Start announcement",
                        tint = if (isSpeaking) Color.White else VAccentBlue,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            if (!isSpeaking) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
