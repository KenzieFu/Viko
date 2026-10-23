package com.example.viko.domain.team.repository

import com.example.viko.domain.team.model.NearbyDevice
import kotlinx.coroutines.flow.Flow

/** Discovers badges registering through the same local Wi-Fi router. */
interface DeviceScanner {
    val isNetworkAvailable: Boolean
    fun scan(): Flow<List<NearbyDevice>>
    /** Require a recent registration before assigning this device to a member. */
    suspend fun confirmAvailable(device: NearbyDevice): Result<Unit>
}
