package com.example.viko.domain.alert.repository

import com.example.viko.domain.alert.model.EmergencyAlert
import kotlinx.coroutines.flow.Flow

interface AlertRepository {
    fun observeAlerts(): Flow<List<EmergencyAlert>>
    suspend fun addAlert(alert: EmergencyAlert)
    suspend fun resolveAlert(alertId: String)
    suspend fun resolveAllActive()
}
