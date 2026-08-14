package com.example.viko.view.screens.channel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun ChannelRowPreview() {
    VikoTheme { ChannelRow(name = "Kitchen", memberCount = 3, isSelected = true, onClick = {}) }
}

@Composable
fun ChannelRow(
    name: String,
    memberCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = VOnSurface
            )
            if (memberCount > 0) {
                Text(
                    text = "$memberCount member${if (memberCount == 1) "" else "s"}",
                    fontSize = 11.sp,
                    color = VOnSurfaceSecondary
                )
            }
        }
        if (isSelected) {
            Icon(Icons.Default.Check, "Selected", tint = VMicBlue, modifier = Modifier.size(20.dp))
        }
    }
}
