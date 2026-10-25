package com.example.viko.view.screens.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VikoTheme

/** Two translucent halos pulse behind the fixed 48 dp circle without affecting layout. */
@Composable
internal fun ChannelSpeakingPulse(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "Channel speaking pulse")
    val scale by transition.animateFloat(
        initialValue = .9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Halo expansion"
    )
    Canvas(modifier.size(66.dp)) {
        drawCircle(color.copy(alpha = .10f), radius = size.minDimension / 2f * scale)
        drawCircle(color.copy(alpha = .10f), radius = size.minDimension / 2f * (58f / 66f) * scale)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C284A)
@Composable
private fun ChannelSpeakingPulsePreview() {
    VikoTheme { ChannelSpeakingPulse(VMicBlue) }
}
