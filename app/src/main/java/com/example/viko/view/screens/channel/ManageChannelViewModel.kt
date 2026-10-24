package com.example.viko.view.screens.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ManageChannelUiState(
    val channels: List<Channel> = emptyList(),
    val memberCounts: Map<String, Int> = emptyMap()
)

/** Every channel in the factory, with how many members each one has. */
class ManageChannelViewModel(
    workerRepository: WorkerRepository,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    val uiState: StateFlow<ManageChannelUiState> = combine(
        channelRepository.observeChannels(),
        workerRepository.observeWorkers()
    ) { channels, workers ->
        ManageChannelUiState(
            channels = channels,
            memberCounts = workers.groupingBy { it.channelId }.eachCount()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = ManageChannelUiState()
    )

    fun createChannel(name: String) = viewModelScope.launch {
        channelRepository.addChannel(Channel(id = "ch_${UUID.randomUUID()}", name = name))
    }
}
