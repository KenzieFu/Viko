package com.example.viko.data.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.File
import kotlin.coroutines.resume

/**
 * Vietnamese speech to Vietnamese text — the ASR stage of [VoicePipeline].
 *
 * **There is no ASR model in this app and none pushed to the phone.** Android's
 * on-device recogniser already has one, and it runs with the radio off once the
 * language pack is downloaded. That is why PhoWhisper is gone: 450MB of
 * hand-delivered ONNX plus a log-mel front end, to do what the platform does for
 * free. NLLB stays the only thing anyone has to `adb push`.
 *
 * Two things must be true on the demo phone, and compiling proves neither:
 *
 * - **API 33+.** [SpeechRecognizer.createOnDeviceSpeechRecognizer] and feeding
 *   it a file instead of the mic both landed in 33. Below that [isAvailable] is
 *   false and speech is relayed untranslated, exactly as it is today.
 * - **The vi-VN pack is on the device.** Call [downloadModel] once *while the
 *   phone still has internet* — the factory floor has none, and a missing pack
 *   looks identical to a broken pipeline from the outside.
 *
 * The other risk is that a vendor's `RecognitionService` ignores
 * `EXTRA_AUDIO_SOURCE` and sits waiting for a microphone that will never speak.
 * [TIMEOUT_MS] is what stops that from wedging the pipeline forever; if every
 * utterance takes exactly that long and returns nothing, this is why.
 *
 * One recogniser is reused across utterances (binding the service costs more
 * than keeping it), so calls must not overlap — [VoicePipeline] serialises them.
 */
class SpeechToText(private val context: Context) : Closeable {

    private companion object {
        const val TAG = "VikoAI"

        /** Vietnamese, as the badge's wearer speaks it. */
        const val LANGUAGE = "vi-VN"

        /** Must match the badge's SAMPLE_RATE — see BadgeAudioServer. */
        const val SAMPLE_RATE = 16_000

        /**
         * A recogniser that never answers must not hold the pipeline shut. Long
         * enough for a slow first run (the service cold-starts), short enough
         * that the supervisor is not left wondering.
         */
        const val TIMEOUT_MS = 10_000L

        /** ~0.3s. Shorter than this is a button tap, not a sentence. */
        const val MIN_BYTES = SAMPLE_RATE / 3 * 2
    }

    private val main = Handler(Looper.getMainLooper())

    /** Created lazily on the main thread — SpeechRecognizer requires it. */
    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context) &&
            hasMicPermission

    /**
     * Yes, really: [SpeechRecognizer] demands RECORD_AUDIO even when the audio
     * comes from a file and the mic is never opened.
     */
    private val hasMicPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Asks the platform to fetch the Vietnamese pack. Needs internet, so it is a
     * setup step, not something to call on the factory floor. Silent when the
     * pack is already there.
     */
    fun downloadModel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        main.post {
            runCatching { recognizer().triggerModelDownload(baseIntent()) }
                .onFailure { Log.w(TAG, "could not request the $LANGUAGE pack", it) }
        }
    }

    /**
     * Transcribes one utterance of PCM16 mono at [SAMPLE_RATE]. Returns null
     * when there is no recogniser, no pack, or nothing intelligible — callers
     * relay the original speech rather than going silent.
     */
    suspend fun transcribe(pcm16: ByteArray): String? {
        if (!isAvailable) {
            Log.i(TAG, "no on-device $LANGUAGE recogniser — relaying speech untranslated")
            return null
        }
        if (pcm16.size < MIN_BYTES) return null

        // A plain PCM dump, no WAV header: the intent already states the rate,
        // encoding and channel count, so there is nothing a header would add.
        val scratch = File(context.cacheDir, "utterance.pcm")
        runCatching { scratch.writeBytes(pcm16) }.onFailure {
            Log.e(TAG, "cannot stage audio for recognition", it)
            return null
        }

        val text = withTimeoutOrNull(TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                ParcelFileDescriptor.open(
                    scratch, ParcelFileDescriptor.MODE_READ_ONLY
                ).use { listen(it) }
            }
        }
        if (text == null) Log.w(TAG, "no transcript for ${pcm16.size / 2} samples")
        return text
    }

    /** Runs on the main thread; resumes from the recogniser's callbacks, also main. */
    private suspend fun listen(fd: ParcelFileDescriptor): String? =
        suspendCancellableCoroutine { cont ->
            val recognizer = recognizer()
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val best = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    cont.finish(best?.ifEmpty { null })
                }

                override fun onError(error: Int) {
                    // ERROR_NO_MATCH on floor noise is normal and not worth a
                    // stack trace; the rest usually means the pack is missing.
                    Log.w(TAG, "recognition failed (code $error)")
                    cont.finish(null)
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            // Timeout and scope cancellation both land here, on an arbitrary
            // thread — bounce back to main, where the recogniser insists on being.
            cont.invokeOnCancellation {
                main.post { runCatching { recognizer.cancel() } }
            }

            runCatching { recognizer.startListening(audioIntent(fd)) }
                .onFailure {
                    Log.e(TAG, "cannot start recognition", it)
                    cont.finish(null)
                }
        }

    /** The listener can fire twice (an error after a result); only the first counts. */
    private fun CancellableContinuation<String?>.finish(value: String?) {
        if (isActive) resume(value)
    }

    private fun recognizer(): SpeechRecognizer =
        recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            .also { recognizer = it }

    private fun baseIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        // Belt and braces: the on-device recogniser cannot go to the network
        // anyway, but a device that silently falls back to a cloud service would
        // be a spec violation nobody would notice until the Wi-Fi was air-gapped.
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    private fun audioIntent(fd: ParcelFileDescriptor) = baseIntent().apply {
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, fd)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
    }

    override fun close() {
        val doomed = recognizer ?: return
        recognizer = null
        main.post { runCatching { doomed.destroy() } }
    }
}
