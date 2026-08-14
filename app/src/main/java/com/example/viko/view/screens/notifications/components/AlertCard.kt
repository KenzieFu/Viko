package com.example.viko.view.screens.notifications.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
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
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.type.AlertStatus
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun AlertCardPreview() {
    VikoTheme {
        AlertCard(
            alert = EmergencyAlert(
                id = "a1",
                workerId = "w1",
                workerName = "Jane Doe",
                zone = "Zone 1",
                team = "Kitchen",
                timeAgo = "2m ago",
                status = AlertStatus.ACTIVE
            ),
            onDirectCall = {},
            onResolve = {}
        )
    }
}

@Composable
fun AlertCard(
    alert: EmergencyAlert,
    onDirectCall: () -> Unit,
    onResolve: () -> Unit
) {
    val isResolved = alert.status == AlertStatus.RESOLVED
    val bgColor = if (isResolved) VSurface else VAlertCream

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp), spotColor = VShadow)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = if (isResolved) VBorder else VAlertCreamBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isResolved) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = if (isResolved) VAccentBlue else VAlertAmber,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Emergency by ${alert.workerName}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp,
                        color = VOnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${alert.zone} • ${alert.team}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VOnSurfaceSecondary
                    )
                    if (isResolved) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Resolved",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VOnSurfaceSecondary
                        )
                    }
                }
                Text(
                    text = alert.timeAgo,
                    fontSize = 13.sp,
                    color = VOnSurfaceSecondary
                )
            }

            if (!isResolved) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NotifActionButton(
                        text = "Direct Call",
                        icon = Icons.Default.Phone,
                        modifier = Modifier.weight(1f),
                        onClick = onDirectCall
                    )
                    NotifActionButton(
                        text = "Resolve",
                        icon = Icons.Outlined.CheckCircle,
                        modifier = Modifier.weight(1f),
                        onClick = onResolve
                    )
                }
            }
        }
    }
}
