package com.example.viko.view.screens.notifications

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.di.AppViewModelProvider
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.type.AlertStatus
import com.example.viko.view.screens.notifications.components.AlertCard
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VBorder
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

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
                .padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Notifications",
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                color = VOnSurface,
                modifier = Modifier.weight(1f)
            )
            if (uiState.hasActiveAlerts) {
                Text(
                    text = "Resolve All",
                    color = VOnSurface,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(VSurface)
                        .border(1.dp, VBorder, RoundedCornerShape(50))
                        .clickable { onResolveAll() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
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
                modifier = Modifier.weight(1f).navigationBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
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

private fun previewAlert(id: String, name: String, status: AlertStatus, timeAgo: String) =
    EmergencyAlert(
        id = id,
        workerId = "w$id",
        workerName = name,
        zone = "Zone 3",
        team = "Operations Team",
        timeAgo = timeAgo,
        status = status
    )

/** Active and resolved alerts side by side — the two card treatments. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun NotificationsScreenPreview() {
    VikoTheme {
        NotificationsScreen(
            uiState = NotificationsUiState(
                alerts = listOf(
                    previewAlert("1", "Minh Luat Nguyen", AlertStatus.ACTIVE, "1m ago"),
                    previewAlert("2", "Minh Luat Nguyen", AlertStatus.ACKNOWLEDGED, "13.00"),
                    previewAlert("3", "Minh Luat Nguyen", AlertStatus.RESOLVED, "10.00")
                )
            ),
            onBack = {}, onDirectCall = {}, onResolve = {}, onResolveAll = {}
        )
    }
}

/** Nothing has happened — the state you hope to see most of the time. */
@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun NotificationsScreenEmptyPreview() {
    VikoTheme {
        NotificationsScreen(
            uiState = NotificationsUiState(),
            onBack = {}, onDirectCall = {}, onResolve = {}, onResolveAll = {}
        )
    }
}
