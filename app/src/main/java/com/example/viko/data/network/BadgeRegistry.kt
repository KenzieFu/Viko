package com.example.viko.data.network

import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.domain.alert.repository.AlertRepository
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.type.WorkerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Turns badge control packets into stored state: a badge that registers becomes
 * an online [Worker], a held PTT button becomes [WorkerStatus.SPEAKING], and an
 * emergency press becomes an [EmergencyAlert]. Every screen already observes
 * these repositories, so nothing in the UI layer needs to know a socket exists.
 *
 * Workers are keyed by the badge's permanent `device_id`, never by IP — DHCP
 * leases change on every reconnect (Detailed System.docx, "Topology").
 */
class BadgeRegistry(
    private val server: BadgeControlServer,
    private val audio: BadgeAudioServer,
    private val workers: WorkerRepository,
    private val channels: ChannelRepository,
    private val alerts: AlertRepository
) {

    fun start(scope: CoroutineScope) {
        server.start(scope)
        audio.start(scope)
        scope.launch { server.packets.collect(::apply) }
    }

    private suspend fun apply(packet: BadgePacket) {
        // Every control message is fresh evidence of where this badge is, and
        // the audio channel needs an address before the supervisor can talk to
        // a badge that has only registered.
        audio.rememberBadge(packet.deviceId, packet.source)

        val known = workers.getWorker(packet.deviceId)

        when (packet) {
            // addWorker is an upsert (WorkerDao inserts with REPLACE), so a badge
            // re-registering after a reboot updates in place instead of duplicating.
            is BadgePacket.Register -> workers.addWorker(
                known?.copy(
                    name = packet.workerName.ifEmpty { known.name },
                    zone = packet.factoryZone,
                    // deviceAddress is left alone: it holds the BLE address the
                    // pairing sheet matched on, and the badge reports its *Wi-Fi*
                    // MAC here. On an ESP32 those are different addresses, and
                    // overwriting one with the other makes an enrolled badge look
                    // unpaired and offerable again.
                    macAddress = packet.macAddress,
                    firmwareVersion = packet.firmwareVersion,
                    isOnline = true,
                    isPaired = true,
                    status = WorkerStatus.AVAILABLE
                ) ?: Worker(
                    id = packet.deviceId,
                    name = packet.workerName.ifEmpty { packet.workerId },
                    channelId = "",
                    zone = packet.factoryZone,
                    deviceAddress = packet.macAddress,
                    macAddress = packet.macAddress,
                    firmwareVersion = packet.firmwareVersion,
                    // The register packet carries no battery — firmware has no
                    // battery ADC wired yet. Corrected by the first emergency packet.
                    batteryLevel = UNKNOWN_BATTERY,
                    isOnline = true
                )
            )

            // A PTT from a badge we've never seen register is dropped rather than
            // conjuring a nameless worker: the badge retries registration every
            // 10s, so it will exist shortly and the next press will land.
            is BadgePacket.Ptt -> known?.let {
                workers.updateWorker(
                    it.copy(
                        status = if (packet.speaking) WorkerStatus.SPEAKING else WorkerStatus.AVAILABLE,
                        isOnline = true
                    )
                )
            }

            is BadgePacket.Emergency -> {
                if (known != null && packet.battery >= 0) {
                    workers.updateWorker(known.copy(batteryLevel = packet.battery, isOnline = true))
                }
                alerts.addAlert(
                    EmergencyAlert(
                        // Timestamped, not device-keyed: a second press is a second
                        // alert, and overwriting the first would hide it.
                        id = "${packet.deviceId}-${System.currentTimeMillis()}",
                        workerId = packet.deviceId,
                        workerName = packet.workerName.ifEmpty { known?.name ?: packet.deviceId },
                        zone = packet.zone.ifEmpty { known?.zone.orEmpty() },
                        team = known?.channelId?.let { channels.getChannel(it)?.name } ?: UNASSIGNED,
                        timeAgo = "Just now"
                    )
                )
            }
        }
    }

    private companion object {
        const val UNKNOWN_BATTERY = 100
        const val UNASSIGNED = "Unassigned"
    }
}
