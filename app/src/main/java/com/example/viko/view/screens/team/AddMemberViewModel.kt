package com.example.viko.view.screens.team

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.settings.model.FactoryWifi
import com.example.viko.domain.settings.repository.FactoryWifiRepository
import com.example.viko.domain.team.model.BadgeSetup
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.model.badgeDeviceId
import com.example.viko.domain.team.repository.DeviceScanner
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.utils.UI_STATE_TIMEOUT_MS
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Which frame of the add-member journey is showing. */
enum class AddMemberStep { Pairing, Setup }

data class AddMemberUiState(
    val step: AddMemberStep = AddMemberStep.Pairing,
    val devices: List<NearbyDevice> = emptyList(),
    val isScanning: Boolean = false,
    /** Sampled at each scan — the sheet says "turn Bluetooth on" rather than lying with an empty list. */
    val isBluetoothEnabled: Boolean = true,
    val pairedDevice: NearbyDevice? = null,
    val name: String = "",
    val zone: String = "",
    /**
     * The factory-wide network every badge is written onto. Comes from Settings,
     * not this form: every access point shares one SSID and password.
     */
    val factoryWifi: FactoryWifi = FactoryWifi(),
    val selectedChannelId: String = "",
    val channels: List<Channel> = emptyList(),
    /** BLE addresses of badges already on the roster — they cannot be set up twice. */
    val enrolledAddresses: Set<String> = emptySet(),
    /** True while the badge is being written over BLE — Save shows a spinner. */
    val isProvisioning: Boolean = false,
    /** Whatever the badge or the BLE stack said when provisioning failed. */
    val error: String? = null,
    /** How many members this run of the flow has added — drives the Done button. */
    val addedCount: Int = 0
) {
    val selectedChannelName: String? get() = channels.find { it.id == selectedChannelId }?.name

    /**
     * Save both stores the member and writes the badge, so the factory Wi-Fi
     * has to be set as surely as the name and the channel — provisioning
     * without an SSID produces a badge that saves a blank network and never
     * comes back.
     */
    val canSave: Boolean
        get() = name.isNotBlank() && selectedChannelName != null &&
            factoryWifi.isConfigured && !isProvisioning

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
    private val factoryWifiRepository: FactoryWifiRepository,
    private val deviceScanner: DeviceScanner
) : ViewModel() {

    private val form = MutableStateFlow(savedStateHandle.restoreForm())
    private var scanJob: Job? = null

    val uiState: StateFlow<AddMemberUiState> = combine(
        form,
        channelRepository.observeChannels(),
        workerRepository.observeWorkers(),
        factoryWifiRepository.observeFactoryWifi()
    ) { state, channels, workers, wifi ->
        state.copy(
            channels = channels,
            factoryWifi = wifi,
            // deviceAddress, not macAddress: deviceAddress is the BLE address the
            // scan reports, while macAddress is the Wi-Fi one the badge sends
            // later over the control channel. They differ on an ESP32.
            enrolledAddresses = workers.map { it.deviceAddress }.toSet()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS),
        initialValue = AddMemberUiState()
    )

    init {
        viewModelScope.launch { form.collect(savedStateHandle::persistForm) }
        // No scan here: BLUETOOTH_SCAN is a runtime permission, and starting
        // before the sheet has asked for it throws SecurityException. The sheet
        // calls rescan() once it has the grant.
    }

    /**
     * Clears the form.
     *
     * Called when the flow is dismissed, not when it opens: the whole point of
     * keeping the form here is that it outlives a recreation mid-edit. Resetting
     * on the way out gives the next open a clean sheet without costing that.
     */
    fun restart() {
        scanJob?.cancel()
        form.value = AddMemberUiState()
    }

    /**
     * Selecting a badge only moves the sheet forward — nothing is written to the
     * hardware yet. The badge needs a worker name and a zone in the same single
     * BLE write as the factory Wi-Fi, and neither has been typed at this point,
     * so the provisioning happens in [save].
     */
    fun select(device: NearbyDevice) = form.update {
        it.copy(pairedDevice = device, step = AddMemberStep.Setup, error = null)
    }

    fun onNameChange(name: String) = form.update { it.copy(name = name) }

    fun onZoneChange(zone: String) = form.update { it.copy(zone = zone) }

    /**
     * Sets the factory Wi-Fi from inside the flow. Onboarding opens pairing on
     * first launch, before anyone has been to Settings, so the network has to be
     * settable from here too. It is the same setting, not a per-member value.
     */
    fun saveFactoryWifi(wifi: FactoryWifi) = viewModelScope.launch {
        factoryWifiRepository.saveFactoryWifi(wifi)
    }

    fun dismissError() = form.update { it.copy(error = null) }

    fun selectChannel(channelId: String) = form.update { it.copy(selectedChannelId = channelId) }

    fun createChannel(name: String) = viewModelScope.launch {
        val channel = Channel(id = "ch_${UUID.randomUUID()}", name = name)
        channelRepository.addChannel(channel)
        form.update { it.copy(selectedChannelId = channel.id) }
    }

    /**
     * Writes the badge over BLE, then persists the member and returns to the
     * device list so the next badge can be set up — onboarding a shift means
     * doing several in a row, and closing the sheet is the supervisor's call
     * via Done.
     *
     * The worker row is only written if the badge confirmed it saved. Storing a
     * member whose hardware rejected the config would put a permanently offline
     * name on the roster with nothing to explain it.
     *
     * No-ops when the form is incomplete rather than inventing a placeholder
     * name; blank "New Worker" rows are how the old screen leaked junk into the
     * roster.
     */
    fun save() = viewModelScope.launch {
        val wifi = factoryWifiRepository.observeFactoryWifi().first()
        val state = form.value.copy(factoryWifi = wifi)
        val device = state.pairedDevice
        if (!state.canSave || device == null) return@launch

        val deviceId = badgeDeviceId(device.address)
        val zone = state.zone.trim().ifEmpty { DEFAULT_ZONE }

        form.update { it.copy(isProvisioning = true, error = null) }
        val result = deviceScanner.provision(
            device = device,
            setup = BadgeSetup(
                deviceId = deviceId,
                workerName = state.name.trim(),
                factoryZone = zone,
                wifiSsid = wifi.ssid.trim(),
                wifiPassword = wifi.password
            )
        )

        result.onFailure { failure ->
            form.update {
                it.copy(isProvisioning = false, error = failure.message ?: "Provisioning failed")
            }
        }
        if (result.isFailure) return@launch

        workerRepository.addWorker(
            Worker(
                // Keyed by the device_id just written to the badge, so the
                // device_register it sends over Wi-Fi lands on this same row
                // instead of creating a second one.
                id = deviceId,
                name = state.name.trim(),
                channelId = state.selectedChannelId,
                zone = zone,
                deviceAddress = device.address,
                // The Wi-Fi MAC is not knowable over BLE; the badge reports it
                // in device_register once it joins the network.
                macAddress = "",
                batteryLevel = FULL_BATTERY,
                // The badge is rebooting into station mode right now. It flips
                // online when it registers, which is the honest signal that the
                // whole chain works — not this write succeeding.
                isOnline = false
            )
        )

        form.update {
            it.copy(
                step = AddMemberStep.Pairing,
                pairedDevice = null,
                name = "",
                selectedChannelId = "",
                isProvisioning = false,
                addedCount = it.addedCount + 1
                // zone deliberately survives: a supervisor onboarding a line
                // usually pairs everyone standing in the same place.
            )
        }
    }

    /**
     * Starts a scan window. Called by the sheet once BLE permissions are in
     * hand, and again from "Scan again" — a badge that fell back to
     * provisioning mode only starts advertising ~20s after power-on, so missing
     * it on the first pass is routine.
     */
    fun rescan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            form.update {
                it.copy(
                    isScanning = true,
                    devices = emptyList(),
                    isBluetoothEnabled = deviceScanner.isBluetoothEnabled
                )
            }
            deviceScanner.scan().collect { devices ->
                form.update { it.copy(devices = devices) }
            }
            // The window closed; an empty list now means "no badges found",
            // not "still looking".
            form.update { it.copy(isScanning = false) }
        }
    }

    private companion object {
        const val DEFAULT_ZONE = "Unassigned Zone"
        const val FULL_BATTERY = 100
    }
}

// Only the fields the supervisor typed or chose are saved. The device list is
// not: after process death a rescan is the correct answer, not a stale list of
// badges that may no longer be in range.

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
    // No Wi-Fi here: it is a factory setting with its own store, not part of
    // the form.
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
        zone = get<String>(KEY_ZONE).orEmpty(),
        selectedChannelId = get<String>(KEY_CHANNEL_ID).orEmpty(),
        addedCount = get<Int>(KEY_ADDED_COUNT) ?: 0
    )
}

/**
 * NearbyDevice is a domain model and stays free of Android's Parcelable, so it
 * crosses SavedStateHandle as its own primitives.
 */
private fun NearbyDevice.toSavedList() = arrayListOf(
    id, name, address, signalStrength.toString()
)

private fun ArrayList<String>.toNearbyDevice(): NearbyDevice? {
    if (size != SAVED_DEVICE_FIELDS) return null
    return NearbyDevice(
        id = this[0],
        name = this[1],
        address = this[2],
        signalStrength = this[3].toIntOrNull() ?: return null
    )
}

private const val SAVED_DEVICE_FIELDS = 4
