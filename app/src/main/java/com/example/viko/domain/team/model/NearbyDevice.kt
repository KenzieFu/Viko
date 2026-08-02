package com.example.viko.domain.team.model

data class NearbyDevice(
    val id: String,
    val name: String,
    val address: String,
    val signalStrength: Int = -65,
    val batteryLevel: Int = 70,
    val location: String = "Central Building"
)
