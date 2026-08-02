package com.example.viko.data.alert.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.type.AlertStatus

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val workerId: String,
    val workerName: String,
    val zone: String,
    val team: String,
    val timeAgo: String,
    val status: AlertStatus
)

fun AlertEntity.toDomain() = EmergencyAlert(
    id = id,
    workerId = workerId,
    workerName = workerName,
    zone = zone,
    team = team,
    timeAgo = timeAgo,
    status = status
)

fun EmergencyAlert.toEntity() = AlertEntity(
    id = id,
    workerId = workerId,
    workerName = workerName,
    zone = zone,
    team = team,
    timeAgo = timeAgo,
    status = status
)
