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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.VAccentBlue
import com.example.viko.view.theme.VikoTheme

@Preview(showBackground = true)
@Composable
private fun AnnouncementWaveBackgroundPreview() {
    VikoTheme { AnnouncementWaveBackground(modifier = Modifier.size(240.dp)) }
}

@Composable
fun AnnouncementWaveBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "announcementWave")
    val progress by infiniteTransition.animateFloat(
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
                color = VAccentBlue.copy(alpha = alpha),
                radius = size.width * (baseRadius + shiftedProgress * 0.09f),
                center = center
            )
        }
    }
}
