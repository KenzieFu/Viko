package com.example.viko.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.VDeviceBody
import com.example.viko.view.theme.VDeviceBodyShade
import com.example.viko.view.theme.VDeviceButton
import com.example.viko.view.theme.VDeviceGrill
import com.example.viko.view.theme.VDeviceGrillDot
import com.example.viko.view.theme.VDeviceLed
import com.example.viko.view.theme.VDeviceNub
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VikoTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * The earpiece as Figma renders it: a rounded light-grey block, the speaker grill
 * ringed by white LEDs, the coral button top-right, the clip nub under it, and a
 * soft glow beneath.
 *
 * Drawn rather than shipped as an image because the 3D render has not been
 * exported yet. When it is, drop it in res/drawable and swap this body for an
 * Image — every caller already gives it the size it needs.
 */
@Composable
fun DeviceIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 160.dp, height = 168.dp)) {
        val body = size.width * BODY_FRACTION
        val left = (size.width - body) / 2f
        val top = size.height * 0.06f

        // Glow beneath: a soft circle squashed flat, so it reads as light pooled
        // on the floor under the device rather than as a disc behind it.
        val glowCenter = Offset(size.width / 2f, top + body * 1.02f)
        scale(scaleX = 1f, scaleY = 0.32f, pivot = glowCenter) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(VOnSurface.copy(alpha = 0.22f), Color.Transparent),
                    center = glowCenter,
                    radius = size.width / 2f
                ),
                radius = size.width / 2f,
                center = glowCenter
            )
        }

        // Clip nub, drawn first so the body sits over its top edge.
        drawRoundRect(
            color = VDeviceNub,
            topLeft = Offset(left + body * 0.12f, top + body * 0.92f),
            size = Size(body * 0.22f, body * 0.14f),
            cornerRadius = CornerRadius(body * 0.05f)
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(VDeviceBody, VDeviceBodyShade),
                startY = top,
                endY = top + body
            ),
            topLeft = Offset(left, top),
            size = Size(body, body),
            cornerRadius = CornerRadius(body * 0.18f)
        )

        // Coral button, top-right.
        drawRoundRect(
            color = VDeviceButton,
            topLeft = Offset(left + body * 0.68f, top + body * 0.17f),
            size = Size(body * 0.17f, body * 0.17f),
            cornerRadius = CornerRadius(body * 0.04f)
        )

        // Speaker: bezel, grill, LED ring.
        val center = Offset(left + body * 0.47f, top + body * 0.64f)
        val bezel = body * 0.31f
        drawCircle(color = VDeviceBody, radius = bezel, center = center)
        drawCircle(
            color = VDeviceBodyShade,
            radius = bezel,
            center = center,
            style = Stroke(width = body * 0.015f)
        )
        drawCircle(color = VDeviceGrill, radius = bezel * 0.74f, center = center)

        val ledRadius = bezel * 0.86f
        repeat(LED_COUNT) { index ->
            val angle = 2.0 * Math.PI * index / LED_COUNT
            drawCircle(
                color = VDeviceLed,
                radius = body * 0.018f,
                center = Offset(
                    center.x + (ledRadius * cos(angle)).toFloat(),
                    center.y + (ledRadius * sin(angle)).toFloat()
                )
            )
        }

        // A few rings of grill holes, enough to read as a speaker at this size.
        listOf(0.22f, 0.42f, 0.6f).forEachIndexed { ring, fraction ->
            val holes = 6 + ring * 6
            repeat(holes) { index ->
                val angle = 2.0 * Math.PI * index / holes
                drawCircle(
                    color = VDeviceGrillDot,
                    radius = body * 0.008f,
                    center = Offset(
                        center.x + (bezel * fraction * cos(angle)).toFloat(),
                        center.y + (bezel * fraction * sin(angle)).toFloat()
                    )
                )
            }
        }
    }
}

/** How much of the canvas width the block itself takes; the rest is glow. */
private const val BODY_FRACTION = 0.72f
private const val LED_COUNT = 20

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun DeviceIllustrationPreview() {
    VikoTheme {
        DeviceIllustration()
    }
}
