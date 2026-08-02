package com.example.viko.data.alert.repository

import com.example.viko.data.alert.local.AlertDao
import com.example.viko.data.alert.local.toDomain
import com.example.viko.data.alert.local.toEntity
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.domain.alert.repository.AlertRepository
import com.example.viko.type.AlertStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlertRepositoryImpl(private val dao: AlertDao) : AlertRepository {
    override fun observeAlerts(): Flow<List<EmergencyAlert>> =
        dao.observeAlerts().map { list -> list.map { it.toDomain() } }

    override suspend fun addAlert(alert: EmergencyAlert) = dao.insert(alert.toEntity())

    override suspend fun resolveAlert(alertId: String) =
        dao.updateStatus(alertId, AlertStatus.RESOLVED)

    override suspend fun resolveAllActive() =
        dao.updateAllStatus(active = AlertStatus.ACTIVE, resolved = AlertStatus.RESOLVED)
}
