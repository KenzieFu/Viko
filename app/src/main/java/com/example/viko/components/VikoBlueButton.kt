package com.example.viko.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.VBrandOnDark
import com.example.viko.view.theme.VikoTheme

/**
 * Figma's blue action button — Continue on onboarding, Save on the setup sheet.
 *
 * Disabled is the same blue at half strength rather than grey: the design uses
 * it to say "not filled in yet", and a grey button reads as permanently dead.
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
            .height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VBrandOnDark,
            contentColor = Color.White,
            disabledContainerColor = VBrandOnDark.copy(alpha = 0.5f),
            disabledContentColor = Color.White.copy(alpha = 0.5f)
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun VikoBlueButtonPreview() {
    VikoTheme { VikoBlueButton(text = "Continue", onClick = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun VikoBlueButtonDisabledPreview() {
    VikoTheme { VikoBlueButton(text = "Continue", onClick = {}, enabled = false) }
}
