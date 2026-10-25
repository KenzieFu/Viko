package com.example.viko.view.screens.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.team.repository.WorkerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class OnboardingStage { CreateChannel, ManageChannel, Members, TeamReady }
data class OnboardingMember(val id: String, val name: String)
data class OnboardingUiState(
    val stage: OnboardingStage = OnboardingStage.CreateChannel,
    val channelName: String = "",
    val channelId: String = "",
    val members: List<OnboardingMember> = emptyList(),
    val replay: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
)

/** Owns the first-channel journey; replay never writes to either repository. */
class OnboardingViewModel(
    private val saved: SavedStateHandle,
    private val channels: ChannelRepository,
    private val workers: WorkerRepository
) : ViewModel() {
    private val form = MutableStateFlow(OnboardingUiState(
        stage = OnboardingStage.valueOf(saved["stage"] ?: OnboardingStage.CreateChannel.name),
        channelName = saved["channelName"] ?: "", channelId = saved["channelId"] ?: "",
        replay = saved["replay"] ?: false,
        members = (saved.get<ArrayList<String>>("demoNames") ?: arrayListOf()).mapIndexed { i, name ->
            OnboardingMember("demo-$i", name)
        }
    ))
    val uiState: StateFlow<OnboardingUiState> = combine(form, workers.observeWorkers()) { state, roster ->
        if (state.replay) state else state.copy(members = roster.filter { it.channelId == state.channelId }
            .map { OnboardingMember(it.id, it.name) })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, form.value)

    private fun update(transform: (OnboardingUiState) -> OnboardingUiState) {
        val state = transform(form.value)
        form.value = state
        saved["stage"] = state.stage.name
        saved["channelName"] = state.channelName
        saved["channelId"] = state.channelId
        saved["replay"] = state.replay
        saved["demoNames"] = ArrayList(state.members.map { it.name })
    }
    fun setReplay(replay: Boolean) = update { it.copy(replay = replay) }
    fun nameChanged(name: String) = update { it.copy(channelName = name, error = null) }
    fun createChannel() {
        val state = form.value
        if (state.saving || state.channelName.isBlank() || state.stage != OnboardingStage.CreateChannel) return
        update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val id = if (state.replay) "demo-channel" else "ch_${UUID.randomUUID()}"
                if (!state.replay) channels.addChannel(Channel(id, state.channelName.trim()))
                update { it.copy(channelId = id, channelName = state.channelName.trim(),
                    saving = false, stage = OnboardingStage.ManageChannel) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { update { it.copy(saving = false, error = "Could not create the channel. Please try again.") } }
        }
    }
    fun openMembers() = update { it.copy(stage = OnboardingStage.Members) }
    fun backToChannels() = update { it.copy(stage = OnboardingStage.ManageChannel) }
    fun saveRoster() {
        // Team-ready is meaningful only after at least one member was saved.
        if (uiState.value.members.isNotEmpty()) update { it.copy(stage = OnboardingStage.TeamReady) }
    }
    fun addDemoMember(name: String) {
        if (!form.value.replay || name.isBlank()) return
        update { it.copy(members = it.members + OnboardingMember("demo-${UUID.randomUUID()}", name.trim())) }
    }
    fun removeMember(id: String) {
        if (form.value.replay) update { it.copy(members = it.members.filterNot { m -> m.id == id }) }
        else if (uiState.value.members.any { it.id == id }) viewModelScope.launch {
            try { workers.removeWorker(id) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { update { it.copy(error = "Could not remove the member. Please try again.") } }
        }
    }
}
