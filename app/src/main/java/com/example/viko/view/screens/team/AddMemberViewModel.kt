package com.example.viko.view.screens.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.DeviceScanner
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Which frame of the add-member journey is showing. */
enum class AddMemberStep { Pairing, Setup }

data class AddMemberUiState(
    val step: AddMemberStep = AddMemberStep.Pairing,
    val devices: List<NearbyDevice> = emptyList(),
    val isScanning: Boolean = true,
    val pairingDeviceId: String? = null,
    val pairedDevice: NearbyDevice? = null,
    val name: String = "",
    val selectedChannelId: String = "",
    val channels: List<Channel> = emptyList(),
    val memberCounts: Map<String, Int> = emptyMap()
) {
    val selectedChannelName: String? get() = channels.find { it.id == selectedChannelId }?.name

    /** The Save button stays dim until both fields are real — see [save]. */
    val canSave: Boolean get() = name.isNotBlank() && selectedChannelName != null
}

/**
 * Owns the whole "add a member" journey: pair a badge, name the person, pick a
 * channel, save. One ViewModel for all of it, because the channel picker has to
 * be able to write back into a half-filled setup form — the reason this stopped
 * being three separate nav destinations.
 */
class AddMemberViewModel(
    private val workerRepository: WorkerRepository,
    private val channelRepository: ChannelRepository,
    private val deviceScanner: DeviceScanner
) : ViewModel() {

    private val form = MutableStateFlow(AddMemberUiState())
    private var scanJob: Job? = null

    val uiState: StateFlow<AddMemberUiState> = combine(
        form,
        channelRepository.observeChannels(),
        workerRepository.observeWorkers()
    ) { state, channels, workers ->
        state.copy(
            channels = channels,
            memberCounts = workers.groupingBy { it.channelId }.eachCount()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = AddMemberUiState()
    )

    init {
        restart()
    }

    /** Clears the form and starts a fresh scan — called each time the flow opens. */
    fun restart() {
        scanJob?.cancel()
        form.value = AddMemberUiState()
        scanJob = viewModelScope.launch {
            deviceScanner.scan().collect { devices ->
                form.update { it.copy(devices = devices) }
            }
            // The scan finished; an empty list now means "no devices found",
            // not "still looking".
            form.update { it.copy(isScanning = false) }
        }
    }

    fun pair(device: NearbyDevice) = viewModelScope.launch {
        form.update { it.copy(pairingDeviceId = device.id) }
        deviceScanner.pair(device)
        // Figma has no "Device Paired!" confirmation — pairing advances the flow.
        form.update {
            it.copy(pairingDeviceId = null, pairedDevice = device, step = AddMemberStep.Setup)
        }
    }

    fun onNameChange(name: String) = form.update { it.copy(name = name) }

    fun selectChannel(channelId: String) = form.update { it.copy(selectedChannelId = channelId) }

    fun createChannel(name: String) = viewModelScope.launch {
        val channel = Channel(id = "ch_${UUID.randomUUID()}", name = name)
        channelRepository.addChannel(channel)
        form.update { it.copy(selectedChannelId = channel.id) }
    }

    /**
     * Persists the new member. No-ops when the form is incomplete rather than
     * inventing a placeholder name — blank "New Worker" rows are how the old
     * screen leaked junk into the roster.
     */
    fun save() = viewModelScope.launch {
        val state = form.value
        if (!state.canSave) return@launch

        val device = state.pairedDevice
        workerRepository.addWorker(
            Worker(
                id = "w_${UUID.randomUUID()}",
                name = state.name.trim(),
                channelId = state.selectedChannelId,
                zone = device?.location ?: DEFAULT_ZONE,
                deviceAddress = device?.address ?: UNKNOWN_ADDRESS,
                macAddress = device?.address ?: UNKNOWN_MAC,
                batteryLevel = device?.batteryLevel ?: FULL_BATTERY,
                isOnline = true
            )
        )
    }

    private companion object {
        const val DEFAULT_ZONE = "Zone 1"
        const val UNKNOWN_ADDRESS = "Unknown"
        const val UNKNOWN_MAC = "00:00:00:00:00:00"
        const val FULL_BATTERY = 100
    }
}
