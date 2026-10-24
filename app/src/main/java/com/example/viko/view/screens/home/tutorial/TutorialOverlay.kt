package com.example.viko.view.screens.home.tutorial

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import com.example.viko.R
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/** How much of the screen behind the cut-out is taken away. */
private const val SCRIM_ALPHA = 0.78f

/** Breathing room between the highlighted control and the edge of the hole. */
private val HOLE_PADDING = 6.dp
private val HOLE_RADIUS = 16.dp
private val BUBBLE_GAP = 24.dp
private val BUBBLE_WIDTH = 320.dp

/**
 * One tutorial step: everything except [bounds] is dimmed, a hand points at
 * what the step is about, and the copy sits clear of it.
 *
 * The hole is punched with [BlendMode.Clear] rather than shipped as the
 * design's five baked mask SVGs — the control it frames is laid out at runtime,
 * so its position is only known then.
 */
@Composable
fun TutorialOverlay(
    step: TutorialStep,
    bounds: Rect?,
    onNext: () -> Unit,
    onSkipAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            // Swallow taps: during the tour the only way on is Next or Skip All.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(Color.Black.copy(alpha = SCRIM_ALPHA))
            if (bounds != null) {
                val pad = with(density) { HOLE_PADDING.toPx() }
                val radius = with(density) { HOLE_RADIUS.toPx() }
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(bounds.left - pad, bounds.top - pad),
                    size = Size(bounds.width + pad * 2, bounds.height + pad * 2),
                    cornerRadius = CornerRadius(radius, radius),
                    blendMode = BlendMode.Clear
                )
            }
        }

        if (bounds != null) {
            // The finger tip is near the top of the asset, so the hand hangs
            // down and to the right of whatever it is pointing at.
            val cursorX = with(density) { (bounds.center.x - 12.dp.toPx()).toDp() }
            val cursorY = with(density) { (bounds.center.y - 10.dp.toPx()).toDp() }
            Image(
                painter = painterResource(id = R.drawable.ic_tutorial_cursor),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = cursorX, y = cursorY)
                    .size(width = 48.dp, height = 53.dp)
            )
        }

        TutorialBubble(
            step = step,
            topOffset = bubbleTop(bounds, density),
            onNext = onNext,
            onSkipAll = onSkipAll,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

/**
 * The copy sits below the spotlight so the hand never covers it, and falls back
 * to the design's own 366dp when a step has nothing to point at.
 */
private fun bubbleTop(bounds: Rect?, density: Density): Dp {
    if (bounds == null) return 366.dp
    return with(density) { bounds.bottom.toDp() } + BUBBLE_GAP
}

@Composable
private fun TutorialBubble(
    step: TutorialStep,
    topOffset: Dp,
    onNext: () -> Unit,
    onSkipAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .offset(x = 20.dp, y = topOffset)
            .width(BUBBLE_WIDTH),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "${step.stepNumber} / ${TutorialStep.total}",
                fontSize = 12.sp,
                color = Color.White
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = step.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = step.body,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Skip All",
                modifier = Modifier.clickable(onClick = onSkipAll),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VSurface)
                    .border(1.dp, VBorder, RoundedCornerShape(8.dp))
                    .clickable(onClick = onNext)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (step.isLast) "Done" else "Next",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun TutorialOverlayPreview() {
    VikoTheme {
        TutorialOverlay(
            step = TutorialStep.PressAndHold,
            bounds = Rect(left = 60f, top = 300f, right = 400f, bottom = 820f),
            onNext = {},
            onSkipAll = {}
        )
    }
}
