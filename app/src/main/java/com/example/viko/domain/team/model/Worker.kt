package com.example.viko.domain.team.model

import com.example.viko.type.WorkerStatus

data class Worker(
    val id: String,
    val name: String,
    val nameKorean: String = "",
    val channelId: String,
    val zone: String,
    val deviceAddress: String,
    val macAddress: String,
    val firmwareVersion: String = "1.0.2",
    val batteryLevel: Int,
    val isOnline: Boolean,
    val status: WorkerStatus = WorkerStatus.AVAILABLE,
    val isPaired: Boolean = true
)
