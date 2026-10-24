package com.example.viko.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/**
 * Label above a bordered row that opens a picker: `Select Channel  ›`.
 *
 * [onClick] null is the locked variant from Figma — a filled row with no border
 * and no chevron — used when the value was decided before the form opened (adding
 * a member from inside a channel's team sheet).
 */
@Composable
fun LabeledSelectField(
    label: String,
    value: String?,
    placeholder: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        FieldLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .clip(shape)
                .then(
                    if (onClick != null) {
                        Modifier
                            .border(1.dp, VBorder, shape)
                            .clickable(onClick = onClick)
                    } else {
                        Modifier.background(VSurface)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value != null) VOnSurface else VOnSurfaceSecondary,
                modifier = Modifier.weight(1f)
            )
            if (onClick != null) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = VOnSurfaceSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun LabeledSelectFieldPreview() {
    VikoTheme {
        LabeledSelectField(label = "Channel", value = null, placeholder = "Select Channel", onClick = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun LabeledSelectFieldLockedPreview() {
    VikoTheme {
        LabeledSelectField(label = "Channel", value = "Operations", placeholder = "Select Channel", onClick = null)
    }
}
