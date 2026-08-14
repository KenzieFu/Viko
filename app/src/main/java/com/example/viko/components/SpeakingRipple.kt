package com.example.viko.components

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VikoTheme

@Composable
fun SpeakingRipple(
    modifier: Modifier = Modifier,
    color: Color = VAccentRed,
    size: Dp = 180.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.12f, targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "a1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.18f, targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(1500, delayMillis = 300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "a2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.24f, targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(1500, delayMillis = 600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "a3"
    )
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "s1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, delayMillis = 300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "s2"
    )
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, delayMillis = 600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "s3"
    )

    Canvas(
        modifier = modifier.size(size)
    ) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val maxRadius = this.size.minDimension / 2
        drawCircle(color.copy(alpha = alpha1), radius = maxRadius * scale1, center = center)
        drawCircle(color.copy(alpha = alpha2), radius = maxRadius * 0.75f * scale2, center = center)
        drawCircle(color.copy(alpha = alpha3), radius = maxRadius * 0.5f * scale3, center = center)
    }
}

@Preview
@Composable
private fun SpeakingRipplePreview() {
    VikoTheme {
        SpeakingRipple()
    }
}
