package com.example.viko.view.screens.team.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.components.VikoListRow
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.screens.team.MemberRow
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VikoTheme

/** Member variant of the shared list row: name, channel tag, availability, and chevron. */
@Composable
fun MemberRosterItem(row: MemberRow, onClick: () -> Unit) {
    VikoListRow(title = row.worker.name, onClick = onClick, supportingContent = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(row.channelName.uppercase(), fontSize = 10.sp, lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold, color = VOnSurfaceSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 100.dp)
                    .border(1.dp, VOnSurfaceSecondary.copy(alpha = .6f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp))
            Spacer(Modifier.width(8.dp))
            MemberStatusLine(worker = row.worker, modifier = Modifier.weight(1f))
        }
    }) {
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Default.ChevronRight, null, tint = VOnSurfaceSecondary, modifier = Modifier.size(20.dp))
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MemberRosterItemPreview() {
    VikoTheme { MemberRosterItem(MemberRow(previewWorker(name = "Tran Hoang Nam", zone = "Central Building", batteryLevel = 70), "Operations"), {}) }
}
