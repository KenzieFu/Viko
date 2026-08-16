package com.example.viko.view.screens.notifications

import com.example.viko.view.theme.VikoTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.di.AppViewModelProvider
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.type.AlertStatus
import com.example.viko.view.theme.VAccentBlue
import com.example.viko.view.theme.VAlertYellow
import com.example.viko.view.theme.VAlertYellowSurface
import com.example.viko.view.theme.VAlertYellowBorder
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VShadow
import com.example.viko.view.theme.VSurface

/**
 * The emergency feed: every SOS a badge has raised, newest first.
 *
 * Resolves the ViewModel and delegates; the layout lives in the stateless
 * overload so @Preview can render it without an Application or database.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onDirectCall: (String) -> Unit,
    viewModel: NotificationsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NotificationsScreen(
        uiState = uiState,
        onBack = onBack,
        onDirectCall = onDirectCall,
        onResolve = viewModel::resolve,
        onResolveAll = viewModel::resolveAll
    )
}

@Composable
fun NotificationsScreen(
    uiState: NotificationsUiState,
    onBack: () -> Unit,
    onDirectCall: (String) -> Unit,
    onResolve: (String) -> Unit,
    onResolveAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

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
            if (uiState.hasActiveAlerts) {
                Text(
                    text = "Resolve All",
                    color = VOnSurfaceSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onResolveAll() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, "Close", tint = VOnSurface)
            }
        }

        if (uiState.alerts.isEmpty()) {
            EmptyNotifications()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.alerts, key = { it.id }) { alert ->
                    AlertCard(
                        alert = alert,
                        onDirectCall = { onDirectCall(alert.workerId) },
                        onResolve = { onResolve(alert.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNotifications() {
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
                contentDescription = null,
                tint = VOnSurfaceSecondary,
                modifier = Modifier.size(48.dp)
            )
            Text("No notifications", color = VOnSurfaceSecondary)
        }
    }
}

@Composable
private fun AlertCard(
    alert: EmergencyAlert,
    onDirectCall: () -> Unit,
    onResolve: () -> Unit
) {
    val isResolved = alert.status == AlertStatus.RESOLVED

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp), spotColor = VShadow)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isResolved) VSurface else VAlertYellowSurface)
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
                    AlertActionButton(
                        text = "Direct Call",
                        icon = Icons.Default.Phone,
                        modifier = Modifier.weight(1f),
                        onClick = onDirectCall
                    )
                    AlertActionButton(
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
private fun AlertActionButton(
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

private fun previewAlert(id: String, name: String, status: AlertStatus, timeAgo: String) =
    EmergencyAlert(
        id = id,
        workerId = "w$id",
        workerName = name,
        zone = "Warehouse A",
        team = "Warehouse",
        timeAgo = timeAgo,
        status = status
    )

/** Active and resolved alerts side by side — the two card treatments. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun NotificationsScreenPreview() {
    VikoTheme {
        NotificationsScreen(
            uiState = NotificationsUiState(
                alerts = listOf(
                    previewAlert("1", "Budi", AlertStatus.ACTIVE, "2 min ago"),
                    previewAlert("2", "Sari", AlertStatus.ACKNOWLEDGED, "14 min ago"),
                    previewAlert("3", "Andi", AlertStatus.RESOLVED, "1 hr ago")
                )
            ),
            onBack = {}, onDirectCall = {}, onResolve = {}, onResolveAll = {}
        )
    }
}

/** Nothing has happened — the state you hope to see most of the time. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun NotificationsScreenEmptyPreview() {
    VikoTheme {
        NotificationsScreen(
            uiState = NotificationsUiState(),
            onBack = {}, onDirectCall = {}, onResolve = {}, onResolveAll = {}
        )
    }
}
