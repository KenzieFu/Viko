package com.example.viko.components

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VikoTheme
import kotlin.math.hypot

/** Shared voice halos expand behind their content and fade completely at the outer edge. */
@Composable
fun ExpandingVoicePulse(
    modifier: Modifier = Modifier,
    color: Color = VMicBlue,
    innerRadius: Dp = 84.dp,
    outerRadius: Dp? = null,
    maxAlpha: Float = .14f
) {
    val progress by rememberInfiniteTransition(label = "Voice pulse").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "Outward wave"
    )
    Canvas(modifier) {
        val startRadius = innerRadius.toPx()
        val endRadius = maxOf(startRadius, outerRadius?.toPx() ?: hypot(size.width / 2f, size.height / 2f))
        repeat(3) { index ->
            val phase = (progress + index / 3f) % 1f
            drawCircle(
                color = color.copy(alpha = maxAlpha * (1f - phase)),
                radius = startRadius + (endRadius - startRadius) * phase,
                center = center
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ExpandingVoicePulsePreview() {
    VikoTheme { ExpandingVoicePulse(Modifier.size(360.dp, 320.dp)) }
}
