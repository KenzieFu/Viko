package com.example.viko.mock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.message.model.TranslationMessage
import com.example.viko.domain.team.model.Worker
import com.example.viko.type.AlertStatus

/**
 * In-memory demo state.
 *
 * [seeded] decides whether the app starts with the mock factory already set up.
 *
 * - `false` (default) — a genuine first run: no members, no channels. Onboarding
 *   happens, "Add new channel" means something, and the *No channel yet* empty
 *   state is reachable. This is what the design describes.
 * - `true` — the populated demo: 4 channels, 20 members, one active alert. Use it
 *   to show Home full of cards without pairing anything first.
 *
 * It used to be unconditionally seeded, which is why Home always opened with four
 * predefined channels nobody created and why onboarding had nothing left to do.
 */
/**
 * Process-lifetime holder so the demo data survives Activity recreation.
 *
 * `remember { AppState() }` inside a composable is destroyed on every rotation,
 * IME insets change or system overlay — which meant every member you had just
 * paired vanished and onboarding started over. This goes away when the screens
 * move onto the Room repositories in `data/`; a ViewModel is the real answer.
 */
object AppStateHolder {
    val instance: AppState by lazy { AppState() }
}

class AppState(seeded: Boolean = false) {
    val workers = mutableStateListOf<Worker>().apply {
        if (seeded) addAll(mockWorkers)
    }
    val channels = mutableStateListOf<Channel>().apply {
        if (seeded) addAll(mockChannels)
    }
    val alerts = mutableStateListOf<EmergencyAlert>().apply {
        if (seeded) addAll(mockAlerts)
    }
    val messages = mutableStateListOf<TranslationMessage>().apply {
        if (seeded) addAll(mockMessages)
    }

    /** True until the supervisor has paired at least one member. */
    val needsOnboarding: Boolean
        get() = workers.isEmpty()

    var activeAlertId by mutableStateOf<String?>(if (seeded) "a1" else null)

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
