package com.example.viko.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun VikoBlueButtonPreview() {
    VikoTheme { VikoBlueButton(text = "Save", onClick = {}) }
}

/**
 * Figma's blue action button (Save / Continue). Disabled is the same blue gone
 * dim rather than grey, which is how the design shows "not filled in yet".
 */
@Composable
fun VikoBlueButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VMicBlue,
            contentColor = Color.White,
            disabledContainerColor = VActivePanel,
            disabledContentColor = Color.White.copy(alpha = 0.5f)
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
