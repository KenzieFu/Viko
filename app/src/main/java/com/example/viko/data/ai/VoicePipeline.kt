package com.example.viko.data.ai

import android.util.Log
import com.example.viko.data.network.BadgeAudioServer
import com.example.viko.domain.message.model.TranslationMessage
import com.example.viko.domain.message.repository.MessageRepository
import com.example.viko.domain.team.repository.WorkerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The whole voice path, end to end: a badge's Vietnamese comes in as PCM and its
 * Korean goes back out as PCM.
 *
 * ```
 * badge PCM16 ─▶ SpeechToText ─▶ TranslationPipeline ─▶ PhoneTextToSpeech ─▶ badge
 *   (UDP 5002)      vi text          vi ─▶ ko text          ko PCM16       (UDP 5002)
 * ```
 *
 * Two of those three stages are the phone's own — only the middle one is a model
 * anybody has to install, and it is the one the team fine-tuned. Every stage
 * returns null instead of throwing when it cannot run, and a null anywhere ends
 * the utterance quietly: the supervisor still heard the worker live (the raw
 * chunks were played on arrival), so a half-installed phone degrades to the
 * working voice link the app shipped with rather than to silence.
 *
 * **Utterance boundaries come from the transport**, not from a timer here.
 * `BadgeAudioServer` already stops playback after 400ms of silence to reset
 * clock drift; that same moment is the end of a push-to-talk press, so it is
 * where the buffered audio is handed over. (The badge also sends `ptt_end` on
 * TCP 5001 — more precise, but it would couple this to the control channel to
 * gain a few tens of ms nobody can hear.)
 *
 * Stages are serialised by [lock]: one utterance is in flight at a time. ONNX
 * decoding is single-threaded by design ([TranslationPipeline]) and one
 * recogniser is shared, so a second concurrent utterance would corrupt both.
 * Push-to-talk makes overlap rare, and queueing beats dropping when it happens.
 */
class VoicePipeline(
    private val audio: BadgeAudioServer,
    private val speechToText: SpeechToText,
    private val translation: TranslationPipeline,
    private val textToSpeech: PhoneTextToSpeech,
    private val workers: WorkerRepository,
    private val messages: MessageRepository
) {

    private companion object {
        const val TAG = "VikoAI"
    }

    private val lock = Mutex()

    /**
     * Subscribes to finished utterances for the life of [scope]. Called once by
     * `VikoApplication`, alongside the servers themselves — a badge is answered
     * whether or not anyone has the app open on a screen.
     */
    fun start(scope: CoroutineScope) {
        audio.onUtterance = { deviceId, pcm ->
            // Launched, never inline: this runs on the UDP receive loop's
            // thread, and holding that for a two-second translation would drop
            // every datagram the OS is buffering behind it.
            scope.launch { process(deviceId, pcm) }
        }
        speechToText.downloadModel()
    }

    private suspend fun process(deviceId: String, pcm16: ByteArray) = lock.withLock {
        val vietnamese = speechToText.transcribe(pcm16) ?: return

        // Blocking and CPU-bound — hundreds of ms of greedy decoding.
        val korean = withContext(Dispatchers.Default) { translation.translate(vietnamese) }
        Log.i(TAG, "\"$vietnamese\" -> ${korean ?: "(no translation model)"}")

        // Stored either way. A transcript with no translation still tells the
        // supervisor what was said and tells us the ASR stage is alive, which is
        // otherwise invisible until a badge speaker plays something.
        record(deviceId, vietnamese, korean)
        if (korean == null) return

        val speech = textToSpeech.synthesize(korean) ?: return

        // ponytail: the Korean goes back to the badge, as asked — the round trip
        // the demo shows off. If the supervisor should hear it instead, this is
        // the one line to change: audio.playLocally(speech).
        audio.sendUtterance(deviceId, speech)
    }

    private suspend fun record(deviceId: String, vietnamese: String, korean: String?) {
        val worker = workers.getWorker(deviceId)
        messages.addMessage(
            TranslationMessage(
                // Timestamped, not device-keyed: every utterance is its own line
                // in the transcript, and a second sentence must not replace the first.
                id = "$deviceId-${System.currentTimeMillis()}",
                workerId = deviceId,
                workerName = worker?.name ?: deviceId,
                channelId = worker?.channelId.orEmpty(),
                originalText = vietnamese,
                translatedText = korean.orEmpty(),
                timestamp = "Just now"
            )
        )
    }
}
