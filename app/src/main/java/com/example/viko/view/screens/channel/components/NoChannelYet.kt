package com.example.viko.view.screens.channel.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.components.AddPillButton
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VikoTheme

/** *No channel yet* and a `+ Add Channel` pill, left-aligned under the search. */
@Composable
fun NoChannelYet(onAddChannel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "No channel yet",
            style = MaterialTheme.typography.bodyLarge,
            color = VOnSurfaceSecondary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        AddPillButton(text = "Add Channel", onClick = onAddChannel)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun NoChannelYetPreview() {
    VikoTheme {
        NoChannelYet(onAddChannel = {})
    }
}
