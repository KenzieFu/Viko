package com.example.viko.view.screens.team.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun SetupInfoRowPreview() {
    VikoTheme { SetupInfoRow(label = "Device", value = "VIKO-ESP32-A1B2") }
}

@Composable
fun SetupInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = VOnSurfaceSecondary, fontSize = 13.sp)
        Text(value, color = VOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
