package com.example.viko.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun SectionDividerPreview() {
    VikoTheme { SectionDivider() }
}

@Composable
fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = VBorder
    )
}
