package com.example.viko.domain.team.repository

import com.example.viko.domain.team.model.NearbyDevice
import kotlinx.coroutines.flow.Flow

/**
 * Finds badges that are **already** on the factory Wi-Fi, from the
 * `device_register` packets they send on joining it.
 *
 * Deliberately separate from [DeviceScanner]: that one is first-time pairing
 * over BLE, for a badge that has never had the credentials. This one is how a
 * provisioned badge is located afterwards — a DHCP lease can move, so an
 * address is only ever as good as the last registration.
 */
interface BadgeDiscovery {
    /** False when the phone is off the factory network; a scan then finds nothing. */
    val isNetworkAvailable: Boolean

    /** Emits the badges heard from so far. Cancelling stops listening. */
    fun scan(): Flow<List<NearbyDevice>>

    /** Require a recent registration before assigning this device to a member. */
    suspend fun confirmAvailable(device: NearbyDevice): Result<Unit>
}
