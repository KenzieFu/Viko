package com.example.viko.view.screens.team

import androidx.lifecycle.SavedStateHandle
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
    val memberCounts: Map<String, Int> = emptyMap(),
    /** Addresses of badges already on the roster — they cannot be paired again. */
    val enrolledAddresses: Set<String> = emptySet(),
    /** How many members this run of the flow has added — drives the Done button. */
    val addedCount: Int = 0
) {
    val selectedChannelName: String? get() = channels.find { it.id == selectedChannelId }?.name

    /** The Save button stays dim until both fields are real — see [save]. */
    val canSave: Boolean get() = name.isNotBlank() && selectedChannelName != null

    fun isEnrolled(device: NearbyDevice): Boolean = device.address in enrolledAddresses
}

/**
 * Owns the whole "add a member" journey: pair a badge, name the person, pick a
 * channel, save. One ViewModel for all of it, because the channel picker has to
 * be able to write back into a half-filled setup form — the reason this stopped
 * being three separate nav destinations.
 *
 * The form is mirrored into [SavedStateHandle]. A ViewModel already survives the
 * configuration changes that used to eat a half-typed name — rotation, IME
 * insets, a system overlay — but not process death, and a supervisor who
 * backgrounds the app mid-pairing should not come back to an empty sheet either.
 */
class AddMemberViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val workerRepository: WorkerRepository,
    private val channelRepository: ChannelRepository,
    private val deviceScanner: DeviceScanner
) : ViewModel() {

    private val form = MutableStateFlow(savedStateHandle.restoreForm())
    private var scanJob: Job? = null

    val uiState: StateFlow<AddMemberUiState> = combine(
        form,
        channelRepository.observeChannels(),
        workerRepository.observeWorkers()
    ) { state, channels, workers ->
        state.copy(
            channels = channels,
            memberCounts = workers.groupingBy { it.channelId }.eachCount(),
            enrolledAddresses = workers.map { it.macAddress }.toSet()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = AddMemberUiState()
    )

    init {
        viewModelScope.launch { form.collect(savedStateHandle::persistForm) }
        startScan()
    }

    /**
     * Clears the form and starts a fresh scan.
     *
     * Called when the flow is dismissed, not when it opens: the whole point of
     * keeping the form here is that it outlives a recreation mid-edit. Resetting
     * on the way out gives the next open a clean sheet without costing that.
     */
    fun restart() {
        form.value = AddMemberUiState()
        startScan()
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
     * Persists the new member and returns to the device list so the next badge
     * can be paired — onboarding a shift means pairing several in a row, and
     * closing the sheet is the supervisor's call via Done.
     *
     * No-ops when the form is incomplete rather than inventing a placeholder
     * name; blank "New Worker" rows are how the old screen leaked junk into the
     * roster.
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

        form.update {
            it.copy(
                step = AddMemberStep.Pairing,
                pairedDevice = null,
                name = "",
                selectedChannelId = "",
                addedCount = it.addedCount + 1
            )
        }
    }

    private fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            deviceScanner.scan().collect { devices ->
                form.update { it.copy(devices = devices) }
            }
            // The scan finished; an empty list now means "no devices found",
            // not "still looking".
            form.update { it.copy(isScanning = false) }
        }
    }

    private companion object {
        const val DEFAULT_ZONE = "Zone 1"
        const val UNKNOWN_ADDRESS = "Unknown"
        const val UNKNOWN_MAC = "00:00:00:00:00:00"
        const val FULL_BATTERY = 100
    }
}

// Only the fields the supervisor typed or chose are saved. The device list is
// not: after process death a rescan is the correct answer, not a stale list of
// badges that may no longer be in range.

private const val KEY_STEP = "add_member_step"
private const val KEY_NAME = "add_member_name"
private const val KEY_CHANNEL_ID = "add_member_channel_id"
private const val KEY_ADDED_COUNT = "add_member_added_count"
private const val KEY_PAIRED_DEVICE = "add_member_paired_device"

private fun SavedStateHandle.persistForm(state: AddMemberUiState) {
    this[KEY_STEP] = state.step.name
    this[KEY_NAME] = state.name
    this[KEY_CHANNEL_ID] = state.selectedChannelId
    this[KEY_ADDED_COUNT] = state.addedCount
    this[KEY_PAIRED_DEVICE] = state.pairedDevice?.toSavedList()
}

private fun SavedStateHandle.restoreForm(): AddMemberUiState {
    val step = get<String>(KEY_STEP)
        ?.let { runCatching { AddMemberStep.valueOf(it) }.getOrNull() }
        ?: AddMemberStep.Pairing
    val pairedDevice = get<ArrayList<String>>(KEY_PAIRED_DEVICE)?.toNearbyDevice()

    return AddMemberUiState(
        // A restored Setup step with no device to set up is not a state the flow
        // can render, so fall back to the device list.
        step = if (step == AddMemberStep.Setup && pairedDevice == null) AddMemberStep.Pairing else step,
        pairedDevice = pairedDevice,
        name = get<String>(KEY_NAME).orEmpty(),
        selectedChannelId = get<String>(KEY_CHANNEL_ID).orEmpty(),
        addedCount = get<Int>(KEY_ADDED_COUNT) ?: 0
    )
}

/**
 * NearbyDevice is a domain model and stays free of Android's Parcelable, so it
 * crosses SavedStateHandle as its own primitives.
 */
private fun NearbyDevice.toSavedList() = arrayListOf(
    id, name, address, signalStrength.toString(), batteryLevel.toString(), location
)

private fun ArrayList<String>.toNearbyDevice(): NearbyDevice? {
    if (size != SAVED_DEVICE_FIELDS) return null
    return NearbyDevice(
        id = this[0],
        name = this[1],
        address = this[2],
        signalStrength = this[3].toIntOrNull() ?: return null,
        batteryLevel = this[4].toIntOrNull() ?: return null,
        location = this[5]
    )
}

private const val SAVED_DEVICE_FIELDS = 6
