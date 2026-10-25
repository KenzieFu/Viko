package com.example.viko.view.screens.notifications.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.example.viko.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.type.AlertStatus
import com.example.viko.view.theme.VAlertYellowBorder
import com.example.viko.view.theme.VAlertYellowSurface
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/**
 * One SOS a member raised. Shared by the Notifications list and the banner on
 * Home, so the two never drift apart.
 *
 * - Active → yellow card, yellow `ⓘ`, **Direct Call** and **Resolve**.
 * - Resolved → plain card, green check, *Resolved*, no actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertCard(
    alert: EmergencyAlert,
    onDirectCall: () -> Unit,
    onResolve: () -> Unit,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = 2
) {
    val isResolved = alert.status == AlertStatus.RESOLVED
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isResolved) VSurface else VAlertYellowSurface)
            .then(if (isResolved) Modifier else Modifier.border(1.dp, VAlertYellowBorder, shape))
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(if (isResolved) R.drawable.ic_notif_done else R.drawable.ic_notif_info),
            contentDescription = if (isResolved) "Resolved" else "Active emergency",
            tint = Color.Unspecified,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "Emergency by ${alert.workerName}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp,
                    color = VOnSurface,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = alert.timeAgo,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    color = VOnSurface.copy(alpha = .7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${alert.zone} • ${alert.team}",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = VOnSurface.copy(alpha = .7f)
            )

            if (isResolved) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Resolved", fontSize = 12.sp,
                    lineHeight = 16.sp, color = VOnSurface.copy(alpha = .7f))
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                // Keep the actions side by side; wrap rather than clip on narrow screens or large fonts.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlertActionButton(text = "Direct Call", onClick = onDirectCall) {
                        Icon(Icons.Outlined.Phone, null, tint = VOnSurface, modifier = Modifier.size(16.dp))
                    }
                    AlertActionButton(text = "Resolve", onClick = onResolve) {
                        Icon(painterResource(R.drawable.ic_notif_done), null, tint = VOnSurface, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** Outlined, see-through — it sits on the yellow card, not on the page. */
@Composable
private fun AlertActionButton(
    text: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, VOnSurface.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 14.sp, lineHeight = 18.sp, color = VOnSurface)
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
