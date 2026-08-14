package com.example.viko.view.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.R
import com.example.viko.mock.AppState
import com.example.viko.view.theme.*

@Preview(showBackground = true)
@Composable
private fun HomeTopBarPreview() {
    VikoTheme {
        HomeTopBar(appState = AppState(), onSosClick = {}, onNotificationsClick = {}, onSettingsClick = {})
    }
}

@Composable
fun HomeTopBar(
    appState: AppState,
    onSosClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 44.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // SOS button (top-left)
        Row(
            modifier = Modifier
                .shadow(2.dp, RoundedCornerShape(14.dp), spotColor = VShadow)
                .clip(RoundedCornerShape(14.dp))
                .background(VSurface)
                .clickable { onSosClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_sos),
                contentDescription = "SOS Emergency Button",
                tint = VAccentRed, // Changes the icon color dynamically
                modifier = Modifier.size(24.dp) // Sets custom sizing
            )
            Text(
                text = "SOS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = VAccentRed
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Notifications with blue badge
        Box {
            IconButton(onClick = onNotificationsClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_bell),
                    contentDescription = "Notification Button",
                    tint = VOnSurface, // untinted defaulted to dark, invisible on dark bg
                    modifier = Modifier.size(24.dp) // Sets custom sizing
                )
            }
            if (appState.activeAlertsCount() > 0) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 22.dp)
                        .clip(CircleShape)
                        .background(VAccentBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${appState.activeAlertsCount()}",
                        fontSize = 9.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Outlined.Settings, "Settings", tint = VOnSurface)
        }
    }
}
