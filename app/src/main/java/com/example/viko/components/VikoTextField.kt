package com.example.viko.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VikoTheme

/**
 * The plain text input used everywhere: no Material box, no underline, no label
 * — the caller draws the border or fill around it.
 *
 * A BasicTextField rather than Material's TextField, because that one enforces
 * a 56dp minimum height and the onboarding fields are 48dp.
 */
@Composable
fun VikoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /** Masks the text — the factory Wi-Fi password is typed on the shop floor. */
    isPassword: Boolean = false
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = VOnSurface, fontSize = 16.sp, lineHeight = 24.sp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 48.dp),
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(VMicBlue),
        visualTransformation =
            if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (isPassword) {
            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
        } else {
            KeyboardOptions(imeAction = ImeAction.Done)
        },
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = textStyle, color = VOnSurfaceSecondary)
                }
                innerTextField()
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun VikoTextFieldPreview() {
    VikoTheme {
        VikoTextField(value = "", onValueChange = {}, placeholder = "Member name")
    }
}
