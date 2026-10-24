package com.example.viko.view.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.settings.model.FactoryWifi
import com.example.viko.domain.settings.repository.FactoryWifiRepository
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val factoryWifi: FactoryWifi = FactoryWifi()
)

class SettingsViewModel(
    private val factoryWifiRepository: FactoryWifiRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = factoryWifiRepository.observeFactoryWifi()
        .map { SettingsUiState(factoryWifi = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
            initialValue = SettingsUiState()
        )

    fun saveFactoryWifi(wifi: FactoryWifi) = viewModelScope.launch {
        factoryWifiRepository.saveFactoryWifi(wifi)
    }
}
