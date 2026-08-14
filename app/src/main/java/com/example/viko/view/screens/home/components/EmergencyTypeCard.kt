package com.example.viko.view.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.*

data class EmergencyType(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accent: Color
)

@Preview(showBackground = true)
@Composable
private fun EmergencyTypeCardPreview() {
    VikoTheme {
        EmergencyTypeCard(
            type = EmergencyType("fire", "Fire", Icons.Default.LocalFireDepartment, VAccentRed),
            isSelected = true,
            dimmed = false,
            onClick = {}
        )
    }
}

@Composable
fun EmergencyTypeCard(
    type: EmergencyType,
    isSelected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (dimmed) 0.4f else 1f
    val bgColor = if (isSelected) type.accent.copy(alpha = 0.08f) else VSurface
    val borderColor = if (isSelected) type.accent.copy(alpha = 0.4f) else VBorder

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(157.dp)
            .alpha(alpha)
            .shadow(1.dp, RoundedCornerShape(16.dp), spotColor = VShadow)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = !dimmed, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) type.accent else type.accent.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    type.icon,
                    contentDescription = type.label,
                    tint = if (isSelected) Color.White else type.accent,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = type.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) type.accent else VOnSurface
            )
        }
    }
}
