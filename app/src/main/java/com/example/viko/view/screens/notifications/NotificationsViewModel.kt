package com.example.viko.view.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.domain.alert.repository.AlertRepository
import com.example.viko.type.AlertStatus
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val alerts: List<EmergencyAlert> = emptyList()
) {
    val hasActiveAlerts: Boolean get() = alerts.any { it.status == AlertStatus.ACTIVE }
}

class NotificationsViewModel(
    private val alertRepository: AlertRepository
) : ViewModel() {

    val uiState: StateFlow<NotificationsUiState> = alertRepository.observeAlerts()
        .map { NotificationsUiState(alerts = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
            initialValue = NotificationsUiState()
        )

    fun resolve(alertId: String) = viewModelScope.launch {
        alertRepository.resolveAlert(alertId)
    }

    fun resolveAll() = viewModelScope.launch {
        alertRepository.resolveAllActive()
    }
}
