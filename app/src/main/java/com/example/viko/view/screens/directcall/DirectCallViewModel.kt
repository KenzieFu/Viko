package com.example.viko.view.screens.directcall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.data.network.BadgeAudioServer
import com.example.viko.data.network.BadgeRegistry
import com.example.viko.data.network.TranslationStatus
import com.example.viko.domain.message.repository.MessageRepository
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.navigation.NavArgs
import com.example.viko.type.CallState
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
    /** The latest completed turn, in its original language. */
    val transcript: String? = null,
    /** [transcript] rendered in the other person's language. */
    val translation: String? = null,
    val translationMessageId: String? = null,
    val translationFromManager: Boolean = false,
    val isTranslating: Boolean = false,
    val translationProblem: String? = null,
    /**
     * Set when the supervisor pressed talk and nothing could be sent — no mic
     * permission, or the badge has never been heard from so we have no address
     * for it. Silence with a lit-up button would be the worst possible feedback.
     */
    val voiceOnly: Boolean = true,
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

    init {
        badgeRegistry.prepareDirectCall()
    }

    private val talking = audio.isTalking
    private val audioState = combine(talking, audio.activeBadges) { isTalking, activeBadges ->
        isTalking to (workerId in activeBadges)
    }
    private val talkError = MutableStateFlow<String?>(null)

    /**
     * Room emits the newest completed turn first, in either direction.
     */
    private val latestMessage = messageRepository.observeMessagesFor(workerId)
        .map { rows -> rows.firstOrNull {
            (it.originalLang == "vi" && it.targetLang == "ko") ||
                (it.originalLang == "ko" && it.targetLang == "vi")
        } }

    private val translationState = combine(badgeRegistry.translationProgress, badgeRegistry.translationStatus) { progress, status ->
        progress[workerId] to (status == TranslationStatus.ACTIVE)
    }

    val uiState: StateFlow<DirectCallUiState> = combine(
        workerRepository.observeWorker(workerId),
        audioState,
        combine(talkError, audio.captureError) { requested, runtime -> runtime ?: requested },
        latestMessage,
        translationState
    ) { worker, (isTalking, badgeAudioActive), error, message, (progress, enabled) ->
        DirectCallUiState(
            worker = worker,
            // Keep Stop reachable even if both people press at once.
            callState = when {
                isTalking -> CallState.SPEAKING
                badgeAudioActive -> CallState.LISTENING
                else -> CallState.IDLE
            },
            transcript = message?.originalText?.takeIf { enabled && it.isNotBlank() },
            translation = message?.translatedText?.takeIf { enabled && it.isNotBlank() },
            translationMessageId = message?.id,
            translationFromManager = message?.originalLang == "ko",
            isTranslating = enabled && (progress?.pending ?: 0) > 0,
            translationProblem = progress?.problem?.takeIf { enabled },
            voiceOnly = !enabled,
            talkError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = DirectCallUiState()
    )

    /** The first tap starts capture; member speech still owns the half-duplex channel. */
    fun startTalking() {
        if (talking.value || uiState.value.callState == CallState.LISTENING) return
        talkError.value = audio.startTalking(workerId, viewModelScope)
    }

    /** The second tap submits the captured turn once. */
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
