package com.example.viko.view.screens.team.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.viko.components.ScreenHeader
import com.example.viko.components.SearchField
import com.example.viko.domain.team.model.Worker
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/**
 * *Add Member* from a team sheet in edit mode: pull someone already paired onto
 * this channel, or press `+` to pair a brand-new earpiece straight into it.
 */
@Composable
fun MemberPickerSheetContent(
    workers: List<Worker>,
    onPick: (String) -> Unit,
    onPairNew: () -> Unit,
    onBack: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = workers.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
    ) {
        ScreenHeader(
            title = "Add Member",
            onBack = onBack,
            onAdd = onPairNew,
            addContentDescription = "Pair a new device"
        )
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search Member",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Text(
                // Everyone paired is already here — the only way to grow the
                // channel is a new earpiece, which is the + above.
                text = if (workers.isEmpty()) "Everyone is already on this channel" else "No member found",
                style = MaterialTheme.typography.bodyMedium,
                color = VOnSurfaceSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { worker ->
                    Text(
                        text = worker.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = VOnSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VSurface)
                            .clickable { onPick(worker.id) }
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun MemberPickerSheetContentPreview() {
    VikoTheme {
        MemberPickerSheetContent(
            workers = listOf(previewWorker("01", "Tran Hoang Nam"), previewWorker("02", "Nguyen Minh Tam")),
            onPick = {}, onPairNew = {}, onBack = {}
        )
    }
}
