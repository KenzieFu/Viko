package com.example.viko.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.VOnboardingBlue
import com.example.viko.view.theme.VMicBlue
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/** The dark `+ Add Channel` / `+ Add Member` pill: blue plus, white bold label. */
@Composable
fun AddPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(VSurface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = VOnboardingBlue, modifier = Modifier.size(20.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = VOnSurface
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun AddPillButtonPreview() {
    VikoTheme {
        AddPillButton(text = "Add Channel", onClick = {})
    }
}
