package com.example.viko.domain.alert.model

import com.example.viko.type.AlertStatus

data class EmergencyAlert(
    val id: String,
    val workerId: String,
    val workerName: String,
    val zone: String,
    val team: String,
    val timeAgo: String,
    val status: AlertStatus = AlertStatus.ACTIVE
)
