package com.example.viko.view.screens.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.domain.team.model.Worker
import com.example.viko.mock.AppState
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VikoTheme

@Preview(showBackground = true)
@Composable
private fun EditableMemberListItemPreview() {
    VikoTheme {
        EditableMemberListItem(worker = AppState().workers.first(), onDelete = {}, onNameChange = {})
    }
}

@Composable
fun EditableMemberListItem(
    worker: Worker,
    onDelete: () -> Unit,
    onNameChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp,
                vertical = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        OutlinedTextField(
            value = worker.name,
            onValueChange = onNameChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        IconButton(
            onClick = onDelete
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = VAccentRed
            )
        }
    }
}
