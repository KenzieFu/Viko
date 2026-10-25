package com.example.viko.view.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PropaneTank
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.R
import com.example.viko.components.AnnouncementWaveBackground
import com.example.viko.components.AnnouncementWaveHeight
import com.example.viko.view.theme.*
import kotlinx.coroutines.delay

private data class EmergencyType(val id: String, val label: String, val icon: ImageVector, val accent: Color)

private val EMERGENCY_TYPES = listOf(
    EmergencyType("fire", "Fire", Icons.Default.LocalFireDepartment, VAccentRed),
    EmergencyType("gas", "Gas Leak", Icons.Default.PropaneTank, VAccentRed),
    EmergencyType("evacuate", "Evacuate", Icons.AutoMirrored.Filled.ExitToApp, VAccentRed),
    EmergencyType("clear", "All Clear", Icons.Default.VerifiedUser, VMicBlue),
)

private const val BROADCAST_DURATION_MS = 3_500L

/** Content-sized SOS sheet. This is the existing local UI preview; no emergency transport is sent. */
@Composable
fun SosSheetContent(onClose: () -> Unit) {
    var selectedType by remember { mutableStateOf<String?>(null) }
    var isBroadcasting by remember { mutableStateOf(false) }

    LaunchedEffect(isBroadcasting) {
        if (isBroadcasting) {
            delay(BROADCAST_DURATION_MS)
            isBroadcasting = false
        }
    }

    // Leave Home visible above the sheet. Small screens / enlarged text can scroll
    // instead of expanding the modal to full-screen or clipping the Announce control.
    Box(
        Modifier.fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .78f)
            .clip(RoundedCornerShape(0.dp))
            .testTag("sos-content")
    ) {
        if (isBroadcasting) {
            AnnouncementWaveBackground(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .height(AnnouncementWaveHeight).testTag("sos-speaking-wave"),
                color = VAccentRed
            )
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                Text("SOS", fontSize = 20.sp, lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold, color = VOnSurface,
                    modifier = Modifier.align(Alignment.Center))
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Default.Close, "Close SOS", tint = VOnSurface, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EMERGENCY_TYPES.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { type ->
                            EmergencyTypeCard(type, selectedType == type.id, isBroadcasting,
                                modifier = Modifier.weight(1f), onClick = {
                                    selectedType = if (selectedType == type.id) null else type.id
                                })
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(104.dp).clip(CircleShape)
                        .background(if (isBroadcasting) VAccentRed else VSurface)
                        .clickable(role = Role.Button) { isBroadcasting = !isBroadcasting },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_announces),
                        contentDescription = if (isBroadcasting) "Stop broadcasting" else "Announce",
                        tint = Color.White, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(if (isBroadcasting) "Speaking..." else "Announce",
                    fontSize = 18.sp, lineHeight = 22.sp,
                    fontWeight = if (isBroadcasting) FontWeight.Bold else FontWeight.Medium,
                    color = if (isBroadcasting) VAccentRed else VOnSurface)
            }
        }
    }
}

@Composable
private fun EmergencyTypeCard(
    type: EmergencyType,
    isSelected: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier.height(160.dp).alpha(if (dimmed) .4f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) VBackground else VSurface)
            .border(1.dp, VBorder, RoundedCornerShape(16.dp))
            .semantics { selected = isSelected }
            .clickable(enabled = !dimmed, role = Role.Button, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) {
                    if (type.id == "clear") VActivePanel else type.accent.copy(alpha = .15f)
                } else Color.Transparent),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (isSelected && !dimmed) {
                    ChannelSpeakingPulse(type.accent, Modifier.requiredSize(66.dp))
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(if (isSelected) type.accent else Color.Transparent)
                        .then(if (isSelected) Modifier else Modifier.border(1.dp, VOnSurface.copy(alpha = .3f), CircleShape)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(type.icon, null, tint = VOnSurface, modifier = Modifier.size(24.dp))
                }
            }
            Text(type.label, fontSize = 16.sp, lineHeight = 20.sp,
                color = if (isSelected) type.accent else VOnSurface)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun SosSheetContentPreview() {
    VikoTheme { SosSheetContent(onClose = {}) }
}
