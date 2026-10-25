package com.example.viko.view.screens.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.example.viko.R
import com.example.viko.view.theme.*

/** A measured cut-out follows the real card; it never relies on a screen's pixel size. */
@Composable
internal fun OnboardingSpotlight(bounds: Rect?, hint: String, onTarget: () -> Unit) {
    val density = LocalDensity.current
    val transition = rememberInfiniteTransition(label = "Tutorial ripple")
    val wave by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "Outward wave")
    BoxWithConstraints(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(alpha = .5f))
            bounds?.let {
                val hole = it.inflate(8.dp.toPx())
                drawRoundRect(Color.Transparent, hole.topLeft, hole.size, CornerRadius(16.dp.toPx()), blendMode = BlendMode.Clear)
                val pointer = Offset(it.center.x, it.bottom - 4.dp.toPx())
                drawCircle(VOnboardingBlue.copy(alpha = .3f), 21.dp.toPx(), pointer)
                drawCircle(VOnboardingBlue.copy(alpha = .28f * (1 - wave)), (12 + 24 * wave).dp.toPx(), pointer)
            }
        }
        bounds?.let { rect ->
            val x = with(density) { rect.left.toDp() }
            val y = with(density) { rect.top.toDp() }
            Box(Modifier.offset(x, y).size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .semantics { contentDescription = hint.replace('\n', ' ') }
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTarget))
            Image(painterResource(R.drawable.ic_tutorial_cursor), null,
                Modifier.offset(with(density) { rect.center.x.toDp() } - 14.dp, with(density) { rect.bottom.toDp() } - 4.dp)
                    .size(width = 36.dp, height = 48.dp).clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTarget))
            Text(hint, color = Color.White, fontSize = 14.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
                modifier = Modifier.offset(
                    x = (with(density) { rect.center.x.toDp() } - 80.dp).coerceIn(0.dp, (maxWidth - 160.dp).coerceAtLeast(0.dp)),
                    y = with(density) { rect.bottom.toDp() } + 48.dp).width(160.dp))
        }
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
private fun OnboardingSpotlightPreview() {
    VikoTheme { OnboardingSpotlight(Rect(20f, 125f, 340f, 181f), "Go to your\nteam channel", {}) }
}
