package com.example.viko.view.screens.directcall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.data.network.BadgeAudioServer
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.navigation.NavArgs
import com.example.viko.type.CallState
import com.example.viko.type.WorkerStatus
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DirectCallUiState(
    val worker: Worker? = null,
    val callState: CallState = CallState.IDLE,
    val translation: String? = null,
    /**
     * Set when the supervisor pressed talk and nothing could be sent — no mic
     * permission, or the badge has never been heard from so we have no address
     * for it. Silence with a lit-up button would be the worst possible feedback.
     */
    val talkError: String? = null
)

class DirectCallViewModel(
    savedStateHandle: SavedStateHandle,
    workerRepository: WorkerRepository,
    private val audio: BadgeAudioServer
) : ViewModel() {

    /** Also the badge's `device_id` — workers are keyed by it end to end. */
    private val workerId: String = checkNotNull(savedStateHandle[NavArgs.WORKER_ID])

    private val talking = MutableStateFlow(false)
    private val talkError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DirectCallUiState> = combine(
        workerRepository.observeWorker(workerId),
        talking,
        talkError
    ) { worker, isTalking, error ->
        DirectCallUiState(
            worker = worker,
            // The member holding their badge button is the authority: their
            // ptt_start already flipped this worker to SPEAKING via
            // BadgeRegistry, and their turn outranks ours.
            callState = when {
                worker?.status == WorkerStatus.SPEAKING -> CallState.LISTENING
                isTalking -> CallState.SPEAKING
                else -> CallState.IDLE
            },
            // No transcript: ASR/NMT/TTS is not built, and inventing a line of
            // Vietnamese here would be a lie the demo tells for us. Speech is
            // carried live instead — see BadgeAudioServer.
            translation = null,
            talkError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = DirectCallUiState()
    )

    /**
     * Manager pushes to talk: opens the phone mic and streams it to this badge.
     * Ignored while the worker holds the channel — half duplex, like the badge's
     * own button.
     */
    fun toggleTalking() {
        if (uiState.value.callState == CallState.LISTENING) return

        if (talking.value) {
            audio.stopTalking()
            talking.value = false
            return
        }

        val failure = audio.startTalking(workerId, viewModelScope)
        talking.value = failure == null
        talkError.value = failure
    }

    /** Leaving the screen must not leave the mic open and streaming. */
    override fun onCleared() {
        audio.stopTalking()
    }
}
