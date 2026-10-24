package com.example.viko.view.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.components.VikoBlueButton
import com.example.viko.components.VikoBrandGlow
import com.example.viko.components.VikoOutlinedField
import com.example.viko.di.AppViewModelProvider
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VikoTheme

/** Distance from the status bar down to the welcome copy, per the design. */
private val HEADER_OFFSET = 163.dp

@Composable
fun FirstChannelScreen(
    onChannelCreated: () -> Unit,
    viewModel: FirstChannelViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.createdChannelId) {
        if (uiState.createdChannelId != null) onChannelCreated()
    }

    FirstChannelScreen(
        uiState = uiState,
        onNameChange = viewModel::onNameChange,
        onContinue = viewModel::createChannel
    )
}

@Composable
fun FirstChannelScreen(
    uiState: FirstChannelUiState,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        VikoBrandGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(modifier = Modifier.height(HEADER_OFFSET))

            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text14Light("Welcome to Viko")
                    Title24Bold("Let’s setup your\nfirst team channel")
                }

                VikoOutlinedField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    placeholder = "Enter Channel Name",
                    onImeAction = onContinue
                )

                VikoBlueButton(
                    text = "Continue",
                    onClick = onContinue,
                    enabled = uiState.canContinue
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Text14Light(text: String) {
    androidx.compose.material3.Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 14.sp,
        fontWeight = FontWeight.Light,
        color = Color.White.copy(alpha = 0.6f),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun Title24Bold(text: String) {
    androidx.compose.material3.Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center
    )
}

@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun FirstChannelScreenPreview() {
    VikoTheme {
        FirstChannelScreen(
            uiState = FirstChannelUiState(),
            onNameChange = {},
            onContinue = {}
        )
    }
}

@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun FirstChannelScreenFilledPreview() {
    VikoTheme {
        FirstChannelScreen(
            uiState = FirstChannelUiState(name = "Operations"),
            onNameChange = {},
            onContinue = {}
        )
    }
}
