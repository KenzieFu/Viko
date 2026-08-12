package com.example.viko.view.screens.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A worker plus the channel name the list needs — resolved once, in the VM. */
data class MemberRow(
    val worker: Worker,
    val channelName: String
)

data class ManageMemberUiState(
    val members: List<MemberRow> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val searchQuery: String = "",
    val filterChannelId: String? = null
)

class ManageMemberViewModel(
    private val workerRepository: WorkerRepository,
    channelRepository: ChannelRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val filterChannelId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ManageMemberUiState> = combine(
        workerRepository.observeWorkers(),
        channelRepository.observeChannels(),
        searchQuery,
        filterChannelId
    ) { workers, channels, query, channelFilter ->
        val channelNames = channels.associate { it.id to it.name }
        ManageMemberUiState(
            members = workers
                .filter { it.matches(query, channelFilter) }
                .map { MemberRow(it, channelNames[it.channelId] ?: UNKNOWN_CHANNEL) },
            channels = channels,
            searchQuery = query,
            filterChannelId = channelFilter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = ManageMemberUiState()
    )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onChannelFilterChange(channelId: String?) {
        filterChannelId.value = channelId
    }

    fun removeWorker(workerId: String) = viewModelScope.launch {
        workerRepository.removeWorker(workerId)
    }

    private fun Worker.matches(query: String, channelFilter: String?): Boolean {
        val matchesSearch = query.isBlank() || name.contains(query.trim(), ignoreCase = true)
        val matchesChannel = channelFilter == null || channelId == channelFilter
        return matchesSearch && matchesChannel
    }

    private companion object {
        const val UNKNOWN_CHANNEL = "Unknown"
    }
}
