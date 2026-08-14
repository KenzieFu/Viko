package com.example.viko.view.screens.team.components

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
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun DetailFieldPreview() {
    VikoTheme { DetailField(label = "Name", value = "Jane Doe") }
}

@Composable
fun DetailField(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(label, fontSize = 11.sp, color = VOnSurfaceSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = VOnSurface)
    }
}
