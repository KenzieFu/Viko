package com.example.viko.view.screens.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** A channel and everyone paired into it — one row on Manage Channel. */
data class ChannelRowUiState(
    val channel: Channel,
    val members: List<Worker>
) {
    val memberCount: Int get() = members.size
}

data class ManageChannelUiState(
    val channels: List<ChannelRowUiState> = emptyList(),
    val isLoaded: Boolean = false
)

class ManageChannelViewModel(
    private val channelRepository: ChannelRepository,
    private val workerRepository: WorkerRepository
) : ViewModel() {

    val uiState: StateFlow<ManageChannelUiState> = combine(
        channelRepository.observeChannels(),
        workerRepository.observeWorkers()
    ) { channels, workers ->
        val byChannel = workers.groupBy { it.channelId }
        ManageChannelUiState(
            channels = channels.map { ChannelRowUiState(it, byChannel[it.id].orEmpty()) },
            isLoaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = ManageChannelUiState()
    )

    fun removeMember(workerId: String) = viewModelScope.launch {
        workerRepository.removeWorker(workerId)
    }

    fun createChannel(name: String) = viewModelScope.launch {
        channelRepository.addChannel(Channel(id = "ch_${UUID.randomUUID()}", name = name.trim()))
    }
}
