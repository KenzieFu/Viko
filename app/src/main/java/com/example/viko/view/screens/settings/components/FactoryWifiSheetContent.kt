package com.example.viko.view.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.components.LabeledTextField
import com.example.viko.components.VikoBlueButton
import com.example.viko.domain.settings.model.FactoryWifi
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VikoTheme

/**
 * The factory's one Wi-Fi network, in the Add New Channel sheet's style. Shown
 * from Settings, and from Setup Member when pairing starts before it is set.
 *
 * Edits are local until Save, so closing the sheet changes nothing.
 */
@Composable
fun FactoryWifiSheetContent(
    current: FactoryWifi,
    onSave: (FactoryWifi) -> Unit,
    onClose: () -> Unit
) {
    var ssid by rememberSaveable { mutableStateOf(current.ssid) }
    // The password is not rememberSaveable: saved instance state is written to
    // disk on process death, and a factory password should not sit there.
    var password by remember { mutableStateOf(current.password) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 24.dp)
            .navigationBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Factory Wi-Fi",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = VOnSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        Text(
            // A badge keeps the network it was paired with, so this is worth
            // saying before someone "fixes" a password and expects the floor to
            // follow.
            text = "Every earpiece joins this network when it is paired. Earpieces " +
                "already paired keep the network they were given.",
            style = MaterialTheme.typography.bodyMedium,
            color = VOnSurfaceSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        LabeledTextField(
            label = "Network Name",
            value = ssid,
            onValueChange = { ssid = it },
            placeholder = "Enter Wi-Fi Name"
        )

        Spacer(modifier = Modifier.height(16.dp))

        LabeledTextField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            placeholder = "Enter Wi-Fi Password",
            isPassword = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        VikoBlueButton(
            text = "Save",
            enabled = ssid.isNotBlank(),
            onClick = { onSave(FactoryWifi(ssid = ssid.trim(), password = password)) }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun FactoryWifiSheetContentPreview() {
    VikoTheme {
        FactoryWifiSheetContent(current = FactoryWifi(), onSave = {}, onClose = {})
    }
}
