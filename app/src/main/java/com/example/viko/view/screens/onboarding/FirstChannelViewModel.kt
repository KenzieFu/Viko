package com.example.viko.view.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class FirstChannelUiState(
    val name: String = "",
    val isSaving: Boolean = false,
    /** Set once the channel exists, which is the screen's cue to move on. */
    val createdChannelId: String? = null
) {
    val canContinue: Boolean get() = name.isNotBlank() && !isSaving
}

/**
 * Onboarding step one: the supervisor names their first team channel. Nothing
 * else in the app means anything until one exists — a badge has to be paired
 * into a channel, so this is the first thing that happens on a fresh install.
 */
class FirstChannelViewModel(
    private val channelRepository: ChannelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FirstChannelUiState())
    val uiState: StateFlow<FirstChannelUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }

    fun createChannel() {
        val state = _uiState.value
        if (!state.canContinue) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val channel = Channel(id = "ch_${UUID.randomUUID()}", name = state.name.trim())
            channelRepository.addChannel(channel)
            _uiState.update { it.copy(isSaving = false, createdChannelId = channel.id) }
        }
    }
}
