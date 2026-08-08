package com.example.viko.view.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.mock.AppState
import com.example.viko.type.AlertStatus
import com.example.viko.view.theme.*

@Composable
fun NotificationsScreen(
    appState: AppState,
    onBack: () -> Unit,
    onDirectCall: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (appState.alerts.any { it.status == AlertStatus.ACTIVE }) {
                Text(
                    text = "Resolve All",
                    color = VOnSurfaceSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { appState.resolveAllAlerts() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        if (appState.alerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Notifications,
                        null,
                        tint = VOnSurfaceSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text("No notifications", color = VOnSurfaceSecondary)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(appState.alerts, key = { it.id }) { alert ->
                    AlertCard(
                        alert = alert,
                        onDirectCall = { onDirectCall(alert.workerId) },
                        onResolve = { appState.resolveAlert(alert.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AlertCard(
    alert: EmergencyAlert,
    onDirectCall: () -> Unit,
    onResolve: () -> Unit
) {
    val isResolved = alert.status == AlertStatus.RESOLVED
    val bgColor = if (isResolved) VSurface else VAlertYellowSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp), spotColor = VShadow)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = if (isResolved) VBorder else VAlertYellowBorder,
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
                    imageVector = if (isResolved) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (isResolved) VAccentBlue else VAlertYellow,
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
                        icon = Icons.Filled.CheckCircle,
                        modifier = Modifier.weight(1f),
                        onClick = onResolve
                    )
                }
            }
        }
    }
}

@Composable
private fun NotifActionButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VSurface)
            .border(1.dp, VBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VAccentBlue,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = VOnSurface
        )
    }
}
