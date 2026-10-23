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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class AddMemberStep { Pairing, Setup }

data class AddMemberUiState(
    val step: AddMemberStep = AddMemberStep.Pairing,
    val devices: List<NearbyDevice> = emptyList(),
    val isScanning: Boolean = false,

    val isNetworkAvailable: Boolean = true,
    val pairedDevice: NearbyDevice? = null,
    val name: String = "",
    val zone: String = "",
    val selectedChannelId: String = "",
    val channels: List<Channel> = emptyList(),
    val memberCounts: Map<String, Int> = emptyMap(),

    val enrolledIds: Set<String> = emptySet(),

    val isProvisioning: Boolean = false,

    val error: String? = null,

    val addedCount: Int = 0
) {
    val selectedChannelName: String? get() = channels.find { it.id == selectedChannelId }?.name

    val canSave: Boolean
        get() = name.isNotBlank() && selectedChannelName != null &&
            pairedDevice != null && !isProvisioning

    fun isEnrolled(device: NearbyDevice): Boolean = device.id in enrolledIds
}

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

            enrolledIds = workers.map { it.id }.toSet()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = AddMemberUiState()
    )

    init {
        viewModelScope.launch { form.collect(savedStateHandle::persistForm) }

    }

    fun restart() {
        scanJob?.cancel()
        form.value = AddMemberUiState()
    }

    fun select(device: NearbyDevice) = form.update {
        it.copy(pairedDevice = device, step = AddMemberStep.Setup, error = null)
    }

    fun onNameChange(name: String) = form.update { it.copy(name = name) }

    fun onZoneChange(zone: String) = form.update { it.copy(zone = zone) }

    fun dismissError() = form.update { it.copy(error = null) }

    fun selectChannel(channelId: String) = form.update { it.copy(selectedChannelId = channelId) }

    fun createChannel(name: String) = viewModelScope.launch {
        val channel = Channel(id = "ch_${UUID.randomUUID()}", name = name)
        channelRepository.addChannel(channel)
        form.update { it.copy(selectedChannelId = channel.id) }
    }

    fun save() = viewModelScope.launch {

        // Channels come from the repository; form flags must be read without UI-state lag.
        val state = form.value.copy(channels = uiState.value.channels)
        val device = state.pairedDevice
        if (!state.canSave || device == null) return@launch

        form.update { it.copy(isProvisioning = true, error = null) }
        try {
            val deviceId = device.id
            check(workerRepository.getWorker(deviceId) == null) {
                "This badge is already assigned to a member."
            }
            deviceScanner.confirmAvailable(device).getOrThrow()
            workerRepository.addWorker(
                Worker(
                    id = deviceId,
                    name = state.name.trim(),
                    channelId = state.selectedChannelId,
                    zone = state.zone.trim().ifEmpty { DEFAULT_ZONE },
                    deviceAddress = device.address,
                    macAddress = "", // Filled by the next registration.
                    batteryLevel = FULL_BATTERY,
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            form.update { it.copy(error = failure.message ?: "Could not add member") }
        } finally {
            form.update { it.copy(isProvisioning = false) }
        }
    }

    fun rescan() {
        val previous = scanJob
        scanJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            form.update {
                it.copy(
                    isScanning = true,
                    devices = emptyList(),
                    error = null,
                    isNetworkAvailable = deviceScanner.isNetworkAvailable
                )
            }
            try {
                deviceScanner.scan().collect { devices ->
                    form.update { it.copy(devices = devices, isNetworkAvailable = deviceScanner.isNetworkAvailable) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                form.update { it.copy(error = failure.message ?: "Wi-Fi discovery failed") }
            } finally {
                form.update { it.copy(isScanning = false) }
            }
        }
    }

    private companion object {
        const val DEFAULT_ZONE = "Unassigned Zone"
        const val FULL_BATTERY = 100
    }
}

private const val KEY_STEP = "add_member_step"
private const val KEY_NAME = "add_member_name"
private const val KEY_ZONE = "add_member_zone"
private const val KEY_CHANNEL_ID = "add_member_channel_id"
private const val KEY_ADDED_COUNT = "add_member_added_count"
private const val KEY_PAIRED_DEVICE = "add_member_paired_device"

private fun SavedStateHandle.persistForm(state: AddMemberUiState) {
    this[KEY_STEP] = state.step.name
    this[KEY_NAME] = state.name
    this[KEY_ZONE] = state.zone
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

        step = if (step == AddMemberStep.Setup && pairedDevice == null) AddMemberStep.Pairing else step,
        pairedDevice = pairedDevice,
        name = get<String>(KEY_NAME).orEmpty(),
        zone = get<String>(KEY_ZONE).orEmpty(),
        selectedChannelId = get<String>(KEY_CHANNEL_ID).orEmpty(),
        addedCount = get<Int>(KEY_ADDED_COUNT) ?: 0
    )
}

private fun NearbyDevice.toSavedList() = arrayListOf(
    id, name, address
)

private fun ArrayList<String>.toNearbyDevice(): NearbyDevice? {
    if (size != SAVED_DEVICE_FIELDS) return null
    return NearbyDevice(
        id = this[0],
        name = this[1],
        address = this[2]
    )
}

private const val SAVED_DEVICE_FIELDS = 3
