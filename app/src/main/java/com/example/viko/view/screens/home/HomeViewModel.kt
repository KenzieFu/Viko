package com.example.viko.view.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.alert.repository.AlertRepository
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.type.AlertStatus
import com.example.viko.type.WorkerStatus
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A channel plus everyone assigned to it — one card on the dashboard. */
data class ChannelSummary(
    val channel: Channel,
    val members: List<Worker>
) {
    val onlineCount: Int get() = members.count { it.isOnline }
    val totalCount: Int get() = members.size
    val isActive: Boolean get() = onlineCount > 0
    val hasSpeakingMember: Boolean get() = members.any { it.status == WorkerStatus.SPEAKING }
}

data class HomeUiState(
    val channels: List<ChannelSummary> = emptyList(),
    val unassignedToChannel: Map<String, List<Worker>> = emptyMap(),
    val activeAlertCount: Int = 0,
    val hasMembers: Boolean = false,
    /**
     * False until the database has answered. Everything that reacts to "there is
     * nothing here" must wait for this — otherwise the first frame of a populated
     * install reads as empty and onboarding opens over a working factory.
     */
    val isLoaded: Boolean = false
) {
    /** No badge has been paired yet, so pairing is the first thing to do. */
    val needsOnboarding: Boolean get() = isLoaded && !hasMembers

    /** Nothing to put in the grid — show the empty state instead of a blank screen. */
    val hasNoChannels: Boolean get() = isLoaded && channels.isEmpty()
}

class HomeViewModel(
    private val workerRepository: WorkerRepository,
    channelRepository: ChannelRepository,
    alertRepository: AlertRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        channelRepository.observeChannels(),
        workerRepository.observeWorkers(),
        alertRepository.observeAlerts()
    ) { channels, workers, alerts ->
        val byChannel = workers.groupBy { it.channelId }
        HomeUiState(
            channels = channels.map { ChannelSummary(it, byChannel[it.id].orEmpty()) },
            unassignedToChannel = channels.associate { channel ->
                channel.id to workers.filter { it.channelId != channel.id }
            },
            activeAlertCount = alerts.count { it.status == AlertStatus.ACTIVE },
            hasMembers = workers.isNotEmpty(),
            isLoaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = HomeUiState()
    )

    fun assignWorkerToChannel(workerId: String, channelId: String) = viewModelScope.launch {
        workerRepository.assignToChannel(workerId, channelId)
    }

    fun renameWorker(workerId: String, name: String) = viewModelScope.launch {
        workerRepository.renameWorker(workerId, name)
    }

    fun removeWorker(workerId: String) = viewModelScope.launch {
        workerRepository.removeWorker(workerId)
    }
}
