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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class MemberDetailUiState(
    val worker: Worker? = null,
    val channels: List<Channel> = emptyList(),
    /** What the Name field shows: the edit in progress, else the stored name. */
    val name: String = "",
    /** The channel the form has chosen — not saved until Save Changes. */
    val channelId: String = ""
) {
    val channelName: String? get() = channels.find { it.id == channelId }?.name
    val canSave: Boolean get() = worker != null && name.isNotBlank()
}

/**
 * Member Details is a form: name and channel are drafts until **Save Changes**,
 * so leaving with `X` discards them. The drafts sit in [SavedStateHandle], so a
 * rotation or process death mid-edit keeps what was typed.
 */
class MemberDetailViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val workerRepository: WorkerRepository,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    private val workerId: String = checkNotNull(savedStateHandle[NavArgs.WORKER_ID])

    // null = untouched, so the field follows the stored value until the
    // supervisor actually edits it.
    private val draftName = savedStateHandle.getStateFlow<String?>(KEY_DRAFT_NAME, null)
    private val draftChannelId = savedStateHandle.getStateFlow<String?>(KEY_DRAFT_CHANNEL, null)

    val uiState: StateFlow<MemberDetailUiState> = combine(
        workerRepository.observeWorker(workerId),
        channelRepository.observeChannels(),
        draftName,
        draftChannelId
    ) { worker, channels, name, channelId ->
        MemberDetailUiState(
            worker = worker,
            channels = channels,
            name = name ?: worker?.name.orEmpty(),
            channelId = channelId ?: worker?.channelId.orEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = MemberDetailUiState()
    )

    fun onNameChange(name: String) {
        savedStateHandle[KEY_DRAFT_NAME] = name
    }

    fun selectChannel(channelId: String) {
        savedStateHandle[KEY_DRAFT_CHANNEL] = channelId
    }

    /** Creates a channel and picks it in the form — still only saved with the rest. */
    fun createChannelAndSelect(name: String) = viewModelScope.launch {
        val channel = Channel(id = "ch_${UUID.randomUUID()}", name = name)
        channelRepository.addChannel(channel)
        selectChannel(channel.id)
    }

    /**
     * Writes only what changed, so saving an untouched form is a no-op, then
     * calls [onSaved]. The screen leaves from there rather than straight after
     * the tap: popping first would clear this ViewModel and could cancel the
     * write halfway.
     */
    fun saveChanges(onSaved: () -> Unit) = viewModelScope.launch {
        val state = uiState.value
        val worker = state.worker ?: return@launch
        if (!state.canSave) return@launch

        val name = state.name.trim()
        if (name != worker.name) workerRepository.renameWorker(workerId, name)
        if (state.channelId != worker.channelId) workerRepository.assignToChannel(workerId, state.channelId)
        onSaved()
    }

    fun removeWorker() = viewModelScope.launch {
        workerRepository.removeWorker(workerId)
    }

    private companion object {
        const val KEY_DRAFT_NAME = "member_detail_draft_name"
        const val KEY_DRAFT_CHANNEL = "member_detail_draft_channel"
    }
}
