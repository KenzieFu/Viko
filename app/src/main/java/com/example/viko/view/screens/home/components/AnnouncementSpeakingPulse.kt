package com.example.viko.view.screens.home.components

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VikoTheme
import kotlin.math.hypot

/** Three staggered waves start at the 168 dp circle, expand outward, and fade to zero. */
@Composable
internal fun AnnouncementSpeakingPulse(modifier: Modifier = Modifier) {
    val progress by rememberInfiniteTransition(label = "Announcement pulse").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "Outward wave"
    )
    Canvas(modifier) {
        val startRadius = 84.dp.toPx()
        val endRadius = maxOf(startRadius, hypot(size.width / 2f, size.height / 2f))
        repeat(3) { index ->
            val phase = (progress + index / 3f) % 1f
            drawCircle(
                color = VMicBlue.copy(alpha = .14f * (1f - phase)),
                radius = startRadius + (endRadius - startRadius) * phase,
                center = center
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun AnnouncementSpeakingPulsePreview() {
    VikoTheme { AnnouncementSpeakingPulse(Modifier.size(360.dp, 320.dp)) }
}
