package com.example.viko.view.screens.directcall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.data.network.BadgeAudioServer
import com.example.viko.domain.message.repository.MessageRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DirectCallUiState(
    val worker: Worker? = null,
    val callState: CallState = CallState.IDLE,
    /** What this member last said, in their own language. */
    val transcript: String? = null,
    /** [transcript] rendered in Korean for the supervisor. */
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
    messageRepository: MessageRepository,
    private val audio: BadgeAudioServer
) : ViewModel() {

    /** Also the badge's `device_id` — workers are keyed by it end to end. */
    private val workerId: String = checkNotNull(savedStateHandle[NavArgs.WORKER_ID])

    private val talking = MutableStateFlow(false)
    private val talkError = MutableStateFlow<String?>(null)

    /**
     * The last thing this member said. `observeMessagesFor` is newest-first, so the
     * head is the current utterance — and it appears a few seconds *after* they
     * release the button, since the cascade runs on the whole turn at once.
     */
    private val latestMessage = messageRepository.observeMessagesFor(workerId)
        .map { it.firstOrNull() }

    val uiState: StateFlow<DirectCallUiState> = combine(
        workerRepository.observeWorker(workerId),
        talking,
        talkError,
        latestMessage
    ) { worker, isTalking, error, message ->
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
            // Deliberately NOT gated on the member still holding the channel. The
            // cascade takes a few seconds on the whole turn, so the transcript always
            // lands after they have let go — gating on SPEAKING would mean it was
            // never once visible.
            transcript = message?.originalText?.takeIf { it.isNotBlank() },
            translation = message?.translatedText?.takeIf { it.isNotBlank() },
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
            // The badge id goes in so the captured turn can be translated and sent
            // back — without it the mic is simply closed and nothing is answered.
            audio.stopTalking(workerId)
            talking.value = false
            return
        }

        val failure = audio.startTalking(workerId, viewModelScope)
        talking.value = failure == null
        talkError.value = failure
    }

    /**
     * Leaving the screen must not leave the mic open and streaming.
     *
     * No badge id: walking away mid-sentence is not a turn anyone meant to send,
     * and translating a half-finished thought into the worker's ear is worse than
     * saying nothing.
     */
    override fun onCleared() {
        audio.stopTalking()
    }
}
