package com.example.viko.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/**
 * The read-only `Device Address` / `Device Status` card under the form on Setup
 * Member and Member Details. [status] is a slot because the status line carries
 * the battery glyph, not just text.
 */
@Composable
fun DeviceInfoCard(
    address: String,
    status: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DeviceInfoRow(label = "Device Address") {
            Text(
                text = address,
                fontSize = 12.sp,
                color = VOnSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DeviceInfoRow(label = "Device Status", value = status)
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = VOnSurface,
            modifier = Modifier.weight(1f)
        )
        value()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun DeviceInfoCardPreview() {
    VikoTheme {
        DeviceInfoCard(
            address = "1234546363SDF##SDF",
            status = { Text("Central Building • 70%", fontSize = 12.sp, color = VOnSurfaceSecondary) }
        )
    }
}
