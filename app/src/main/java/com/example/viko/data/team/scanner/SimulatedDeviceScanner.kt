package com.example.viko.data.team.scanner

import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.domain.team.repository.DeviceScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stand-in for the BLE scan until the transport layer exists. It emits a
 * plausible discovery sequence — devices trickle in, pairing takes a beat — so
 * the pairing UI can be built and demoed without hardware.
 *
 * The real implementation belongs beside this file (`BleDeviceScanner`) and
 * needs BLUETOOTH_SCAN / BLUETOOTH_CONNECT permissions declared in the manifest.
 */
class SimulatedDeviceScanner : DeviceScanner {

    override fun scan(): Flow<List<NearbyDevice>> = flow {
        emit(emptyList())
        delay(SCAN_WARMUP_MS)
        // Badges appear one at a time, the way a real advertisement sweep fills in.
        for (count in 1..SIMULATED_DEVICES.size) {
            emit(SIMULATED_DEVICES.take(count))
            delay(DEVICE_INTERVAL_MS)
        }
    }

    override suspend fun pair(device: NearbyDevice) {
        delay(PAIRING_MS)
    }

    private companion object {
        const val SCAN_WARMUP_MS = 800L
        const val DEVICE_INTERVAL_MS = 500L
        const val PAIRING_MS = 1400L

        val SIMULATED_DEVICES = listOf(
            NearbyDevice(id = "nd1", name = "VIKO-ESP32-A1B2", address = "AA:BB:CC:11:22:33", signalStrength = -45),
            NearbyDevice(id = "nd2", name = "VIKO-ESP32-C3D4", address = "AA:BB:CC:44:55:66", signalStrength = -67),
            NearbyDevice(id = "nd3", name = "VIKO-ESP32-E5F6", address = "AA:BB:CC:77:88:99", signalStrength = -78),
            NearbyDevice(id = "nd4", name = "VIKO-ESP32-G7H8", address = "AA:BB:CC:AA:BB:CC", signalStrength = -82),
        )
    }
}
