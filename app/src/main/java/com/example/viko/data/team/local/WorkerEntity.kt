package com.example.viko.data.team.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.viko.domain.team.model.Worker
import com.example.viko.type.WorkerStatus

@Entity(tableName = "workers")
data class WorkerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameKorean: String,
    val channelId: String,
    val zone: String,
    val deviceAddress: String,
    val macAddress: String,
    val firmwareVersion: String,
    val batteryLevel: Int,
    val isOnline: Boolean,
    val status: WorkerStatus,
    val isPaired: Boolean
)

fun WorkerEntity.toDomain() = Worker(
    id = id,
    name = name,
    nameKorean = nameKorean,
    channelId = channelId,
    zone = zone,
    deviceAddress = deviceAddress,
    macAddress = macAddress,
    firmwareVersion = firmwareVersion,
    batteryLevel = batteryLevel,
    isOnline = isOnline,
    status = status,
    isPaired = isPaired
)

fun Worker.toEntity() = WorkerEntity(
    id = id,
    name = name,
    nameKorean = nameKorean,
    channelId = channelId,
    zone = zone,
    deviceAddress = deviceAddress,
    macAddress = macAddress,
    firmwareVersion = firmwareVersion,
    batteryLevel = batteryLevel,
    isOnline = isOnline,
    status = status,
    isPaired = isPaired
)
