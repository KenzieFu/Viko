package com.example.viko.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VikoTheme

/**
 * Tiny grey label *above* a bordered rounded field — the Setup Member / Member
 * Details pattern. Each field is its own bordered box; they are never grouped
 * into one card.
 */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FieldLabel(label)
        VikoTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            isPassword = isPassword,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, VBorder, RoundedCornerShape(12.dp))
        )
    }
}

/** The label that sits above a form field. Shared with [LabeledSelectField]. */
@Composable
internal fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = VOnSurfaceSecondary,
        modifier = Modifier.padding(start = 16.dp)
    )
    Spacer(modifier = Modifier.height(6.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun LabeledTextFieldPreview() {
    VikoTheme {
        LabeledTextField(label = "Name", value = "", onValueChange = {}, placeholder = "Enter Member Name")
    }
}
