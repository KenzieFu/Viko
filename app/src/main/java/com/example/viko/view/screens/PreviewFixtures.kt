package com.example.viko.view.screens

import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.team.model.Worker
import com.example.viko.type.WorkerStatus
import com.example.viko.view.screens.home.ChannelSummary

/**
 * Sample domain objects for @Preview only.
 *
 * Previews used to call `AppState()`, which seeded itself from mock data; that
 * went away with the Room migration, and a preview cannot build a ViewModel
 * because it has no Application or database. So the previews construct domain
 * models directly, and these helpers keep that from being retyped in every file.
 *
 * Not used by any screen at runtime.
 */

internal fun previewWorker(
    id: String = "01",
    name: String = "Budi",
    channelId: String = "c1",
    status: WorkerStatus = WorkerStatus.AVAILABLE,
    isOnline: Boolean = true,
    batteryLevel: Int = 82,
    zone: String = "Warehouse A"
) = Worker(
    id = id,
    name = name,
    channelId = channelId,
    zone = zone,
    deviceAddress = "VK-$id",
    macAddress = "AA:BB:CC:DD:EE:$id",
    batteryLevel = batteryLevel,
    isOnline = isOnline,
    status = status
)

internal fun previewChannel(id: String = "c1", name: String = "Warehouse") =
    Channel(id = id, name = name)

/** A channel with a mix of states: one speaking, one idle, one offline. */
internal fun previewChannelSummary(
    id: String = "c1",
    name: String = "Warehouse"
) = ChannelSummary(
    channel = previewChannel(id, name),
    members = listOf(
        previewWorker("01", "Budi", id, WorkerStatus.SPEAKING),
        previewWorker("02", "Sari", id),
        previewWorker("03", "Andi", id, isOnline = false, batteryLevel = 15)
    )
)
