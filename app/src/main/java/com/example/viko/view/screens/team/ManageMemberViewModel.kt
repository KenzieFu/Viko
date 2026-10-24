package com.example.viko.view.screens.team

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.navigation.NavArgs
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A worker plus the channel name the list needs — resolved once, in the VM. */
data class MemberRow(
    val worker: Worker,
    val channelName: String
)

data class ManageMemberUiState(
    val members: List<MemberRow> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val searchQuery: String = "",
    val filterChannelId: String? = null,
    /** False until Room has answered — "no members yet" must not flash on a full roster. */
    val isLoaded: Boolean = false
) {
    /** Nothing paired at all, as opposed to a search or filter that matched nobody. */
    val hasNoMembers: Boolean get() = isLoaded && members.isEmpty() && searchQuery.isBlank() && filterChannelId == null
}

class ManageMemberViewModel(
    savedStateHandle: SavedStateHandle,
    workerRepository: WorkerRepository,
    channelRepository: ChannelRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    /** Starts on the channel Manage Channel was showing, when it sent us here. */
    private val filterChannelId = MutableStateFlow(savedStateHandle.get<String>(NavArgs.CHANNEL_ID))

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
            filterChannelId = channelFilter,
            isLoaded = true
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

    private fun Worker.matches(query: String, channelFilter: String?): Boolean {
        val matchesSearch = query.isBlank() || name.contains(query.trim(), ignoreCase = true)
        val matchesChannel = channelFilter == null || channelId == channelFilter
        return matchesSearch && matchesChannel
    }

    private companion object {
        const val UNKNOWN_CHANNEL = "Unassigned"
    }
}
