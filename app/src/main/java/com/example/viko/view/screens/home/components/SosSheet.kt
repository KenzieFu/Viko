package com.example.viko.view.screens.home.components

import com.example.viko.view.theme.VSurfaceVariant
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.components.AnnouncementWaveBackground
import com.example.viko.components.AnnouncementWaveHeight
import com.example.viko.view.theme.VikoTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PropaneTank
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.VAccentBlue
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VShadow
import com.example.viko.view.theme.VSurface
import kotlinx.coroutines.delay

private data class EmergencyType(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accent: Color
)

private val EMERGENCY_TYPES = listOf(
    EmergencyType("fire", "Fire", Icons.Default.LocalFireDepartment, VAccentRed),
    EmergencyType("gas", "Gas Leak", Icons.Default.PropaneTank, VAccentRed),
    EmergencyType("evacuate", "Evacuate", Icons.AutoMirrored.Filled.ExitToApp, VAccentRed),
    EmergencyType("clear", "All Clear", Icons.Default.VerifiedUser, VAccentBlue),
)

/** How long a site-wide emergency broadcast runs before it auto-stops. */
private const val BROADCAST_DURATION_MS = 3_500L

/**
 * The emergency grid drives how tall the sheet is, so [EMERGENCY_CARD_HEIGHT] is
 * the one knob for giving the sheet more room.
 *
 * [EMERGENCY_GRID_HEIGHT] is derived rather than typed out: LazyVerticalGrid does
 * not size itself to its rows, so a hardcoded height that no longer matched the
 * cards would silently clip them instead of failing.
 *
 * The row/column counts come first because top-level properties initialise in file
 * order — a height computed from values declared below it would read them as zero.
 */
private const val EMERGENCY_GRID_COLUMNS = 2
private const val EMERGENCY_GRID_ROWS = 2

private val EMERGENCY_CARD_HEIGHT = 165.dp
private val EMERGENCY_GRID_SPACING = 16.dp
private val EMERGENCY_GRID_HEIGHT =
    EMERGENCY_CARD_HEIGHT * EMERGENCY_GRID_ROWS + EMERGENCY_GRID_SPACING * (EMERGENCY_GRID_ROWS - 1)

private val ANNOUNCE_BUTTON = 88.dp

/**
 * Site-wide emergency broadcast: pick a type, hold the floor for a few seconds.
 * Nothing is sent yet — this is the UI ahead of the emergency packet transport.
 */
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

    // The sheet surface never changes — only the wave reacts, same as the
    // announcement sheet.
    Box(modifier = Modifier.fillMaxWidth()) {
        // Same wave the announcement sheet uses, in red — arcs sweeping up from
        // the bottom of the sheet rather than a halo around the button, so the
        // whole surface reads as live. Drawn behind the content, and it takes no
        // layout space, so nothing moves when a broadcast starts.
        if (isBroadcasting) {
            AnnouncementWaveBackground(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(AnnouncementWaveHeight),
                color = VAccentRed
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SOS",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = VOnSurface,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Default.Close, "Close", tint = VOnSurface)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(EMERGENCY_GRID_COLUMNS),
                horizontalArrangement = Arrangement.spacedBy(EMERGENCY_GRID_SPACING),
                verticalArrangement = Arrangement.spacedBy(EMERGENCY_GRID_SPACING),
                modifier = Modifier.height(EMERGENCY_GRID_HEIGHT),
                userScrollEnabled = false
            ) {
                items(EMERGENCY_TYPES) { type ->
                    EmergencyTypeCard(
                        type = type,
                        isSelected = selectedType == type.id,
                        dimmed = isBroadcasting,
                        onClick = {
                            selectedType = if (selectedType == type.id) null else type.id
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // No halo slot to reserve any more — the wave behind the sheet does
                // the work, so the button is just the button and cannot move.
                Box(
                    modifier = Modifier
                        .size(ANNOUNCE_BUTTON)
                        .clip(CircleShape)
                        .background(VAccentRed)
                        .clickable { isBroadcasting = !isBroadcasting },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Campaign,
                        contentDescription = if (isBroadcasting) "Stop broadcasting" else "Announce",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isBroadcasting) "Speaking..." else "Announce",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isBroadcasting) FontWeight.Bold else FontWeight.Medium,
                    color = if (isBroadcasting) VAccentRed else VOnSurface
                )
            }
        }
    }
}

@Composable
private fun EmergencyTypeCard(
    type: EmergencyType,
    isSelected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(EMERGENCY_CARD_HEIGHT)
            .alpha(if (dimmed) 0.4f else 1f)
            .shadow(1.dp, RoundedCornerShape(16.dp), spotColor = VShadow)
            .clip(RoundedCornerShape(16.dp))
            // Unselected carries no accent at all — the card is just a lighter
            // surface than the sheet, which is what separates it now that there is
            // no outline. VSurface would be invisible here: the sheet is VSurface.
            .background(if (isSelected) type.accent.copy(alpha = 0.08f) else VSurfaceVariant)
            .then(
                if (isSelected) Modifier.border(
                    width = 1.5.dp,
                    color = type.accent.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .clickable(enabled = !dimmed, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Unselected: hollow, hairline ring, neutral glyph — an emergency type
            // reads as armed only once it has been chosen, so four red icons cannot
            // sit there looking like four live alarms.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) type.accent else Color.Transparent)
                    .then(
                        if (isSelected) Modifier
                        else Modifier.border(1.dp, VBorder, CircleShape)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    type.icon,
                    contentDescription = type.label,
                    tint = if (isSelected) Color.White else VOnSurfaceSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = type.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) type.accent else VOnSurface
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun SosSheetContentPreview() {
    VikoTheme { SosSheetContent(onClose = {}) }
}
