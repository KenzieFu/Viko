package com.example.viko.view.screens.team

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.components.*
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.view.theme.*

@Composable
internal fun SetupMemberScreenContent(
    device: NearbyDevice?,
    name: String,
    onNameChange: (String) -> Unit,
    zone: String,
    onZoneChange: (String) -> Unit,
    isWifiConfigured: Boolean,
    onSetUpWifi: () -> Unit,
    channelName: String?,
    isChannelLocked: Boolean,
    canSave: Boolean,
    isProvisioning: Boolean,
    error: String?,
    onPickChannel: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
            .padding(top = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        // The form scrolls; Save stays pinned at the bottom where Figma has it,
        // however many fields the badge needs above it.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            DeviceIllustration(modifier = Modifier.align(Alignment.CenterHorizontally))

            Spacer(modifier = Modifier.height(24.dp))

            LabeledTextField(
                label = "Name",
                value = name,
                onValueChange = onNameChange,
                placeholder = "Enter Member Name"
            )

            Spacer(modifier = Modifier.height(16.dp))

            LabeledSelectField(
                label = "Channel",
                value = channelName,
                placeholder = "Select Channel",
                // Opened from a channel's own team sheet: the channel is already
                // decided, so the row is shown locked rather than as a picker.
                onClick = if (isChannelLocked) null else onPickChannel
            )

            Spacer(modifier = Modifier.height(16.dp))

            // The zone goes onto the badge, not just the roster: it is what an
            // emergency_alert reports, and it is how a supervisor knows where to
            // run before anything has been translated.
            LabeledTextField(
                label = "Location",
                value = zone,
                onValueChange = onZoneChange,
                placeholder = "e.g. Central Building"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // The badge has no keyboard and no screen, so pairing is the only
            // moment it can be given the factory network — one network for the
            // whole site, kept in Settings. Normally already set, and then this
            // row is not shown at all; on a fresh install it is the one thing
            // standing between Save and a working badge.
            if (!isWifiConfigured) {
                LabeledSelectField(
                    label = "Factory Wi-Fi",
                    value = null,
                    placeholder = "Set up factory Wi-Fi",
                    onClick = onSetUpWifi
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            DeviceInfoCard(
                address = device?.address ?: "Unknown",
                status = {
                    Text(
                        // Battery and location only arrive once the badge is on Wi-Fi
                        // and registers; before that, signal is all BLE can tell us.
                        text = device?.let { if (it.id == "demo") "Preview only" else "Pairing • ${it.signalStrength} dBm" } ?: "Unknown",
                        fontSize = 12.sp,
                        color = VOnSurfaceSecondary
                    )
                }
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = VWarning
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)) {
            // Figma dims Save until the form is complete — see AddMemberViewModel.save
            // for why that matters. Saving here also writes the badge over BLE,
            // which takes a few seconds and can fail.
            VikoBlueButton(
                text = if (isProvisioning) "Saving…" else "Save",
                enabled = canSave,
                onClick = onSave
            )

            if (isProvisioning) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = VMicBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Keep the wearable nearby — it switches to Wi-Fi when setup finishes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VOnSurfaceSecondary
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun SetupMemberScreenPreview() {
    VikoTheme { SetupMemberScreenContent(NearbyDevice("demo", "ViKo-A1B2", "AA:BB:CC:11:22:33"),
        "Le Khanh Linh", {}, "Central Building", {}, true, {}, "Operations", true, true, false, null, {}, {}, {}) }
}
