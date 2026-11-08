package com.example.viko.data.network

import android.util.Log
import com.example.viko.data.team.scanner.WifiDeviceScanner
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.data.ai.Speaker
import com.example.viko.data.ai.VietnameseVoice
import com.example.viko.data.ai.onevoice.OneVoicePipeline
import com.example.viko.data.ai.onevoice.VoiceDirection
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.domain.alert.repository.AlertRepository
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.message.model.TranslationMessage
import com.example.viko.domain.message.repository.MessageRepository
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.type.WorkerStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.io.File
import java.util.UUID

/**
 * Lists registrations for enrollment and updates already-enrolled workers.
 * A held PTT button becomes [WorkerStatus.SPEAKING], and an
 * emergency press becomes an [EmergencyAlert]. Every screen already observes
 * these repositories, so nothing in the UI layer needs to know a socket exists.
 *
 * Workers are keyed by the badge's permanent `device_id`, never by IP — DHCP
 * leases change on every reconnect (Detailed System.docx, "Topology").
 */
class BadgeRegistry(
    private val server: BadgeControlServer,
    private val discovery: WifiDeviceScanner,
    private val audio: BadgeAudioServer,
    private val workers: WorkerRepository,
    private val channels: ChannelRepository,
    private val alerts: AlertRepository,
    private val messages: MessageRepository,
    private val modelDir: File,
    private val keepBothRecognizers: () -> Boolean,
    private val speaker: () -> Speaker,
    private val voice: () -> VietnameseVoice
) {

    /**
     * `device_id`s whose speech is being transcribed and translated right now.
     *
     * The cascade takes seconds, and with the live relay off there is otherwise
     * nothing at all between the worker letting go of the button and the Korean
     * arriving — the supervisor cannot tell "thinking" from "broken". Screens
     * observe this to say which.
     */
    private val _translating = MutableStateFlow<Set<String>>(emptySet())
    val translating: StateFlow<Set<String>> = _translating.asStateFlow()

    private fun markTranslating(deviceId: String, active: Boolean) {
        _translating.update { if (active) it + deviceId else it - deviceId }
    }

    private val _translationEnabled = MutableStateFlow(false)
    val translationEnabled = _translationEnabled.asStateFlow()

    private data class Turn(val deviceId: String, val pcm: ByteArray, val direction: VoiceDirection)

    fun start(scope: CoroutineScope) {
        // Subscribe before accepting packets: SharedFlow has no replay.
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            server.packets.collect { apply(it, scope) }
        }
        server.start(scope)
        audio.start(scope)
        if (!android.os.Process.is64Bit() || !OneVoicePipeline.installed(modelDir)) return

        scope.launch(Dispatchers.Default) {
            val korean = speaker()
            val vietnamese = voice()
            withTimeoutOrNull(8_000) {
                // The engine can publish installed voices shortly after its
                // connection callback, especially after the phone boots.
                while (korean.status !is Speaker.Status.Ready ||
                    vietnamese.status !is VietnameseVoice.Status.Ready) {
                    delay(500)
                    korean.refreshOfflineVoice()
                    vietnamese.refreshOfflineVoice()
                }
            }
            if (korean.status !is Speaker.Status.Ready || vietnamese.status !is VietnameseVoice.Status.Ready) {
                Log.w(TAG, "OneVoice requires installed offline Korean and Vietnamese voices")
                return@launch
            }
            val turns = Channel<Turn>(2)
            fun enqueue(id: String, pcm: ByteArray, direction: VoiceDirection) {
                if (turns.trySend(Turn(id, pcm, direction)).isFailure) {
                    Log.w(TAG, "Translation busy; skipping turn")
                }
            }

            audio.onUtterance = { id, pcm -> enqueue(id, pcm, VoiceDirection.Worker) }
            audio.onSupervisorUtterance = { id, pcm -> enqueue(id, pcm, VoiceDirection.Manager) }
            _translationEnabled.value = true
            try {
                OneVoicePipeline(modelDir, keepBothRecognizers).use { pipeline ->
                    for (turn in turns) handleTurn(pipeline, turn)
                }
            } finally {
                _translationEnabled.value = false
                audio.onUtterance = null
                audio.onSupervisorUtterance = null
                turns.close()
            }
        }
    }

    private suspend fun handleTurn(pipeline: OneVoicePipeline, turn: Turn) {
        val (deviceId, pcm, direction) = turn
        val worker = workers.getWorker(deviceId) ?: return
        markTranslating(deviceId, true)
        try {
            val result = pipeline.process(pcm, direction)
            if (result == null) {
                replayOriginal(turn)
                return
            }
            val translated = result.translation
            if (translated.isNotBlank()) {
                messages.addMessage(
                    TranslationMessage(
                        id = UUID.randomUUID().toString(),
                        workerId = deviceId,
                        workerName = worker.name,
                        channelId = worker.channelId,
                        originalText = result.transcript,
                        translatedText = translated,
                        originalLang = direction.source,
                        targetLang = direction.target,
                        timestamp = Instant.now().toString()
                    )
                )
            }
            val delivered = translated.isNotBlank() && when (direction) {
                VoiceDirection.Worker -> !audio.isTalking.value && speaker().speak(translated)
                VoiceDirection.Manager -> voice().synthesise(translated)?.let {
                    audio.sendPcm(deviceId, it)
                } ?: false
            }
            if (!delivered) replayOriginal(turn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OneVoice turn failed", e)
            replayOriginal(turn)
        } catch (e: LinkageError) {
            Log.e(TAG, "OneVoice native library unavailable", e)
            // Missing ABI libraries cannot be repaired during this process.
            audio.onUtterance = null
            audio.onSupervisorUtterance = null
            _translationEnabled.value = false
            replayOriginal(turn)
        } finally {
            markTranslating(deviceId, false)
        }
    }

    private suspend fun replayOriginal(turn: Turn) {
        when (turn.direction) {
            VoiceDirection.Worker -> audio.playLocalPcm(turn.deviceId, turn.pcm)
            VoiceDirection.Manager -> audio.sendPcm(turn.deviceId, turn.pcm)
        }
    }

    private suspend fun apply(packet: BadgePacket, scope: CoroutineScope) {
        // Every control message is fresh evidence of where this badge is, and
        // the audio channel needs an address before the supervisor can talk to
        // a badge that has only registered.
        audio.rememberBadge(packet.deviceId, packet.source)

        val known = workers.getWorker(packet.deviceId)

        when (packet) {
            is BadgePacket.Register -> {
                discovery.record(NearbyDevice(packet.deviceId, "ViKo ${packet.deviceId}",
                    packet.source.hostAddress.orEmpty()))
                // Discovery is not enrollment. Preserve the manager's name, zone and channel.
                if (known != null) workers.updateWorker(known.copy(
                    deviceAddress = packet.source.hostAddress.orEmpty(),
                    macAddress = packet.macAddress,
                    firmwareVersion = packet.firmwareVersion,
                    isOnline = true,
                    isPaired = true
                ))
            }

            is BadgePacket.Ptt -> {
                // The utterance boundary. Deliberately driven by the button and not
                // by a silence timeout: BadgeAudioServer's IDLE_TIMEOUT_MS exists to
                // reset clock drift between the badge's I2S and this phone's DAC, and
                // a gap in speech is not the end of a turn.
                if (packet.speaking) {
                    if (known != null) audio.beginUtterance(packet.deviceId)
                } else {
                    audio.endUtterance(packet.deviceId, scope)
                }

                known?.let {
                    workers.updateWorker(
                        it.copy(
                            status = if (packet.speaking) WorkerStatus.SPEAKING else WorkerStatus.AVAILABLE,
                            isOnline = true
                        )
                    )
                }
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
                        workerName = known?.name ?: packet.workerName.ifEmpty { packet.deviceId },
                        zone = known?.zone ?: packet.zone,
                        team = known?.channelId?.let { channels.getChannel(it)?.name } ?: UNASSIGNED,
                        timeAgo = "Just now"
                    )
                )
            }
        }
    }

    private companion object {
        const val TAG = "VikoAI"
        const val UNASSIGNED = "Unassigned"
    }
}
