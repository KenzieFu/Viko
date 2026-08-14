package com.example.viko.view.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.mock.AppState
import com.example.viko.type.AlertStatus
import com.example.viko.view.screens.notifications.components.AlertCard
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
                        Icons.Outlined.Notifications,
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

@Preview(showBackground = true)
@Composable
private fun NotificationsScreenPreview() {
    VikoTheme {
        NotificationsScreen(appState = AppState(), onBack = {}, onDirectCall = {})
    }
}
