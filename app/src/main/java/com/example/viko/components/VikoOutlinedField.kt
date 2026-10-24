package com.example.viko.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VBrand
import com.example.viko.view.theme.VFieldBorder
import com.example.viko.view.theme.VFieldText
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/** The 10sp caption sitting above a field, inset to match its corner radius. */
@Composable
fun VikoFieldLabel(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

/**
 * The bordered single-line input from the design — the channel name on
 * onboarding and the member name on the setup sheet.
 *
 * BasicTextField rather than Material's `TextField`, because the design's box
 * is 48dp with 12dp padding and Material's own minimum height is taller than
 * that before any padding is applied.
 */
@Composable
fun VikoOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = VFieldText, fontSize = 16.sp),
        cursorBrush = SolidColor(VBrand),
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }, onGo = { onImeAction() }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VBackground)
                    .border(
                        width = 1.dp,
                        // Focus is the only cue the design gives that this is
                        // the thing the keyboard is pointed at.
                        color = if (isFocused) VBrand else VFieldBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 16.sp,
                        color = VFieldText.copy(alpha = 0.3f)
                    )
                }
                innerTextField()
            }
        }
    )
}

/**
 * Same box, filled and not editable — the Channel row on the setup sheet, which
 * opens the picker instead of the keyboard.
 */
@Composable
fun VikoPickerField(
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VSurface)
            .border(1.dp, VFieldBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = value.ifBlank { placeholder },
            fontSize = 16.sp,
            color = if (value.isBlank()) VFieldText.copy(alpha = 0.3f) else VFieldText
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun VikoFieldsPreview() {
    VikoTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VikoFieldLabel("Name")
            VikoOutlinedField(value = "", onValueChange = {}, placeholder = "Enter Member Name")
            VikoOutlinedField(value = "Le Khanh Linh", onValueChange = {}, placeholder = "Enter Member Name")
            VikoFieldLabel("Channel")
            VikoPickerField(value = "Operations", placeholder = "Select Channel", onClick = {})
        }
    }
}
