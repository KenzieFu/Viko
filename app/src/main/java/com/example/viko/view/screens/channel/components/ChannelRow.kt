package com.example.viko.view.screens.channel.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.components.VikoListRow
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VikoTheme

/**
 * A channel as a card row: bold name on the left, whatever the list needs on
 * the right — a blue ✓ in the picker, a count and `›` in Manage Channel. A
 * selected row keeps its background; only the trailing content changes.
 */
@Composable
fun ChannelRow(
    name: String,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    VikoListRow(title = name, onClick = onClick, trailing = trailing)
}

/** The picker's selected marker. */
@Composable
fun SelectedCheck() {
    Icon(Icons.Default.Check, contentDescription = "Selected", tint = VMicBlue, modifier = Modifier.size(22.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ChannelRowPreview() {
    VikoTheme {
        ChannelRow(name = "Operations", onClick = {}) { SelectedCheck() }
    }
}
