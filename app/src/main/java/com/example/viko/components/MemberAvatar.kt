package com.example.viko.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VikoTheme

@Composable
fun MemberAvatar(name: String, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(VBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.toString().orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = VOnSurface
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun MemberAvatarPreview() {
    VikoTheme {
        MemberAvatar(name = "Budi")
    }
}
