package com.example.viko.view.screens.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.navigation.NavArgs
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class SelectChannelUiState(
    val channels: List<Channel> = emptyList(),
    val memberCounts: Map<String, Int> = emptyMap(),
    val selectedChannelId: String = ""
)

/**
 * Backs the full-screen channel picker used when changing the channel of a
 * member that already exists. The add-member flow has its own state — see
 * [com.example.viko.view.screens.team.AddMemberViewModel].
 */
class SelectChannelViewModel(
    savedStateHandle: SavedStateHandle,
    private val workerRepository: WorkerRepository,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    private val workerId: String = checkNotNull(savedStateHandle[NavArgs.WORKER_ID])

    val uiState: StateFlow<SelectChannelUiState> = combine(
        channelRepository.observeChannels(),
        workerRepository.observeWorkers(),
        workerRepository.observeWorker(workerId)
    ) { channels, workers, worker ->
        SelectChannelUiState(
            channels = channels,
            memberCounts = workers.groupingBy { it.channelId }.eachCount(),
            selectedChannelId = worker?.channelId.orEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = SelectChannelUiState()
    )

    fun selectChannel(channelId: String) = viewModelScope.launch {
        workerRepository.assignToChannel(workerId, channelId)
    }

    /** Creates a channel and moves this worker into it in one step. */
    fun createChannelAndSelect(name: String) = viewModelScope.launch {
        val channel = Channel(id = "ch_${UUID.randomUUID()}", name = name)
        channelRepository.addChannel(channel)
        workerRepository.assignToChannel(workerId, channel.id)
    }
}
