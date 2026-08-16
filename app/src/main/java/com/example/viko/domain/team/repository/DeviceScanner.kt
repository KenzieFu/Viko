package com.example.viko.domain.team.repository

import com.example.viko.domain.team.model.BadgeSetup
import com.example.viko.domain.team.model.NearbyDevice
import kotlinx.coroutines.flow.Flow

/**
 * Discovers unpaired ViKo badges and writes their setup over BLE.
 *
 * A badge only advertises while it is unprovisioned — either fresh out of the
 * box or because its stored Wi-Fi credentials failed to connect, in which case
 * it falls back to advertising roughly 20 seconds after power-on. So an empty
 * scan is a normal, temporary state, not an error.
 */
interface DeviceScanner {
    /**
     * False when the phone's Bluetooth radio is switched off, in which case a
     * scan runs and finds nothing rather than failing. Read fresh at each scan.
     */
    val isBluetoothEnabled: Boolean

    /**
     * Emits the growing set of badges seen so far, then completes when the scan
     * window closes. Cancelling the collection stops the scan early.
     */
    fun scan(): Flow<List<NearbyDevice>>

    /**
     * Writes [setup] to [device] and waits for the badge to confirm it saved.
     *
     * On success the badge reboots into Wi-Fi station mode; it then registers
     * itself over the control channel, which is what actually brings the worker
     * online. Provisioning succeeding does not mean the badge reached the network.
     */
    suspend fun provision(device: NearbyDevice, setup: BadgeSetup): Result<Unit>
}
