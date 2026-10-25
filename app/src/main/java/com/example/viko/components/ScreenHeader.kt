package com.example.viko.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VikoTheme

/**
 * `‹  Title  (+)` — the header of Settings, Manage Member, Manage Channel and the
 * picker sheets. [onAdd] null leaves the blue `+` out (Settings has none).
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null,
    addContentDescription: String = "Add",
    titleStyle: TextStyle = MaterialTheme.typography.headlineMedium
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = VOnSurface)
        }
        Text(
            text = title,
            style = titleStyle,
            fontWeight = FontWeight.Bold,
            color = VOnSurface,
            modifier = Modifier.weight(1f)
        )
        if (onAdd != null) {
            Spacer(modifier = Modifier.width(12.dp))
            BlueAddButton(contentDescription = addContentDescription, onClick = onAdd)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ScreenHeaderPreview() {
    VikoTheme {
        ScreenHeader(title = "Manage Member", onBack = {}, onAdd = {})
    }
}
