package com.example.viko.view.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.view.theme.VAlertYellow
import com.example.viko.view.theme.VAlertYellowBorder
import com.example.viko.view.theme.VAlertYellowSurface
import com.example.viko.view.theme.VikoTheme

/**
 * The SOS that just came in, raised over the channel grid.
 *
 * It is a prompt, not the record: it shows for a few seconds and then gets out
 * of the way, because the alert is still waiting in Notifications either way.
 * Tapping the body goes there.
 */
@Composable
fun EmergencyBanner(
    alert: EmergencyAlert,
    onOpen: () -> Unit,
    onDirectCall: () -> Unit,
    onResolve: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VAlertYellowSurface)
            .border(1.dp, VAlertYellowBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = VAlertYellow,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Emergency by ${alert.workerName}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${alert.zone} • ${alert.team}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Manage",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BannerAction(
                text = "Direct Call",
                icon = Icons.Filled.Phone,
                onClick = onDirectCall,
                modifier = Modifier.weight(1f)
            )
            BannerAction(
                text = "Resolve",
                icon = Icons.Filled.CheckCircle,
                onClick = onResolve,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BannerAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .border(1.dp, VAlertYellowBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = VAlertYellow,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun EmergencyBannerPreview() {
    VikoTheme {
        Box(modifier = Modifier.padding(20.dp)) {
            EmergencyBanner(
                alert = EmergencyAlert(
                    id = "a1",
                    workerId = "w1",
                    workerName = "Minh Luat Nguyen",
                    zone = "Zone 3",
                    team = "Operations Team",
                    timeAgo = "1m ago"
                ),
                onOpen = {}, onDirectCall = {}, onResolve = {}
            )
        }
    }
}
