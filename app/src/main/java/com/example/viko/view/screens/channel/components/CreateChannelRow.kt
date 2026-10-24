package com.example.viko.view.screens.channel.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VikoTheme

/**
 * A search that matched nothing offers to create it inline — `Store   Add New +`
 * — instead of dead-ending on an empty list.
 */
@Composable
fun CreateChannelRow(name: String, onClick: () -> Unit) {
    ChannelRow(name = name, onClick = onClick) {
        Text("Add New", color = VOnSurface, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Icon(Icons.Default.Add, contentDescription = "Add $name", tint = VOnSurface, modifier = Modifier.size(18.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun CreateChannelRowPreview() {
    VikoTheme {
        CreateChannelRow(name = "Store", onClick = {})
    }
}
