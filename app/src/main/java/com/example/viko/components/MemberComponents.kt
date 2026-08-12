package com.example.viko.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.domain.team.model.Worker
import com.example.viko.type.WorkerStatus
import com.example.viko.view.theme.VAccentBlue
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary

/**
 * One worker in a list: avatar, name, live status, battery. Shared by the home
 * team sheet and anywhere else a roster is shown.
 */
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
        MemberAvatar(name = worker.name)

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
                    text = worker.statusLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (worker.status == WorkerStatus.SPEAKING) VAccentRed else VOnSurfaceSecondary
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        } else {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                BatteryBadge(level = worker.batteryLevel)
                if (onDirectCall != null && worker.isOnline) {
                    IconButton(onClick = onDirectCall, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = "Call ${worker.name}",
                            tint = VOnSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** The same row in edit mode: rename in place, or delete. */
@Composable
fun EditableMemberListItem(
    worker: Worker,
    onNameChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = worker.name,
            onValueChange = onNameChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${worker.name}", tint = VAccentRed)
        }
    }
}

@Composable
fun MemberAvatar(name: String, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(VBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.toString().orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = VOnSurface
        )
    }
}

/** A label/value row, used by the setup and detail forms. */
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = VOnSurfaceSecondary, fontSize = 13.sp)
        Text(value, color = VOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** A stacked label-above-value field, used by the member detail card. */
@Composable
fun DetailField(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(label, fontSize = 11.sp, color = VOnSurfaceSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = VOnSurface)
    }
}

/**
 * A pulsing dot while the worker holds the channel, a plain dot otherwise —
 * the "someone is talking right now" cue on a noisy factory floor.
 */
@Composable
private fun WorkerStatusIndicator(worker: Worker) {
    val pulseColor = when (worker.status) {
        WorkerStatus.SPEAKING -> VAccentRed
        WorkerStatus.LISTENING -> VAccentBlue
        else -> null
    }

    if (pulseColor == null || !worker.isOnline) {
        StatusDot(isOnline = worker.isOnline)
        return
    }

    val progress by rememberInfiniteTransition(label = "workerStatus").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "workerStatusProgress"
    )

    Canvas(modifier = Modifier.size(14.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = pulseColor.copy(alpha = 0.22f * (1f - progress)),
            radius = size.minDimension * (0.25f + progress * 0.25f),
            center = center
        )
        drawCircle(
            color = pulseColor,
            radius = size.minDimension * 0.24f,
            center = center
        )
    }
}

private fun Worker.statusLabel(): String = when (status) {
    WorkerStatus.SPEAKING -> "Speaking..."
    WorkerStatus.LISTENING -> "Listening"
    WorkerStatus.OFFLINE -> "Offline"
    WorkerStatus.AVAILABLE -> "$zone • Available"
}
