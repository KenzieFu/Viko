package com.example.viko.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/**
 * The *Delete member?* sheet: title + `X`, one line of consequence, then
 * **Cancel** (dark) beside **Delete** (solid red). Red here is deliberate —
 * removing a member wipes their paired badge, which is not undoable.
 */
@Composable
fun ConfirmDeleteSheetContent(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Delete member?",
    message: String = "Your paired devices data will be deleted and has to setup a new one later.",
    confirmLabel: String = "Delete"
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 20.dp)
            .navigationBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = VOnSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = VOnSurface)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = VOnSurfaceSecondary)
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetButton(text = "Cancel", containerColor = VSurface, onClick = onDismiss, modifier = Modifier.weight(1f))
            SheetButton(text = confirmLabel, containerColor = VAccentRed, onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SheetButton(
    text: String,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = Color.White)
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun ConfirmDeleteSheetContentPreview() {
    VikoTheme {
        ConfirmDeleteSheetContent(onConfirm = {}, onDismiss = {})
    }
}
