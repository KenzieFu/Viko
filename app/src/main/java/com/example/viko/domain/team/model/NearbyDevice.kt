package com.example.viko.domain.team.model

/** Stable firmware identity and current LAN address from a badge registration. */
data class NearbyDevice(
    val id: String,
    val name: String,
    val address: String
)
