package com.example.viko.view.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.domain.alert.repository.AlertRepository
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.data.network.BadgeAudioServer
import com.example.viko.type.AlertStatus
import com.example.viko.type.WorkerStatus
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A channel plus everyone assigned to it — one card on the dashboard. */
data class ChannelSummary(
    val channel: Channel,
    val members: List<Worker>,
    val activeBadgeIds: Set<String> = emptySet()
) {
    val onlineCount: Int get() = members.count { it.isOnline }
    val totalCount: Int get() = members.size
    val isActive: Boolean get() = onlineCount > 0
    val speaker: Worker? get() = members.firstOrNull { it.id in activeBadgeIds }
        ?: members.firstOrNull { it.status == WorkerStatus.SPEAKING }
    val hasSpeakingMember: Boolean get() = speaker != null
}

data class HomeUiState(
    val channels: List<ChannelSummary> = emptyList(),
    val unassignedToChannel: Map<String, List<Worker>> = emptyMap(),
    val activeAlertCount: Int = 0,
    /** The newest unresolved SOS — Home shows it as a yellow banner above the grid. */
    val activeAlert: EmergencyAlert? = null,
    val hasMembers: Boolean = false,
    /**
     * False until the database has answered. Everything that reacts to "there is
     * nothing here" must wait for this — otherwise the first frame of a populated
     * install reads as empty and onboarding opens over a working factory.
     */
    val isLoaded: Boolean = false,
    val talkingChannelId: String? = null,
    val talkError: String? = null
) {
    /** No badge has been paired yet, so pairing is the first thing to do. */
    val needsOnboarding: Boolean get() = isLoaded && !hasMembers

    /** Nothing to put in the grid — show the empty state instead of a blank screen. */
    val hasNoChannels: Boolean get() = isLoaded && channels.isEmpty()
}

class HomeViewModel(
    private val workerRepository: WorkerRepository,
    channelRepository: ChannelRepository,
    private val alertRepository: AlertRepository,
    private val audio: BadgeAudioServer
) : ViewModel() {

    private data class ChannelTalk(val channelId: String? = null, val error: String? = null)

    private val channelTalk = MutableStateFlow(ChannelTalk())

    val uiState: StateFlow<HomeUiState> = combine(
        channelRepository.observeChannels(),
        workerRepository.observeWorkers(),
        alertRepository.observeAlerts(),
        audio.activeBadges,
        channelTalk
    ) { channels, workers, alerts, activeBadgeIds, talk ->
        val byChannel = workers.groupBy { it.channelId }
        HomeUiState(
            channels = channels.map { ChannelSummary(it, byChannel[it.id].orEmpty(), activeBadgeIds) },
            unassignedToChannel = channels.associate { channel ->
                channel.id to workers.filter { it.channelId != channel.id }
            },
            activeAlertCount = alerts.count { it.status == AlertStatus.ACTIVE },
            // The alerts table has no timestamp to sort on; rows come back in
            // insertion order, so the last active one is the one just raised.
            activeAlert = alerts.lastOrNull { it.status == AlertStatus.ACTIVE },
            hasMembers = workers.isNotEmpty(),
            isLoaded = true,
            talkingChannelId = talk.channelId,
            talkError = talk.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch {
            audio.isTalking.collect {
                if (!audio.isTalking.value && channelTalk.value.channelId != null) {
                    channelTalk.value = ChannelTalk(error = audio.captureError.value)
                }
            }
        }
    }

    fun toggleChannelTalk(channelId: String) {
        val current = channelTalk.value.channelId
        if (current != null) {
            if (current == channelId) stopChannelTalk()
            return
        }

        val channel = uiState.value.channels.firstOrNull { it.channel.id == channelId } ?: return
        if (channel.hasSpeakingMember) return
        val recipients = channel.members.filter { it.isOnline }.map { it.id }
        val error = audio.startChannelTalk(recipients, viewModelScope)
        channelTalk.value = if (error == null && audio.isTalking.value) ChannelTalk(channelId = channelId)
            else ChannelTalk(error = error ?: audio.captureError.value)
    }

    fun stopChannelTalk() {
        val wasTalking = channelTalk.value.channelId != null
        channelTalk.value = ChannelTalk()
        if (wasTalking) audio.stopTalking(viewModelScope)
    }

    fun assignWorkerToChannel(workerId: String, channelId: String) = viewModelScope.launch {
        workerRepository.assignToChannel(workerId, channelId)
    }

    fun renameWorker(workerId: String, name: String) = viewModelScope.launch {
        workerRepository.renameWorker(workerId, name)
    }

    fun removeWorker(workerId: String) = viewModelScope.launch {
        workerRepository.removeWorker(workerId)
    }

    fun resolveAlert(alertId: String) = viewModelScope.launch {
        alertRepository.resolveAlert(alertId)
    }
}
