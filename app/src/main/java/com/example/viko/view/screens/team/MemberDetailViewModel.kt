package com.example.viko.view.screens.team

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.message.model.TranslationMessage
import com.example.viko.domain.message.repository.MessageRepository
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.navigation.NavArgs
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemberDetailUiState(
    val worker: Worker? = null,
    val channelName: String? = null,
    val recentMessages: List<TranslationMessage> = emptyList()
)

class MemberDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val workerRepository: WorkerRepository,
    channelRepository: ChannelRepository,
    messageRepository: MessageRepository
) : ViewModel() {

    private val workerId: String = checkNotNull(savedStateHandle[NavArgs.WORKER_ID])

    val uiState: StateFlow<MemberDetailUiState> = combine(
        workerRepository.observeWorker(workerId),
        channelRepository.observeChannels(),
        messageRepository.observeMessagesFor(workerId)
    ) { worker, channels, messages ->
        MemberDetailUiState(
            worker = worker,
            channelName = channels.find { it.id == worker?.channelId }?.name,
            recentMessages = messages.take(RECENT_MESSAGE_COUNT)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = MemberDetailUiState()
    )

    fun removeWorker() = viewModelScope.launch {
        workerRepository.removeWorker(workerId)
    }

    private companion object {
        const val RECENT_MESSAGE_COUNT = 3
    }
}
