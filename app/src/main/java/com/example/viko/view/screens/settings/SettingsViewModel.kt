package com.example.viko.view.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val translationStatus: TranslationStatus = TranslationStatus.OFF
)

class SettingsViewModel(
    private val factoryWifiRepository: FactoryWifiRepository,
    private val translationPreferences: TranslationPreferences,
    badgeRegistry: BadgeRegistry
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        factoryWifiRepository.observeFactoryWifi(),
        translationPreferences.enabled,
        badgeRegistry.translationStatus
    ) { wifi, enabled, status ->
        SettingsUiState(wifi, enabled, status)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
            initialValue = SettingsUiState(
                aiTranslationEnabled = translationPreferences.enabled.value,
                translationStatus = badgeRegistry.translationStatus.value
            )
        )

    fun setAiTranslation(enabled: Boolean) = translationPreferences.setEnabled(enabled)

    fun saveFactoryWifi(wifi: FactoryWifi) = viewModelScope.launch {
        factoryWifiRepository.saveFactoryWifi(wifi)
    }
}
