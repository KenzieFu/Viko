package com.example.viko.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppState {
    val workers = mutableStateListOf(*mockWorkers.toTypedArray())
    val channels = mutableStateListOf(*mockChannels.toTypedArray())
    val alerts = mutableStateListOf(*mockAlerts.toTypedArray())
    val messages = mutableStateListOf(*mockMessages.toTypedArray())

    var activeAlertId by mutableStateOf<String?>("a1")

    val activeAlert: EmergencyAlert?
        get() = activeAlertId?.let { id -> alerts.find { it.id == id && it.status == AlertStatus.ACTIVE } }

    fun resolveAlert(alertId: String) {
        val idx = alerts.indexOfFirst { it.id == alertId }
        if (idx >= 0) {
            alerts[idx] = alerts[idx].copy(status = AlertStatus.RESOLVED)
            activeAlertId = alerts.firstOrNull { it.status == AlertStatus.ACTIVE }?.id
        }
    }

    fun resolveAllAlerts() {
        for (i in alerts.indices) {
            if (alerts[i].status == AlertStatus.ACTIVE) {
                alerts[i] = alerts[i].copy(status = AlertStatus.RESOLVED)
            }
        }
        activeAlertId = null
    }

    fun removeWorker(workerId: String) {
        workers.removeAll { it.id == workerId }
    }

    fun addWorker(worker: Worker) {
        workers.add(worker)
    }

    fun updateWorkerChannel(workerId: String, channelId: String) {
        val idx = workers.indexOfFirst { it.id == workerId }
        if (idx >= 0) workers[idx] = workers[idx].copy(channelId = channelId)
    }

    fun workersForChannel(channelId: String): List<Worker> =
        workers.filter { it.channelId == channelId }

    fun activeAlertsCount(): Int = alerts.count { it.status == AlertStatus.ACTIVE }

    fun addMessage(msg: TranslationMessage) {
        messages.add(0, msg)
    }

    fun assignWorkerToChannel(workerId: String, channelId: String) {
        val idx = workers.indexOfFirst { it.id == workerId }
        if (idx >= 0) {
            workers[idx] = workers[idx].copy(channelId = channelId)
        }
    }

    fun unassignWorkerFromChannel(workerId: String) {
        val idx = workers.indexOfFirst { it.id == workerId }
        if (idx >= 0) {
            workers[idx] = workers[idx].copy(channelId = "")
        }
    }
}
