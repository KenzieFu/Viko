package com.example.viko.view.screens.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.R
import com.example.viko.components.VikoBrandGlow
import com.example.viko.di.AppViewModelProvider
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VikoTheme
import kotlinx.coroutines.delay

/** How long the mark is held even if Room answers immediately. */
private const val MINIMUM_HOLD_MS = 1_200L

/**
 * The first frame of the app: the mark over the brand bloom, while the database
 * is asked whether this install has ever been set up.
 */
@Composable
fun SplashScreen(
    onFinished: (SplashDestination) -> Unit,
    viewModel: SplashViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { delay(MINIMUM_HOLD_MS) }
    LaunchedEffect(uiState.destination) {
        // Both have to be true: the hold keeps the mark from flashing past, and
        // the destination is meaningless until Room has replied.
        delay(MINIMUM_HOLD_MS)
        uiState.destination?.let(onFinished)
    }

    SplashScreen()
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground),
        contentAlignment = Alignment.Center
    ) {
        VikoBrandGlow()
        Image(
            painter = painterResource(id = R.drawable.viko_wordmark),
            contentDescription = "ViKo",
            modifier = Modifier
                .width(152.dp)
                .height(52.dp)
        )
    }
}

@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun SplashScreenPreview() {
    VikoTheme { SplashScreen() }
}
