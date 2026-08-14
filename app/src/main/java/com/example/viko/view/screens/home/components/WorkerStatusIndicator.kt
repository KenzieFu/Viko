package com.example.viko.view.screens.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.components.StatusDot
import com.example.viko.domain.team.model.Worker
import com.example.viko.mock.AppState
import com.example.viko.type.WorkerStatus
import com.example.viko.view.theme.VAccentBlue
import com.example.viko.view.theme.VAccentRed
import com.example.viko.view.theme.VikoTheme

@Preview(showBackground = true)
@Composable
private fun WorkerStatusIndicatorPreview() {
    VikoTheme { WorkerStatusIndicator(worker = AppState().workers.first()) }
}

@Composable
fun WorkerStatusIndicator(worker: Worker) {
    val animatedColor = when (worker.status) {
        WorkerStatus.SPEAKING -> VAccentRed
        WorkerStatus.LISTENING -> VAccentBlue
        else -> null
    }

    if (animatedColor == null || !worker.isOnline) {
        StatusDot(isOnline = worker.isOnline)
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "workerStatus")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "workerStatusProgress"
    )

    Canvas(modifier = Modifier.size(14.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = animatedColor.copy(alpha = 0.22f * (1f - progress)),
            radius = size.minDimension * (0.25f + progress * 0.25f),
            center = center
        )
        drawCircle(
            color = animatedColor,
            radius = size.minDimension * 0.24f,
            center = center
        )
    }
}
