package com.example.viko.view.screens.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.components.VikoBlueButton
import com.example.viko.view.theme.VBrand
import com.example.viko.view.theme.VOnboardingBlue
import com.example.viko.view.theme.VikoTheme

/** Home stays visible above the intro; only the footer has a brand-blue bloom. */
@Composable
fun ReadyToGoOverlay(onStartTutorial: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Transparent, .5f to Color.Black.copy(alpha = .75f), .8f to Color.Black, 1f to Color.Black
        )))
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height)
            val radius = size.width * 1.5f
            drawCircle(Brush.radialGradient(listOf(VOnboardingBlue.copy(alpha = .30f), Color.Transparent),
                center = center, radius = radius), radius, center)
        }
        Column(
            Modifier.fillMaxSize().safeDrawingPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 48.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text("You are now ready to go", fontSize = 24.sp, lineHeight = 30.sp,
                fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text("Manage your team easily from across the factory without language barrier",
                fontSize = 14.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = .75f),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(32.dp))
            VikoBlueButton("Start Tutorial", onStartTutorial, containerColor = VBrand)
            // Adjacent equal-height actions; TextButton has no fill or border.
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Skip Intro Tutorial", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ReadyToGoOverlayPreview() { VikoTheme { ReadyToGoOverlay({}, {}) } }
