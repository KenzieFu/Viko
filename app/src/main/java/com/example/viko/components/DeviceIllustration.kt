package com.example.viko.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.R
import com.example.viko.view.theme.VikoTheme

/** Isolated device illustration exported from the supplied Figma component. */
@Composable
fun DeviceIllustration(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.onboarding_device), "ViKo wearable",
        modifier.size(width = 197.dp, height = 189.dp))
}

@Preview
@Composable
private fun DeviceIllustrationPreview() { VikoTheme { DeviceIllustration() } }
