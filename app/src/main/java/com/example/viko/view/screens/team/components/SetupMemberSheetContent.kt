package com.example.viko.view.screens.team.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.components.VikoBlueButton
import com.example.viko.components.VikoTextField
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun SetupMemberSheetContentPreview() {
    VikoTheme {
        SetupMemberSheetContent(
            device = NearbyDevice(id = "nd1", name = "VIKO-ESP32-A1B2", address = "AA:BB:CC:11:22:33"),
            name = "Jane Doe",
            onNameChange = {},
            channelName = "Kitchen",
            onPickChannel = {},
            onSave = {},
            onClose = {}
        )
    }
}

@Composable
fun SetupMemberSheetContent(
    device: NearbyDevice?,
    name: String,
    onNameChange: (String) -> Unit,
    channelName: String?,
    onPickChannel: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    // Figma greys the Save button until BOTH a name and a channel are set. The
    // old screen let you save an empty form and quietly named the person
    // "New Worker", which is how blank members got created.
    val canSave = name.isNotBlank() && channelName != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
            .navigationBarsPadding()
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        DeviceIllustration(modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(modifier = Modifier.height(20.dp))

        // Name + Channel group
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("Name", fontSize = 11.sp, color = VOnSurfaceSecondary)
                VikoTextField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = "Enter Member Name",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = VBorder)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickChannel)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Channel", fontSize = 11.sp, color = VOnSurfaceSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = channelName ?: "Select Channel",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (channelName != null) VOnSurface else VOnSurfaceSecondary
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = VOnSurfaceSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Read-only device facts
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VSurfaceVariant)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SetupInfoRow("Device Address", device?.address ?: "Unknown")
            SetupInfoRow(
                "Device Status",
                "${device?.location ?: "Unknown"} • ${device?.batteryLevel ?: 0}%"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        VikoBlueButton(text = "Save", enabled = canSave, onClick = onSave)
    }
}
