package com.example.viko.view.screens.directcall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.data.network.BadgeAudioServer
import com.example.viko.data.network.VoiceMode
import com.example.viko.data.network.BadgeRegistry
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
     * The cascade is running on this member's last turn. With live relay off
     * there is otherwise nothing between the button being released and the
     * Korean arriving, and silence reads as a broken link.
     */
    val isTranslating: Boolean = false,
    /**
     * Set when the supervisor pressed talk and nothing could be sent — no mic
     * permission, or the badge has never been heard from so we have no address
     * for it. Silence with a lit-up button would be the worst possible feedback.
     */
    val voiceOnly: Boolean = !VoiceMode.TRANSLATION_ENABLED,
    val talkError: String? = null
)

class DirectCallViewModel(
    savedStateHandle: SavedStateHandle,
    workerRepository: WorkerRepository,
    messageRepository: MessageRepository,
    badgeRegistry: BadgeRegistry,
    private val audio: BadgeAudioServer
) : ViewModel() {

    /** Also the badge's `device_id` — workers are keyed by it end to end. */
    private val workerId: String = checkNotNull(savedStateHandle[NavArgs.WORKER_ID])

    private val talking = audio.isTalking
    private val talkError = MutableStateFlow<String?>(null)

    /**
     * The last thing this member said. `observeMessagesFor` is newest-first, so the
     * head is the current utterance — and it appears a few seconds *after* they
     * release the button, since the cascade runs on the whole turn at once.
     */
    private val latestMessage = messageRepository.observeMessagesFor(workerId)
        .map { it.firstOrNull() }

    private val translating = badgeRegistry.translating.map { workerId in it }

    val uiState: StateFlow<DirectCallUiState> = combine(
        workerRepository.observeWorker(workerId),
        talking,
        combine(talkError, audio.captureError) { requested, runtime -> runtime ?: requested },
        latestMessage,
        translating
    ) { worker, isTalking, error, message, isTranslating ->
        DirectCallUiState(
            worker = worker,
            // Keep Stop reachable even if both people press at once.
            callState = when {
                isTalking -> CallState.SPEAKING
                worker?.status == WorkerStatus.SPEAKING -> CallState.LISTENING
                else -> CallState.IDLE
            },
            // Deliberately NOT gated on the member still holding the channel. The
            // cascade takes a few seconds on the whole turn, so the transcript always
            // lands after they have let go — gating on SPEAKING would mean it was
            // never once visible.
            transcript = message?.originalText?.takeIf { VoiceMode.TRANSLATION_ENABLED && it.isNotBlank() },
            translation = message?.translatedText?.takeIf { VoiceMode.TRANSLATION_ENABLED && it.isNotBlank() },
            isTranslating = isTranslating,
            talkError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = DirectCallUiState()
    )

    /** Press starts capture; member speech still owns the half-duplex channel. */
    fun startTalking() {
        if (talking.value || uiState.value.callState == CallState.LISTENING) return
        talkError.value = audio.startTalking(workerId, viewModelScope)
    }

    /** A deliberate release submits the captured turn once. */
    fun stopTalking() {
        if (talking.value) audio.stopTalking(workerId)
    }

    /** Cancellation, navigation, or backgrounding discards the partial turn. */
    fun cancelTalking() {
        audio.stopTalking()
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
