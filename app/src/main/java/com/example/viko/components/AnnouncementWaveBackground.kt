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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.VAccentBlue
import com.example.viko.view.theme.VikoTheme

/**
 * The expanding rings behind a live announcement — drawn low in the sheet so it
 * reads as sound coming up out of the talk button.
 *
 * [color] defaults to the announcement blue; the SOS sheet passes red so an
 * emergency broadcast is never mistaken for a routine one.
 */
@Composable
fun AnnouncementWaveBackground(
    modifier: Modifier = Modifier,
    color: Color = VAccentBlue
) {
    val progress by rememberInfiniteTransition(label = "announcementWave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveProgress"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height * 1.24f)
        val baseRadii = listOf(0.39f, 0.56f, 0.72f)
        baseRadii.forEachIndexed { index, baseRadius ->
            val shiftedProgress = (progress + index * 0.22f) % 1f
            val alpha = (0.08f * (1f - shiftedProgress)).coerceAtLeast(0.02f)
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = size.width * (baseRadius + shiftedProgress * 0.09f),
                center = center
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun AnnouncementWaveBackgroundPreview() {
    VikoTheme {
        AnnouncementWaveBackground(modifier = Modifier.size(240.dp))
    }
}
