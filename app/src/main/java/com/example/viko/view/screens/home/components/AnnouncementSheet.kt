package com.example.viko.view.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.R
import com.example.viko.components.ExpandingVoicePulse
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VikoTheme

/** Content-sized announcement UI preview; the modal host owns the surface and handle. */
@Composable
fun AnnouncementSheetContent(onClose: () -> Unit) {
    var isSpeaking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .78f)
            .clipToBounds()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(28.dp)) {
            Text("Announce", fontSize = 24.sp, lineHeight = 28.sp,
                fontWeight = FontWeight.Bold, color = VOnSurface,
                modifier = Modifier.align(Alignment.Center))
            IconButton(onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd).size(24.dp)) {
                Icon(Icons.Default.Close, "Close announcement", tint = VOnSurface, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(if (isSpeaking) "Speaking..." else "From Lee Suyeon (수연이)",
            fontSize = 14.sp, lineHeight = 20.sp,
            color = VOnSurfaceSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        // This wrapper keeps the circle fixed while rings expand past it and fade.
        Box(Modifier.fillMaxWidth().height(320.dp).testTag("announcement-icon-wrapper"),
            contentAlignment = Alignment.Center) {
            if (isSpeaking) {
                ExpandingVoicePulse(Modifier.matchParentSize().testTag("announcement-speaking-pulse"))
            }
            Box(
                Modifier.size(168.dp).clip(CircleShape)
                    .background(if (isSpeaking) VMicBlue else Color.White)
                    .clickable(role = Role.Button) { isSpeaking = !isSpeaking },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(if (isSpeaking) R.drawable.ic_announces else R.drawable.ic_announce_notactive),
                    contentDescription = if (isSpeaking) "Stop announcement" else "Start announcement",
                    tint = if (isSpeaking) Color.White else VMicBlue,
                    modifier = Modifier.size(44.dp)
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
