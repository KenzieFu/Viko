package com.example.viko.view.screens.home.components

import com.example.viko.view.theme.VikoTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.viko.R
import com.example.viko.components.AnnouncementWaveBackground
import com.example.viko.components.AnnouncementWaveHeight
import com.example.viko.components.VikoDragHandle
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary

private val ANNOUNCE_BUTTON = 168.dp

/** Space between the "From …" line and the button. */
private val ANNOUNCE_BUTTON_GAP = 96.dp

/**
 * All-channel announcement. Draws its own drag handle and corner radius because
 * the host passes a transparent container so this can own the whole surface.
 *
 * The surface itself does not change while speaking — the wave is the only thing
 * that reacts, which keeps this consistent with the SOS sheet.
 */
@Composable
fun AnnouncementSheetContent(onClose: () -> Unit) {
    var isSpeaking by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(470.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(VBackground)
            .navigationBarsPadding()
    ) {
        if (isSpeaking) {
            AnnouncementWaveBackground(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(AnnouncementWaveHeight)
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
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = VOnSurface,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = VOnSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (isSpeaking) "Speaking..." else "From Lee Suyeon (수연이)",
                style = MaterialTheme.typography.bodyMedium,
                color = VOnSurfaceSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(ANNOUNCE_BUTTON_GAP))

            // Size and gap are the same in both states so the button cannot shift
            // when you tap it — only the fill and glyph swap: white with a blue
            // megaphone at rest, blue with a white one while speaking.
            Box(
                modifier = Modifier
                    .size(ANNOUNCE_BUTTON)
                    .clip(CircleShape)
                    .background(if (isSpeaking) VMicBlue else Color.White)
                    .clickable { isSpeaking = !isSpeaking },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_announce),
                    contentDescription = if (isSpeaking) "Stop announcement" else "Start announcement",
                    tint = if (isSpeaking) Color.White else VMicBlue,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun AnnouncementSheetContentPreview() {
    VikoTheme { AnnouncementSheetContent(onClose = {}) }
}
