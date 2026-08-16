package com.example.viko.components

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
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VikoTheme

/** A stacked label-above-value field, used by the member detail card. */
@Composable
fun DetailField(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(label, fontSize = 11.sp, color = VOnSurfaceSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = VOnSurface)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun DetailFieldPreview() {
    VikoTheme {
        DetailField(label = "Zone", value = "Warehouse A")
    }
}
