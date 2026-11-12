package com.example.viko.data.network

import android.os.SystemClock
import android.util.Log
import com.example.viko.data.settings.TranslationPreferences
import com.example.viko.data.team.scanner.WifiDeviceScanner
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.data.ai.Speaker
import com.example.viko.data.ai.VietnameseVoice
import com.example.viko.data.ai.onevoice.OneVoicePipeline
import com.example.viko.data.ai.onevoice.VoiceDirection
import com.example.viko.data.ai.onevoice.tts.OfflineNeuralVoices
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

enum class TranslationStatus {
    OFF, CHECKING, ACTIVE, MODELS_MISSING, VOICES_MISSING, UNAVAILABLE
}

internal data class TranslationProgress(
    val latestTurn: Long,
    val pending: Int,
    val problem: String? = null
)

/**
 * Lists registrations for enrollment and updates already-enrolled workers.
 * A held PTT button becomes [WorkerStatus.SPEAKING], and an
 * emergency press becomes an [EmergencyAlert]. Every screen already observes
 * these repositories, so nothing in the UI layer needs to know a socket exists.
 *
 * Workers are keyed by the badge's permanent `device_id`, never by IP — DHCP
 * leases change on every reconnect (Detailed System.docx, "Topology").
 */
class BadgeRegistry internal constructor(
    private val server: BadgeControlServer,
    private val discovery: WifiDeviceScanner,
    private val audio: BadgeAudioServer,
    private val workers: WorkerRepository,
    private val channels: ChannelRepository,
    private val alerts: AlertRepository,
    private val messages: MessageRepository,
    private val modelDir: File,
    private val translationPreferences: TranslationPreferences,
    private val keepBothRecognizers: () -> Boolean,
    private val neuralVoices: OfflineNeuralVoices,
    private val speaker: () -> Speaker,
    private val voice: () -> VietnameseVoice
) {

    private val _translationProgress = MutableStateFlow<Map<String, TranslationProgress>>(emptyMap())
    internal val translationProgress = _translationProgress.asStateFlow()
    private val turnIds = AtomicLong()

    private fun startTurn(turn: Turn) {
        _translationProgress.update { all ->
            val current = all[turn.deviceId]
            val pending = (current?.pending ?: 0) + 1
            val next = if (current == null || turn.id > current.latestTurn) {
                TranslationProgress(turn.id, pending)
            } else {
                current.copy(pending = pending)
            }
            all + (turn.deviceId to next)
        }
    }

    private fun finishTurn(turn: Turn, problem: String?) {
        _translationProgress.update { all ->
            val current = all[turn.deviceId] ?: return@update all
            val next = current.copy(
                pending = current.pending - 1,
                problem = if (turn.id == current.latestTurn) problem else current.problem
            )
            if (next.pending == 0 && next.problem == null) all - turn.deviceId
            else all + (turn.deviceId to next)
        }
    }

    private val _translationStatus = MutableStateFlow(TranslationStatus.OFF)
    val translationStatus = _translationStatus.asStateFlow()

    private val directCallOpened = MutableStateFlow(false)

    fun prepareDirectCall() {
        directCallOpened.value = true
    }

    private data class Turn(
        val id: Long, val deviceId: String, val pcm: ByteArray, val direction: VoiceDirection,
        val endedAt: Long, val queuedAt: Long = SystemClock.elapsedRealtime()
    )
    private data class Reply(val turn: Turn, val translation: String?)
    private val talkLock = Mutex()

    fun start(scope: CoroutineScope) {
        // Subscribe before accepting packets: SharedFlow has no replay.
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            server.packets.collect { packet -> talkLock.withLock { apply(packet) } }
        }
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            var previous = emptySet<String>()
            audio.activeBadges.collect { active ->
                talkLock.withLock {
                    for (id in (previous - active) + (active - previous)) {
                        val worker = workers.getWorker(id) ?: continue
                        // Read the current state after taking the lock: a database
                        // lookup may suspend while more packets arrive or expire.
                        val speaking = id in audio.activeBadges.value
                        if (speaking) {
                            if (worker.status != WorkerStatus.SPEAKING || !worker.isOnline)
                                workers.updateWorker(worker.copy(status = WorkerStatus.SPEAKING, isOnline = true))
                        } else {
                            clearSpeaking(id)
                        }
                        Log.i(TAG, "Live voice $id ${if (speaking) "speaking" else "idle"} from UDP activity")
                    }
                    previous = active
                }
            }
        }
        server.start(scope)
        audio.start(scope)
        scope.launch {
            workers.observeWorkers().collect { rows ->
                audio.translationWorkerIds = rows.mapTo(mutableSetOf()) { it.id }
                audio.setSavedBadgeAddresses(rows.filter { it.isPaired }.associate { it.id to it.deviceAddress })
            }
        }
        scope.launch {
            // Speaking is transient even when the app was killed mid-utterance.
            val savedWorkers = workers.observeWorkers().first()
            talkLock.withLock {
                for (worker in savedWorkers) {
                    if (worker.status == WorkerStatus.SPEAKING && worker.id !in audio.activeBadges.value)
                        clearSpeaking(worker.id)
                }
            }
        }
        scope.launch(Dispatchers.Default) {
            val turns = Channel<Turn>(2)
            fun enqueue(id: String, pcm: ByteArray, direction: VoiceDirection, endedAt: Long) {
                val turn = Turn(turnIds.incrementAndGet(), id, pcm, direction, endedAt)
                startTurn(turn)
                if (turns.trySend(turn).isFailure) {
                    finishTurn(turn, "Translation busy. Try speaking again.")
                    Log.w(TAG, "Translation busy; skipping turn")
                }
            }
            val capture = BadgeAudioServer.TranslationCapture(
                isEnabled = { translationPreferences.enabled.value },
                worker = { id, pcm, endedAt -> enqueue(id, pcm, VoiceDirection.Worker, endedAt) },
                manager = { id, pcm, endedAt -> enqueue(id, pcm, VoiceDirection.Manager, endedAt) }
            )
            try {
                OneVoicePipeline(modelDir, keepBothRecognizers).use { pipeline ->
                    coroutineScope {
                        val modelAccess = Mutex()
                        val replies = Channel<Reply>(2)
                        // Keep speech in order while the next turn can already produce text.
                        val speech = launch {
                            for (reply in replies) playReply(reply)
                        }
                        val settings = launch {
                            translationPreferences.enabled.collectLatest { enabled ->
                                audio.translationCapture = null
                                val status = if (enabled) {
                                    _translationStatus.value = TranslationStatus.CHECKING
                                    try {
                                        checkTranslationSetup()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Could not check OneVoice setup", e)
                                        TranslationStatus.UNAVAILABLE
                                    }
                                } else {
                                    TranslationStatus.OFF
                                }
                                if (status == TranslationStatus.ACTIVE) {
                                    audio.translationCapture = capture
                                }
                                _translationStatus.value = status
                            }
                        }
                        val warmup = launch { prepareModels(pipeline, modelAccess) }
                        try {
                            for (turn in turns) handleTurn(pipeline, turn, modelAccess, replies)
                        } finally {
                            replies.cancel()
                            speech.cancel()
                            settings.cancel()
                            warmup.cancel()
                        }
                    }
                }
            } finally {
                audio.translationCapture = null
                _translationProgress.value = emptyMap()
                _translationStatus.value = TranslationStatus.OFF
                turns.close()
                neuralVoices.close()
            }
        }
    }

    private suspend fun checkTranslationSetup(): TranslationStatus {
        if (!android.os.Process.is64Bit()) return TranslationStatus.UNAVAILABLE
        if (!OneVoicePipeline.installed(modelDir)) return TranslationStatus.MODELS_MISSING
        if (neuralVoices.installed()) return TranslationStatus.ACTIVE

        val korean = speaker()
        val vietnamese = voice()
        val ready = withTimeoutOrNull(8_000) {
            // Installed voices can become available shortly after the engine connects.
            while (korean.status !is Speaker.Status.Ready || vietnamese.status !is VietnameseVoice.Status.Ready) {
                delay(500)
                korean.refreshOfflineVoice()
                vietnamese.refreshOfflineVoice()
            }
            true
        }
        return if (ready == true) TranslationStatus.ACTIVE else TranslationStatus.VOICES_MISSING
    }

    private suspend fun prepareModels(pipeline: OneVoicePipeline, modelAccess: Mutex) {
        combine(directCallOpened, translationStatus) { opened, status ->
            opened && status == TranslationStatus.ACTIVE
        }.distinctUntilChanged().collectLatest { prepare ->
            if (!prepare) return@collectLatest
            try {
                modelAccess.withLock { pipeline.prepareTranslation() }
                modelAccess.withLock { pipeline.prepareWorker() }
                modelAccess.withLock { pipeline.prepareManager() }
                neuralVoices.prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not prepare OneVoice models ahead of the call", e)
            } catch (e: LinkageError) {
                Log.w(TAG, "OneVoice libraries could not be loaded", e)
            }
        }
    }

    private suspend fun handleTurn(
        pipeline: OneVoicePipeline, turn: Turn, modelAccess: Mutex, replies: Channel<Reply>
    ) {
        val (_, deviceId, pcm, direction) = turn
        val startedAt = SystemClock.elapsedRealtime()
        var textFinished = false
        fun finishText(problem: String? = null) {
            if (textFinished) return
            textFinished = true
            finishTurn(turn, problem)
        }
        try {
            val worker = workers.getWorker(deviceId) ?: return
            val waitingAt = SystemClock.elapsedRealtime()
            val (modelWait, result) = modelAccess.withLock {
                val waited = SystemClock.elapsedRealtime() - waitingAt
                waited to pipeline.process(pcm, direction)
            }
            if (result == null || result.translation.isBlank()) {
                finishText("No translation for this turn. Try speaking again.")
                replies.send(Reply(turn, null))
                return
            }
            val translated = result.translation
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
            finishText()
            Log.i("VikoTurn", "turn=${turn.id} target=${direction.target} " +
                "end_to_text_ms=${SystemClock.elapsedRealtime() - turn.endedAt} " +
                "finalize_ms=${turn.queuedAt - turn.endedAt} queue_ms=${startedAt - turn.queuedAt} " +
                "model_wait_ms=$modelWait")
            replies.send(Reply(turn, translated))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OneVoice turn failed", e)
            finishText("Translation failed. Try speaking again.")
            replies.send(Reply(turn, null))
        } catch (e: LinkageError) {
            Log.e(TAG, "OneVoice native library unavailable", e)
            // Missing ABI libraries cannot be repaired during this process.
            audio.translationCapture = null
            if (translationPreferences.enabled.value) {
                _translationStatus.value = TranslationStatus.UNAVAILABLE
            }
            finishText("Translation unavailable.")
            replies.send(Reply(turn, null))
        } finally {
            finishText()
        }
    }

    private suspend fun playReply(reply: Reply) {
        val delivered = try {
            reply.translation?.let { speakTranslation(reply.turn, it) } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not play translated speech", e)
            false
        } catch (e: LinkageError) {
            Log.w(TAG, "Offline speech library unavailable", e)
            false
        }
        if (!delivered) replayOriginal(reply.turn)
    }

    private suspend fun speakTranslation(turn: Turn, text: String): Boolean {
        val deviceId = turn.deviceId
        val direction = turn.direction
        if (direction == VoiceDirection.Worker && audio.isTalking.value) return false
        if (neuralVoices.installed()) {
            val chunks = neuralVoices.stream(text, direction)
            val firstFrame = {
                Log.i("VikoTurn", "turn=${turn.id} target=${direction.target} " +
                    "end_to_first_dispatch_ms=${SystemClock.elapsedRealtime() - turn.endedAt}")
                Unit
            }
            return when (direction) {
                VoiceDirection.Worker -> audio.playLocalPcm(deviceId, chunks, firstFrame)
                VoiceDirection.Manager -> audio.sendPcm(deviceId, chunks, firstFrame)
            }
        }
        return when (direction) {
            VoiceDirection.Worker -> speaker().speak(text)
            VoiceDirection.Manager -> voice().synthesise(text)?.let { audio.sendPcm(deviceId, it) } ?: false
        }
    }

    private suspend fun replayOriginal(turn: Turn) {
        when (turn.direction) {
            VoiceDirection.Worker -> audio.playLocalPcm(turn.deviceId, turn.pcm)
            VoiceDirection.Manager -> audio.sendPcm(turn.deviceId, turn.pcm)
        }
    }

    private suspend fun clearSpeaking(deviceId: String) {
        if (deviceId in audio.activeBadges.value) return
        val worker = workers.getWorker(deviceId) ?: return
        if (worker.status == WorkerStatus.SPEAKING && deviceId !in audio.activeBadges.value)
            workers.updateWorker(worker.copy(status = WorkerStatus.AVAILABLE))
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
                // Audio packets own the speaking state and translation boundaries.
                if (known != null && !known.isOnline)
                    workers.updateWorker(known.copy(isOnline = true))
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
