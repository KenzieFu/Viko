package com.example.viko.data.ai

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import java.io.Closeable
import java.util.Locale

/**
 * Speaks translated text out of the phone's loudspeaker.
 *
 * Android's built-in [TextToSpeech] rather than a bundled model. The four models
 * this app already pushes come to 2.8GB and a VITS voice would add ~78MB per
 * language for something the platform does natively — and does offline, once the
 * voice data is installed.
 *
 * **Offline is the whole point and it is not automatic.** Google's TTS downloads
 * voices on demand, and the factory floor has no internet. A missing voice does
 * not throw: it fails silently, which on a safety-adjacent channel is the worst
 * possible behaviour. So [status] is checked at startup and surfaced, rather than
 * discovered the first time a worker says something urgent.
 */
class Speaker(context: Context) : Closeable {

    companion object {
        private const val TAG = "VikoAI"

        /** What the supervisor hears. Vietnamese is Flow B's problem, not this one. */
        private val KOREAN = Locale.KOREAN
    }

    /** Why the Speaker cannot talk, or null when it can. */
    sealed interface Status {
        data object Ready : Status
        data object Initialising : Status
        data class Unavailable(val reason: String) : Status
    }

    @Volatile
    var status: Status = Status.Initialising
        private set

    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { code ->
            status = if (code != TextToSpeech.SUCCESS) {
                Status.Unavailable("text-to-speech engine failed to start")
            } else {
                configure()
            }
            Log.i(TAG, "tts: $status")
        }
    }

    private fun configure(): Status {
        val tts = engine ?: return Status.Unavailable("engine went away during startup")
        return when (tts.setLanguage(KOREAN)) {
            TextToSpeech.LANG_MISSING_DATA -> Status.Unavailable(
                // Actionable on purpose: this is fixed by installing the voice while
                // the phone still has internet, and nobody will guess that from a
                // generic failure.
                "Korean voice not installed — connect this phone to the internet once " +
                    "and install Korean under Settings > Text-to-speech"
            )
            TextToSpeech.LANG_NOT_SUPPORTED ->
                Status.Unavailable("this text-to-speech engine has no Korean voice")
            else -> {
                // Available is not the same as usable offline — see selectOfflineVoice.
                if (selectOfflineVoice(tts, KOREAN) == null) {
                    return Status.Unavailable(noOfflineVoiceMessage("Korean"))
                }
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        // MEDIA at loudspeaker volume, matching BadgeAudioServer's
                        // playback. VOICE_COMMUNICATION routes to the earpiece on most
                        // phones, which is useless on a factory floor.
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                Status.Ready
            }
        }
    }

    /**
     * Speaks one translated utterance, interrupting whatever is still playing.
     *
     * QUEUE_FLUSH rather than QUEUE_ADD: if a worker says two things quickly, the
     * newer one matters more, and a supervisor listening to a backlog is a
     * supervisor hearing stale instructions.
     *
     * Returns false when nothing was said, so the caller can fall back to showing
     * the text.
     */
    fun speak(korean: String): Boolean {
        if (korean.isBlank()) return false
        val tts = engine
        if (tts == null || status !is Status.Ready) {
            Log.w(TAG, "cannot speak, tts is $status")
            return false
        }
        val result = tts.speak(korean, TextToSpeech.QUEUE_FLUSH, null, "viko-utterance")
        if (result != TextToSpeech.SUCCESS) Log.w(TAG, "tts refused the utterance")
        return result == TextToSpeech.SUCCESS
    }

    fun stop() {
        runCatching { engine?.stop() }
    }

    override fun close() {
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }
}
