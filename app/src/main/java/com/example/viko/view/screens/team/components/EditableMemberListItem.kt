package com.example.viko.view.screens.team.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.components.VikoTextField
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/**
 * A team-sheet row in edit mode: the name as a filled field, and a red trash
 * that deletes the member outright — not just their place on this channel.
 */
@Composable
fun EditableMemberListItem(
    name: String,
    onNameChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VikoTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = "Member name",
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(VSurface)
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete $name", tint = VAccentRed)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun EditableMemberListItemPreview() {
    VikoTheme {
        EditableMemberListItem(name = "Hoang Mai Phuong", onNameChange = {}, onDelete = {})
    }
}
