package com.example.viko.view.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viko.components.ScreenHeader
import com.example.viko.components.VikoDragHandle
import com.example.viko.di.AppViewModelProvider
import com.example.viko.domain.settings.model.FactoryWifi
import com.example.viko.view.screens.settings.components.FactoryWifiSheetContent
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VOnSurface
import com.example.viko.view.theme.VOnSurfaceSecondary
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/**
 * The gear on Home: the two management lists, and the factory Wi-Fi every
 * earpiece is paired onto.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenManageMembers: () -> Unit,
    onOpenManageChannels: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenManageMembers = onOpenManageMembers,
        onOpenManageChannels = onOpenManageChannels,
        onSaveFactoryWifi = viewModel::saveFactoryWifi
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onOpenManageMembers: () -> Unit,
    onOpenManageChannels: () -> Unit,
    onSaveFactoryWifi: (FactoryWifi) -> Unit
) {
    var showWifiSheet by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBackground)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(modifier = Modifier.height(16.dp))

        ScreenHeader(title = "Settings", onBack = onBack)

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsRow(title = "Manage Members", onClick = onOpenManageMembers)
            SettingsRow(title = "Manage Channels", onClick = onOpenManageChannels)
            // One network for the whole factory — every access point shares the
            // SSID and password — so it is a setting, not a field per member.
            SettingsRow(
                title = "Factory Wi-Fi",
                value = uiState.factoryWifi.ssid.ifBlank { "Not set" },
                onClick = { showWifiSheet = true }
            )
        }
    }

    if (showWifiSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWifiSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VBackground,
            contentColor = VOnSurface,
            dragHandle = { VikoDragHandle() }
        ) {
            FactoryWifiSheetContent(
                current = uiState.factoryWifi,
                onSave = { wifi ->
                    onSaveFactoryWifi(wifi)
                    showWifiSheet = false
                },
                onClose = { showWifiSheet = false }
            )
        }
    }
}

@Composable
private fun SettingsRow(title: String, onClick: () -> Unit, value: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = VOnSurface,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                fontSize = 12.sp,
                color = VOnSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 6.dp)
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = VOnSurfaceSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview(device = "id:pixel_7", showBackground = true, backgroundColor = 0xFF1C1D20)
@Composable
private fun SettingsScreenPreview() {
    VikoTheme {
        SettingsScreen(
            uiState = SettingsUiState(FactoryWifi(ssid = "FactoryTranslate_AP")),
            onBack = {}, onOpenManageMembers = {}, onOpenManageChannels = {}, onSaveFactoryWifi = {}
        )
    }
}
