package com.example.viko.view.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.data.ai.onevoice.TranslationDiagnostics
import com.example.viko.data.network.BadgeRegistry
import com.example.viko.data.network.TranslationStatus
import com.example.viko.data.settings.TranslationPreferences
import com.example.viko.domain.settings.model.FactoryWifi
import com.example.viko.domain.settings.repository.FactoryWifiRepository
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val factoryWifi: FactoryWifi = FactoryWifi(),
    val aiTranslationEnabled: Boolean = false,
    val translationStatus: TranslationStatus = TranslationStatus.OFF,
    val diagnosticsEnabled: Boolean = false
)

class SettingsViewModel internal constructor(
    private val factoryWifiRepository: FactoryWifiRepository,
    private val translationPreferences: TranslationPreferences,
    private val diagnostics: TranslationDiagnostics,
    badgeRegistry: BadgeRegistry
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        factoryWifiRepository.observeFactoryWifi(),
        translationPreferences.enabled,
        badgeRegistry.translationStatus,
        translationPreferences.diagnosticsEnabled
    ) { wifi, enabled, status, recording ->
        SettingsUiState(wifi, enabled, status, recording)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
            initialValue = SettingsUiState(
                aiTranslationEnabled = translationPreferences.enabled.value,
                translationStatus = badgeRegistry.translationStatus.value,
                diagnosticsEnabled = translationPreferences.diagnosticsEnabled.value
            )
        )

    fun setAiTranslation(enabled: Boolean) = translationPreferences.setEnabled(enabled)

    fun setDiagnostics(enabled: Boolean) = translationPreferences.setDiagnosticsEnabled(enabled)

    internal fun exportDiagnostics(uri: Uri, onFinished: (TranslationDiagnostics.ExportResult) -> Unit) =
        viewModelScope.launch { onFinished(diagnostics.export(uri)) }

    fun saveFactoryWifi(wifi: FactoryWifi) = viewModelScope.launch {
        factoryWifiRepository.saveFactoryWifi(wifi)
    }
}
