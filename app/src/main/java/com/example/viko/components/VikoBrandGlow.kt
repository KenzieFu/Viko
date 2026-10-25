package com.example.viko.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.*
import kotlin.math.exp

/** Figma's two off-screen discs with soft outer shadows over the #1692FF bloom. */
@Composable
fun VikoBrandGlow(modifier: Modifier = Modifier, glowColor: Color = VOnboardingBlue) {
    Canvas(modifier.fillMaxSize()) {
        val scale = size.width / 360f
        for ((center, alpha) in listOf(
            Offset(size.width * .54f, -65f * scale) to .20f,
            Offset(size.width * .5f, size.height + 90f * scale) to .08f
        )) {
            val radius = 440f * scale
            drawCircle(Brush.radialGradient(listOf(glowColor.copy(alpha = alpha), Color.Transparent),
                center = center, radius = radius), radius, center)
        }
        // Draw only the outer Gaussian shadow: a white disc in multiply mode
        // leaves the underlying blue unchanged. Concentric strokes work on API 24.
        for (cy in listOf(-7f * scale, size.height + 92f * scale)) {
            for (radius in listOf(209f * scale, 296f * scale)) {
                val center = Offset(size.width / 2, cy)
                val blur = 15f * scale
                for (i in 1..30) {
                    val distance = i * scale
                    val opacity = .10f * exp(-(distance * distance) / (2 * blur * blur))
                    drawCircle(Color.Black.copy(alpha = opacity), radius + distance,
                        center, style = Stroke(scale + .5f))
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20, widthDp = 360, heightDp = 800)
@Composable
private fun VikoBrandGlowPreview() { VikoTheme { VikoBrandGlow() } }
