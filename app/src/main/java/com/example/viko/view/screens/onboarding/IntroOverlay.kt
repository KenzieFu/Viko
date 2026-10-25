package com.example.viko.view.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.res.painterResource
import com.example.viko.R
import com.example.viko.components.VikoBlueButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.VikoTheme

/**
 * "Your team ready!" — shown over Manage Channel once the first badge is in,
 * so the supervisor sees the channel they just filled behind the message.
 */
@Composable
fun TeamReadyOverlay(
    onGoToHomepage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Transparent, Color.Black.copy(alpha = .8f), Color(0xFF063A63)
        ))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Your team ready!", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text(buildAnnotatedString {
                append("You have setup your first team. You can always manage channel and member in ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Settings") }
            }, fontSize = 16.sp, lineHeight = 22.sp, color = Color.White.copy(alpha = .75f),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Image(painterResource(R.drawable.onboarding_setting), "Settings on the homepage",
                modifier = Modifier.fillMaxWidth().aspectRatio(320f / 150f))
            Spacer(Modifier.height(32.dp))
            VikoBlueButton("Go to Homepage", onGoToHomepage, Modifier.fillMaxWidth(.75f))
        }
    }
}

@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun TeamReadyOverlayPreview() {
    VikoTheme { TeamReadyOverlay(onGoToHomepage = {}) }
}
