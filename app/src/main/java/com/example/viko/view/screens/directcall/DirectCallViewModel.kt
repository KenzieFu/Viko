package com.example.viko.view.screens.directcall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.navigation.NavArgs
import com.example.viko.type.CallState
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DirectCallUiState(
    val worker: Worker? = null,
    val callState: CallState = CallState.IDLE,
    val transcript: String? = null,
    val translation: String? = null
)

/** Local, non-persisted parts of a call: who is talking and what came through. */
private data class CallSession(
    val callState: CallState = CallState.IDLE,
    val transcript: String? = null,
    val translation: String? = null
)

class DirectCallViewModel(
    savedStateHandle: SavedStateHandle,
    workerRepository: WorkerRepository
) : ViewModel() {

    private val workerId: String = checkNotNull(savedStateHandle[NavArgs.WORKER_ID])

    private val session = MutableStateFlow(CallSession())

    val uiState: StateFlow<DirectCallUiState> = combine(
        workerRepository.observeWorker(workerId),
        session
    ) { worker, call ->
        DirectCallUiState(
            worker = worker,
            callState = call.callState,
            transcript = call.transcript,
            translation = call.translation
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = DirectCallUiState()
    )

    init {
        simulateIncomingSpeech()
    }

    /** Manager pushes to talk. Ignored while the worker holds the channel. */
    fun toggleTalking() = session.update { call ->
        when (call.callState) {
            CallState.LISTENING -> call
            CallState.SPEAKING -> call.copy(callState = CallState.IDLE)
            CallState.IDLE -> call.copy(callState = CallState.SPEAKING)
        }
    }

    /**
     * Placeholder for the real receive path: a `ptt_start` control packet,
     * streamed audio, then a translated transcript. Delete this once the badge
     * transport delivers real messages.
     */
    private fun simulateIncomingSpeech() = viewModelScope.launch {
        delay(INCOMING_DELAY_MS)
        session.update {
            it.copy(
                callState = CallState.LISTENING,
                transcript = "Vâng thủ trưởng, tôi nghĩ chúng tôi đã xử lý xong rồi. Vậy là ổn rồi ạ.",
                translation = "Yes boss, I think we've got them handled already now. So it's fine now."
            )
        }
        delay(SPEECH_DURATION_MS)
        session.update { it.copy(callState = CallState.IDLE) }
    }

    private companion object {
        const val INCOMING_DELAY_MS = 2_500L
        const val SPEECH_DURATION_MS = 4_000L
    }
}
