package com.example.viko.view.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.components.BatteryBadge
import com.example.viko.domain.team.model.Worker
import com.example.viko.mock.AppState
import com.example.viko.type.WorkerStatus
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun MemberListItemPreview() {
    VikoTheme {
        MemberListItem(worker = AppState().workers.first(), onDirectCall = {})
    }
}

@Composable
fun MemberListItem(
    worker: Worker,
    onDirectCall: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDirectCall?.invoke() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(VBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = worker.name.first().toString(),
                style = MaterialTheme.typography.titleLarge,
                color = VOnSurface
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = worker.name,
                style = MaterialTheme.typography.titleMedium,
                color = VOnSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WorkerStatusIndicator(worker = worker)
                Text(
                    text = when (worker.status) {
                        WorkerStatus.SPEAKING -> "Speaking..."
                        WorkerStatus.LISTENING -> "Listening"
                        WorkerStatus.OFFLINE -> "Offline"
                        WorkerStatus.AVAILABLE -> "${worker.zone} • Available"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (worker.status == WorkerStatus.SPEAKING) VAccentRed else VOnSurfaceSecondary
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        } else {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BatteryBadge(level = worker.batteryLevel)
                if (onDirectCall != null && worker.isOnline) {
                    IconButton(
                        onClick = { onDirectCall() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Phone, "Call", tint = VOnSurface, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
