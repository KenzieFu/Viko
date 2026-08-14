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
            animation = tween(durationMillis = WAVE_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveProgress"
    )

    Canvas(modifier = modifier) {
        // Centred below the bottom edge, so only the tops of the rings show and
        // they read as sound rising out of the button rather than as circles.
        val center = Offset(size.width / 2f, size.height * WAVE_ORIGIN_Y)
        val maxRadius = size.width * WAVE_MAX_RADIUS

        repeat(WAVE_RING_COUNT) { index ->
            // Each ring runs the same small -> big sweep, evenly offset in time, so
            // one is always starting as another finishes.
            val ringProgress = (progress + index.toFloat() / WAVE_RING_COUNT) % 1f
            drawCircle(
                // Brightest when small, gone by the time it is full size.
                color = color.copy(alpha = WAVE_MAX_ALPHA * (1f - ringProgress)),
                radius = maxRadius * (WAVE_MIN_RADIUS + (1f - WAVE_MIN_RADIUS) * ringProgress),
                center = center
            )
        }
    }
}

/**
 * How tall the wave area should be. Both the announcement and the SOS sheet use
 * this rather than their own number, so the pulse is the same size in both — the
 * arcs are sized off the box, so two different heights would be two different
 * pulses.
 */
val AnnouncementWaveHeight = 236.dp

private const val WAVE_PERIOD_MS = 1700
private const val WAVE_RING_COUNT = 3

/**
 * Where the rings are centred, as a fraction of the wave's height. Past 1f puts
 * the origin below the bottom edge so only the crowns of the arcs show.
 *
 * Together with [WAVE_MAX_RADIUS] this is what keeps the pulse in the lower part
 * of the sheet: origin 1.15 minus a 0.72-of-width radius leaves the biggest arc
 * just inside the top of the wave area. Push the radius up or the origin down and
 * the arcs climb out of it and wash over the whole sheet.
 */
private const val WAVE_ORIGIN_Y = 1.15f

/** Largest a ring gets, as a fraction of the wave's width. */
private const val WAVE_MAX_RADIUS = 0.72f

/** Size a ring is born at, as a fraction of [WAVE_MAX_RADIUS]. */
private const val WAVE_MIN_RADIUS = 0.40f

/** Alpha of a newborn ring; it fades linearly to zero as it expands. */
private const val WAVE_MAX_ALPHA = 0.18f

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun AnnouncementWaveBackgroundPreview() {
    VikoTheme {
        AnnouncementWaveBackground(modifier = Modifier.size(240.dp))
    }
}
