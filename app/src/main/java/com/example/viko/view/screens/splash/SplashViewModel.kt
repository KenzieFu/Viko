package com.example.viko.view.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Where the splash hands off to, once the database has answered. */
enum class SplashDestination { Onboarding, Home }

data class SplashUiState(
    val destination: SplashDestination? = null
) {
    /** Null until Room replies — routing on the initial value would always onboard. */
    val isResolved: Boolean get() = destination != null
}

class SplashViewModel(
    channelRepository: ChannelRepository
) : ViewModel() {

    val uiState: StateFlow<SplashUiState> = channelRepository.observeChannels()
        .map { channels ->
            SplashUiState(
                destination =
                    if (channels.isEmpty()) SplashDestination.Onboarding else SplashDestination.Home
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
            initialValue = SplashUiState()
        )
}
