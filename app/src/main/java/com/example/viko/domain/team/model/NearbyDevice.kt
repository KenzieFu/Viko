package com.example.viko.domain.team.model

/**
 * A badge seen in a BLE scan, before anything is known about it beyond its
 * advertisement. Battery and zone are not in here because a BLE advertisement
 * does not carry them — the badge only reports those once it is provisioned and
 * talking over the control channel.
 */
data class NearbyDevice(
    val id: String,
    val name: String,
    val address: String,
    val signalStrength: Int = -65
)
