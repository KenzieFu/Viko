package com.example.viko.data.team.scanner

import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.domain.team.repository.DeviceScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Reuses TCP device_register packets; no Bluetooth or separate discovery protocol. */
class WifiDeviceScanner(
    private val networkAvailable: () -> Boolean,
    private val clock: () -> Long
) : DeviceScanner {
    private data class Seen(val device: NearbyDevice, val at: Long)
    private val seen = linkedMapOf<String, Seen>()
    override val isNetworkAvailable: Boolean get() = networkAvailable()

    @Synchronized
    fun record(device: NearbyDevice) {
        seen[device.id] = Seen(device, clock())
    }

    @Synchronized
    private fun recent(): List<NearbyDevice> {
        val now = clock()
        seen.entries.removeAll { now - it.value.at >= FRESH_MS }
        return seen.values.map { it.device }.sortedBy { it.id }
    }

    override fun scan(): Flow<List<NearbyDevice>> = flow {
        // Registration repeats every 10 seconds. Keep listening while the sheet is open.
        while (true) {
            emit(if (isNetworkAvailable) recent() else emptyList())
            delay(500)
        }
    }

    override suspend fun confirmAvailable(device: NearbyDevice): Result<Unit> =
        if (isNetworkAvailable && recent().any { it.id == device.id && it.address == device.address }) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(
                "Badge is no longer reachable on this Wi-Fi. Check both devices use the same router, then scan again."
            ))
        }

    companion object { const val FRESH_MS = 35_000L }
}
