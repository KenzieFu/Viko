package com.example.viko.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VikoTheme

/**
 * The plain text input used everywhere in the sheets: no Material box, no
 * underline, no label — the caller draws the rounded container around it.
 */
@Composable
fun VikoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = VOnSurfaceSecondary, fontSize = 15.sp) },
        modifier = modifier,
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = VOnSurface,
            unfocusedTextColor = VOnSurface,
            cursorColor = VMicBlue
        )
    )
}

@Preview
@Composable
private fun VikoTextFieldPreview() {
    VikoTheme {
        VikoTextField(value = "", onValueChange = {}, placeholder = "Member name")
    }
}
