package com.example.viko.view.screens.team.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VikoTheme

/** The same row in edit mode: rename in place, or delete. */
@Composable
fun EditableMemberListItem(
    worker: Worker,
    onNameChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = worker.name,
            onValueChange = onNameChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${worker.name}", tint = VAccentRed)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun EditableMemberListItemPreview() {
    VikoTheme {
        EditableMemberListItem(
            worker = Worker(
                id = "w1",
                name = "Budi",
                channelId = "c1",
                zone = "Warehouse A",
                deviceAddress = "VK-001",
                macAddress = "AA:BB:CC:DD:EE:01",
                batteryLevel = 82,
                isOnline = true
            ),
            onNameChange = {},
            onDelete = {}
        )
    }
}
