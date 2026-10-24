package com.example.viko.view.screens.notifications.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.type.AlertStatus
import com.example.viko.view.theme.VAlertYellow
import com.example.viko.view.theme.VAlertYellowBorder
import com.example.viko.view.theme.VAlertYellowSurface
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VOnline
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/**
 * One SOS a member raised. Shared by the Notifications list and the banner on
 * Home, so the two never drift apart.
 *
 * - Active → yellow card, yellow `ⓘ`, **Direct Call** and **Resolve**.
 * - Resolved → plain card, green check, *Resolved*, no actions.
 */
@Composable
fun AlertCard(
    alert: EmergencyAlert,
    onDirectCall: () -> Unit,
    onResolve: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isResolved = alert.status == AlertStatus.RESOLVED
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isResolved) VSurface else VAlertYellowSurface)
            .then(if (isResolved) Modifier else Modifier.border(1.dp, VAlertYellowBorder, shape))
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (isResolved) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = if (isResolved) "Resolved" else "Active emergency",
            tint = if (isResolved) VOnline else VAlertYellow,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "Emergency by ${alert.workerName}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp,
                    color = VOnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = alert.timeAgo,
                    fontSize = 11.sp,
                    color = VOnSurfaceSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${alert.zone} • ${alert.team}",
                style = MaterialTheme.typography.bodyMedium,
                color = VOnSurfaceSecondary
            )

            if (isResolved) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Resolved", style = MaterialTheme.typography.bodyMedium, color = VOnSurfaceSecondary)
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlertActionButton(text = "Direct Call", icon = Icons.Default.Phone, onClick = onDirectCall)
                    AlertActionButton(text = "Resolve", icon = Icons.Filled.CheckCircle, onClick = onResolve)
                }
            }
        }
    }
}

/** Outlined, see-through — it sits on the yellow card, not on the page. */
@Composable
private fun AlertActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .border(1.dp, VOnSurface.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = VOnSurface, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = VOnSurface)
    }
}

private fun previewAlert(status: AlertStatus, timeAgo: String) = EmergencyAlert(
    id = "1",
    workerId = "w1",
    workerName = "Minh Luat Nguyen",
    zone = "Zone 3",
    team = "Operations Team",
    timeAgo = timeAgo,
    status = status
)

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun AlertCardPreview() {
    VikoTheme {
        AlertCard(alert = previewAlert(AlertStatus.ACTIVE, "1m ago"), onDirectCall = {}, onResolve = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun AlertCardResolvedPreview() {
    VikoTheme {
        AlertCard(alert = previewAlert(AlertStatus.RESOLVED, "10.00"), onDirectCall = {}, onResolve = {})
    }
}
