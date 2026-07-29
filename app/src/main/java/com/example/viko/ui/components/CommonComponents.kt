package com.example.viko.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.ui.theme.*

@Composable
fun VikoDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(VBorder)
        )
    }
}

@Composable
fun VikoTopBar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        trailingContent?.invoke()
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = VOnSurface
            )
        }
    }
}

/**
 * The plain text input used everywhere in the sheets: no Material box, no
 * underline, no label — the caller draws the rounded container around it.
 */
@Composable
fun VikoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = VOnSurfaceSecondary, fontSize = 15.sp) },
        modifier = modifier,
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = VOnSurface,
            unfocusedTextColor = VOnSurface,
            cursorColor = VMicBlue
        )
    )
}

/**
 * Figma's blue action button (Save / Continue). Disabled is the same blue gone
 * dim rather than grey, which is how the design shows "not filled in yet".
 */
@Composable
fun VikoBlueButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VMicBlue,
            contentColor = Color.White,
            disabledContainerColor = VActivePanel,
            disabledContentColor = Color.White.copy(alpha = 0.5f)
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusDot(isOnline: Boolean, size: Dp = 8.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isOnline) VOnline else VOffline)
    )
}

@Composable
fun BatteryBadge(level: Int) {
    val color = when {
        level <= 20 -> VLowBattery
        level <= 40 -> VWarning
        else -> VOnSurface
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(
            imageVector = Icons.Default.BatteryFull,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "$level%",
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SpeakingRipple(
    modifier: Modifier = Modifier,
    color: Color = VAccentRed,
    size: Dp = 180.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.12f, targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "a1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.18f, targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(1500, delayMillis = 300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "a2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.24f, targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(1500, delayMillis = 600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "a3"
    )
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "s1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, delayMillis = 300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "s2"
    )
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, delayMillis = 600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "s3"
    )

    Canvas(
        modifier = modifier.size(size)
    ) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val maxRadius = this.size.minDimension / 2
        drawCircle(color.copy(alpha = alpha1), radius = maxRadius * scale1, center = center)
        drawCircle(color.copy(alpha = alpha2), radius = maxRadius * 0.75f * scale2, center = center)
        drawCircle(color.copy(alpha = alpha3), radius = maxRadius * 0.5f * scale3, center = center)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = VPrimary,
    contentColor: Color = VOnPrimary
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
fun ChipBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(VBackground)
            .border(1.dp, VBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = VOnSurface
        )
    }
}

@Composable
fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = VBorder
    )
}
