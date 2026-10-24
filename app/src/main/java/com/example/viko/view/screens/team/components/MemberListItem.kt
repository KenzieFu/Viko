package com.example.viko.view.screens.team.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.domain.team.model.Worker
import com.example.viko.type.WorkerStatus
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VMemberSpeaking
import com.example.viko.view.theme.VMemberSpeakingSurface
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/**
 * One member in the team sheet: a card with the name, `Zone • 🔋 %`, and a phone
 * to call them on the right. No avatar — the design has none.
 *
 * - Not available → the whole card dims and the phone stops responding.
 * - Speaking → teal card and outline, a waveform beside the name.
 */
@Composable
fun MemberListItem(
    worker: Worker,
    onDirectCall: () -> Unit
) {
    val isSpeaking = worker.isOnline && worker.status == WorkerStatus.SPEAKING
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSpeaking) VMemberSpeakingSurface else VSurface)
            .border(1.dp, if (isSpeaking) VMemberSpeaking else VBorder, shape)
            .alpha(if (worker.isOnline) 1f else UNAVAILABLE_ALPHA)
            .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = worker.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isSpeaking) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = "${worker.name} is speaking",
                        tint = VOnSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            MemberStatusLine(worker = worker)
        }

        IconButton(onClick = onDirectCall, enabled = worker.isOnline) {
            Icon(
                Icons.Default.Phone,
                contentDescription = "Call ${worker.name}",
                // The dimmed card already fades this; disabled must not fade it twice.
                tint = VOnSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** How far an unreachable member's card fades back — same as an empty channel on Home. */
private const val UNAVAILABLE_ALPHA = 0.35f

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun MemberListItemPreview() {
    VikoTheme {
        MemberListItem(worker = previewWorker(name = "Le Khanh Linh", zone = "Zone 4", batteryLevel = 70), onDirectCall = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun MemberListItemSpeakingPreview() {
    VikoTheme {
        MemberListItem(
            worker = previewWorker(name = "Le Khanh Linh", zone = "Zone 4", status = WorkerStatus.SPEAKING),
            onDirectCall = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun MemberListItemUnavailablePreview() {
    VikoTheme {
        MemberListItem(worker = previewWorker(name = "Pham Bao Anh", isOnline = false), onDirectCall = {})
    }
}
