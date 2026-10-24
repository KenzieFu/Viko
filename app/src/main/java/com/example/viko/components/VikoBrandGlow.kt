package com.example.viko.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The blue bloom behind the splash and onboarding screens.
 *
 * In Figma this is a solid #1692FF circle under a 200px Gaussian blur, with two
 * multiply-blended ellipses darkening its edges. A blurred solid circle is a
 * radial falloff, so it is drawn as one here rather than shipped as a ~1165px
 * PNG per density — a vector drawable cannot carry the blur, and the multiply
 * pass only matters against the page colour, which is already [glowColor]'s
 * backdrop.
 */
@Composable
fun VikoBrandGlow(
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFF1692FF)
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Sits above the top edge, so only its lower half washes into the page.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width * 0.54f, size.height * -0.02f),
                radius = size.width * 0.95f
            ),
            radius = size.width * 0.95f,
            center = Offset(size.width * 0.54f, size.height * -0.02f)
        )
        // And its mirror below the bottom edge, which is what keeps the middle
        // of the screen the darkest part of it.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 1.05f),
                radius = size.width * 0.85f
            ),
            radius = size.width * 0.85f,
            center = Offset(size.width * 0.5f, size.height * 1.05f)
        )
    }
}
