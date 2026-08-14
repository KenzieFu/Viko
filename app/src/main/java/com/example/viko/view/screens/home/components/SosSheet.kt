package com.example.viko.view.screens.home.components

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
import com.example.viko.components.SpeakingRipple
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
 * The emergency grid drives how tall the sheet is, so raising [EMERGENCY_CARD_HEIGHT]
 * is how you give the sheet more room.
 *
 * [EMERGENCY_GRID_HEIGHT] is derived rather than typed out: the grid does not size
 * itself to its rows, so a hardcoded height that no longer matched the cards would
 * silently clip them instead of failing.
 */
// Declared before EMERGENCY_GRID_HEIGHT on purpose: top-level vals initialise in
// file order, so a height computed from properties defined below it would read
// them as zero.
/** Four types in two columns. */
private const val EMERGENCY_GRID_COLUMNS = 2
private const val EMERGENCY_GRID_ROWS = 2

private val EMERGENCY_CARD_HEIGHT = 165.dp
private val EMERGENCY_GRID_SPACING = 16.dp
private val EMERGENCY_GRID_HEIGHT =
    EMERGENCY_CARD_HEIGHT * EMERGENCY_GRID_ROWS + EMERGENCY_GRID_SPACING * (EMERGENCY_GRID_ROWS - 1)

/**
 * The Announce button and the box it sits in. The box is the larger of the two so
 * the ripple has somewhere to go without the button moving when it appears.
 */
private val ANNOUNCE_BUTTON = 88.dp
private val ANNOUNCE_SLOT = 132.dp

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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isBroadcasting) VAccentRed.copy(alpha = 0.05f) else Color.Transparent)
    ) {
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
                // Reserves the ripple's room whether or not it is drawing. Sized to
                // fit, starting a broadcast grew this box 88 -> 132.dp, which shoved
                // the button up and the label down — and, since the sheet is
                // content-sized, resized the whole sheet under the user's thumb.
                Box(
                    modifier = Modifier.size(ANNOUNCE_SLOT),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBroadcasting) {
                        SpeakingRipple(size = ANNOUNCE_SLOT, color = VAccentRed)
                    }
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
                            contentDescription = "Announce",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
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
            .background(if (isSelected) type.accent.copy(alpha = 0.08f) else VSurface)
            .border(
                width = 1.5.dp,
                color = if (isSelected) type.accent.copy(alpha = 0.4f) else VBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = !dimmed, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) type.accent else type.accent.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    type.icon,
                    contentDescription = type.label,
                    tint = if (isSelected) Color.White else type.accent,
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
