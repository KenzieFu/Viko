package com.example.viko.view.screens.team.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.viko.components.BatteryBadge
import com.example.viko.domain.team.model.Worker
import com.example.viko.type.WorkerStatus
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.theme.VMemberSpeakingText
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VikoTheme

/**
 * The grey subtitle under a member's name: `Zone 4 • 🔋 70%`.
 *
 * - Unreachable earpiece → `Not Available`, no battery (it is not reporting one).
 * - Member holding their button → a teal **Speaking** leads the line.
 */
@Composable
fun MemberStatusLine(worker: Worker, modifier: Modifier = Modifier) {
    if (!worker.isOnline) {
        Text(text = "Not Available", fontSize = 12.sp, color = VOnSurfaceSecondary, modifier = modifier)
        return
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (worker.status == WorkerStatus.SPEAKING) {
            Text(text = "Speaking", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VMemberSpeakingText)
            Text(text = SEPARATOR, fontSize = 12.sp, color = VOnSurfaceSecondary)
        }
        Text(
            text = worker.zone + SEPARATOR,
            fontSize = 12.sp,
            color = VOnSurfaceSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        BatteryBadge(level = worker.batteryLevel)
    }
}

private const val SEPARATOR = " • "

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun MemberStatusLinePreview() {
    VikoTheme {
        MemberStatusLine(worker = previewWorker(status = WorkerStatus.SPEAKING, zone = "Zone 4", batteryLevel = 70))
    }
}
