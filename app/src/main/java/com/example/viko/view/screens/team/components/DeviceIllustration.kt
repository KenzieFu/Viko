package com.example.viko.view.screens.team.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun DeviceIllustrationPreview() {
    VikoTheme { DeviceIllustration() }
}

/**
 * Stand-in for the 3D earpiece render in Figma. Replace with the real asset
 * once design exports it — drop a PNG in res/drawable and swap this out.
 */
@Composable
fun DeviceIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(112.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(VOnSurface.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(VSurfaceVariant)
                .border(3.dp, VOffline, CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(VWarning)
        )
    }
}
