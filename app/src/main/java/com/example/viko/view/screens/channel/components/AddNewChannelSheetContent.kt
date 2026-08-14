package com.example.viko.view.screens.channel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.components.VikoBlueButton
import com.example.viko.components.VikoTextField
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun AddNewChannelSheetContentPreview() {
    VikoTheme {
        AddNewChannelSheetContent(onCreate = {}, onClose = {})
    }
}

@Composable
fun AddNewChannelSheetContent(
    prefill: String = "",
    onCreate: (String) -> Unit,
    onClose: () -> Unit
) {
    var name by remember { mutableStateOf(prefill) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Add New Channel",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VOnSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VSurfaceVariant)
                .border(1.dp, VBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp)
        ) {
            VikoTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Enter Channel Name",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        VikoBlueButton(
            text = "Continue",
            enabled = name.isNotBlank(),
            onClick = { onCreate(name.trim()) }
        )
    }
}
