package com.example.viko.domain.team.repository

import com.example.viko.domain.team.model.NearbyDevice
import kotlinx.coroutines.flow.Flow

/**
 * Discovers unpaired ViKo badges nearby.
 *
 * The real implementation is a BLE scan (`BluetoothLeScanner`) against badges
 * that were put into provisioning mode by a 5-second button hold. Until that
 * exists the UI runs against [com.example.viko.data.team.scanner.SimulatedDeviceScanner].
 */
interface DeviceScanner {
    /**
     * Emits the growing set of devices seen so far. Collecting starts a scan;
     * cancelling the collection stops it.
     */
    fun scan(): Flow<List<NearbyDevice>>

    /**
     * Provisions [device] — in the real transport this is where Wi-Fi
     * credentials go out over BLE and the badge reports back. Returns once the
     * badge is paired.
     */
    suspend fun pair(device: NearbyDevice)
}
