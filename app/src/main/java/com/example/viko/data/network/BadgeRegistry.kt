package com.example.viko.data.network

import android.util.Log
import com.example.viko.data.team.scanner.WifiDeviceScanner
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.data.ai.Speaker
import com.example.viko.data.ai.SpeechPipeline
import com.example.viko.data.ai.VietnameseVoice
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.domain.alert.repository.AlertRepository
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.message.model.TranslationMessage
import com.example.viko.domain.message.repository.MessageRepository
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.type.WorkerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

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
    private val speech: () -> SpeechPipeline,
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

    fun start(scope: CoroutineScope) {
        // Subscribe before accepting packets: SharedFlow has no replay.
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            server.packets.collect(::apply)
        }
        server.start(scope)
        audio.start(scope)
        if (!VoiceMode.TRANSLATION_ENABLED) return

        // Runs on the receive thread, so it must not block: the cascade takes
        // seconds and there are datagrams queued behind it. Dispatchers.Default
        // rather than IO because this is CPU-bound inference, not waiting on a
        // socket, and it must not compete with the audio thread for IO workers.
        audio.onUtterance = { deviceId, pcm ->
            scope.launch(Dispatchers.Default) { translate(deviceId, pcm) }
        }
        audio.onSupervisorUtterance = { deviceId, pcm ->
            scope.launch(Dispatchers.Default) { answer(deviceId, pcm) }
        }
    }

    /**
     * One badge utterance: Vietnamese speech in, Korean spoken to the supervisor
     * and a transcript stored.
     *
     * Everything here is best-effort. The models are pushed by hand and any stage
     * may be missing, in which case the supervisor still heard the raw speech live
     * — [BadgeAudioServer] played it as it arrived — and this simply adds nothing.
     */
    private suspend fun translate(deviceId: String, pcm: ByteArray) {
        markTranslating(deviceId, true)
        val result = try {
            speech().process(pcm, sourceLanguage = "vi", targetLanguage = "ko")
        } catch (e: Exception) {
            Log.e(TAG, "cascade failed for $deviceId", e)
            return
        } finally {
            // In `finally`, so a crash or an unintelligible utterance clears the
            // indicator too. A spinner that never stops is worse than no spinner.
            markTranslating(deviceId, false)
        }
        if (!result.hasText) return

        val worker = workers.getWorker(deviceId)
        messages.addMessage(
            TranslationMessage(
                id = "$deviceId-${System.currentTimeMillis()}",
                workerId = deviceId,
                workerName = worker?.name ?: deviceId,
                channelId = worker?.channelId.orEmpty(),
                originalText = result.transcript.orEmpty(),
                translatedText = result.translation.orEmpty(),
                originalLang = result.sourceLanguage,
                targetLang = result.targetLanguage,
                timestamp = Instant.now().toString()
            )
        )

        // Only speak a real translation. Reading the Vietnamese aloud to a Korean
        // supervisor would be worse than the silence, and they already heard it.
        result.translation?.takeIf { it.isNotBlank() }?.let { speaker().speak(it) }
        Log.i(TAG, "$deviceId: '${result.transcript}' -> <${result.tag}> -> '${result.translation}'")
    }

    private suspend fun apply(packet: BadgePacket) {
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
                if (packet.speaking) audio.beginUtterance(packet.deviceId)
                else audio.endUtterance(packet.deviceId)

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

    /**
     * One supervisor turn: Korean speech in, Vietnamese spoken out of the badge.
     *
     * The mirror of [translate], sharing every stage — the same tagger, the same
     * MT weights with the direction flipped, and the same silence gate. Only the
     * ends differ: `whisper-ko` instead of `whisper-vi`, and the result goes back
     * over UDP as bytes rather than out of this phone's speaker.
     *
     * The badge already heard the raw Korean live while the button was held, so a
     * failure here costs the translation, not the channel.
     */
    private suspend fun answer(deviceId: String, pcm: ByteArray) {
        markTranslating(deviceId, true)
        val result = try {
            speech().process(pcm, sourceLanguage = "ko", targetLanguage = "vi")
        } catch (e: Exception) {
            Log.e(TAG, "reverse cascade failed for $deviceId", e)
            return
        } finally {
            markTranslating(deviceId, false)
        }
        val vietnamese = result.translation?.takeIf { it.isNotBlank() } ?: return

        val audioBytes = voice().synthesise(vietnamese)
        if (audioBytes == null) {
            Log.w(TAG, "could not synthesise Vietnamese for $deviceId")
            return
        }
        audio.sendPcm(deviceId, audioBytes)

        val worker = workers.getWorker(deviceId)
        messages.addMessage(
            TranslationMessage(
                id = "$deviceId-sup-${System.currentTimeMillis()}",
                workerId = deviceId,
                workerName = worker?.name ?: deviceId,
                channelId = worker?.channelId.orEmpty(),
                originalText = result.transcript.orEmpty(),
                translatedText = vietnamese,
                originalLang = result.sourceLanguage,
                targetLang = result.targetLanguage,
                timestamp = Instant.now().toString()
            )
        )
        Log.i(TAG, "$deviceId <- '${result.transcript}' -> <${result.tag}> -> '$vietnamese'")
    }

    private companion object {
        const val TAG = "VikoAI"
        const val UNKNOWN_BATTERY = 100
        const val UNASSIGNED = "Unassigned"
    }
}
