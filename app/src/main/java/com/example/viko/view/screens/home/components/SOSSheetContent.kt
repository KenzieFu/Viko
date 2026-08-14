package com.example.viko.view.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PropaneTank
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.components.SpeakingRipple
import com.example.viko.mock.AppState
import com.example.viko.view.theme.*
import kotlinx.coroutines.delay

@Preview(showBackground = true)
@Composable
private fun SOSSheetContentPreview() {
    VikoTheme {
        SOSSheetContent(appState = AppState(), onClose = {}, onDirectCall = {})
    }
}

@Composable
fun SOSSheetContent(
    appState: AppState,
    onClose: () -> Unit,
    onDirectCall: (String) -> Unit
) {
    val types = listOf(
        EmergencyType("fire", "Fire", Icons.Default.LocalFireDepartment, VAccentRed),
        EmergencyType("gas", "Gas Leak", Icons.Default.PropaneTank, VAccentRed),
        EmergencyType("evacuate", "Evacuate", Icons.AutoMirrored.Filled.ExitToApp, VAccentRed),
        EmergencyType("clear", "All Clear", Icons.Default.VerifiedUser, VAccentBlue),
    )

    var selectedType by remember { mutableStateOf<String?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    // Speaking auto-stops after a short broadcast
    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            delay(3500)
            isSpeaking = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSpeaking) VAccentRed.copy(alpha = 0.05f) else Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            // Header — centered title, close on right
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SOS",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = VOnSurface,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = VOnSurface)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2×2 emergency type grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(330.dp),
                userScrollEnabled = false
            ) {
                items(types) { type ->
                    EmergencyTypeCard(
                        type = type,
                        isSelected = selectedType == type.id,
                        dimmed = isSpeaking,
                        onClick = {
                            if (!isSpeaking) {
                                selectedType = if (selectedType == type.id) null else type.id
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Announce button (red) — shows ripple + "Speaking..." while broadcasting
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.size(if (isSpeaking) 132.dp else 88.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSpeaking) {
                        SpeakingRipple(size = 132.dp, color = VAccentRed)
                    }
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(VAccentRed)
                            .clickable { isSpeaking = !isSpeaking },
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
                    text = if (isSpeaking) "Speaking..." else "Announce",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSpeaking) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSpeaking) VAccentRed else VOnSurface
                )
            }
        }
    }
}
