package com.example.viko.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.*

/** Shared 16sp label and 16 × 15dp spacing for channels, devices and members. */
@Composable
fun VikoListRow(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .shadow(1.dp, shape).clip(shape).background(VSurface)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = if (enabled) VOnSurface else VOnSurfaceSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supportingContent != null) {
                Spacer(Modifier.height(6.dp))
                supportingContent()
            }
        }
        trailing()
    }
}

@Preview
@Composable
private fun VikoListRowPreview() { VikoTheme { VikoListRow("Operations") { Text("1", fontSize = 12.sp) } } }
