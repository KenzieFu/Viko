package com.example.viko.data.ai

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

private const val TAG = "VikoAI"

/**
 * Picks a voice for [locale] that works with no network, and installs it on
 * [tts]. Returns null when the only voices available need a server.
 *
 * **`setLanguage` returning success is not enough**, and this is the trap that
 * matters most for this product. Google's engine reports a language as available
 * when it has a *server-side* voice for it, with no local data at all. On a phone
 * with internet everything then works, so it passes every bench test; on the
 * factory floor, which has no internet by design, synthesis fails with a bare
 * `error status code: -4` and the supervisor's reply silently never reaches the
 * badge.
 *
 * Observed exactly that on the test device: `vi-VN` reported available,
 * `TTS dispatch: vi-vn-x-vic-server`, synthesis failed.
 *
 * So the check is not "is there a voice" but "is there a voice that does not need
 * a network". [Voice.isNetworkConnectionRequired] answers it, and
 * [TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED] rules out entries that are
 * advertised but not downloaded.
 */
internal fun selectOfflineVoice(tts: TextToSpeech, locale: Locale): Voice? {
    val candidates = runCatching { tts.voices }.getOrNull().orEmpty()
        .filter { it.locale.language == locale.language }

    if (candidates.isEmpty()) {
        Log.w(TAG, "no voices at all for ${locale.language}")
        return null
    }

    val offline = candidates.filter {
        !it.isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features
    }
    if (offline.isEmpty()) {
        Log.w(
            TAG,
            "${locale.language}: ${candidates.size} voice(s), all need a network " +
                "(${candidates.joinToString { it.name }})"
        )
        return null
    }

    // Highest quality among the local ones. They are all already downloaded, so
    // this costs nothing but sounds better.
    val best = offline.maxByOrNull { it.quality }!!
    return if (tts.setVoice(best) == TextToSpeech.SUCCESS) {
        Log.i(TAG, "${locale.language}: using offline voice ${best.name} (quality ${best.quality})")
        best
    } else {
        Log.w(TAG, "${locale.language}: engine refused offline voice ${best.name}")
        null
    }
}

/** The message a supervisor can actually act on, while the phone still has internet. */
internal fun noOfflineVoiceMessage(language: String): String =
    "No offline $language voice on this phone. Connect to the internet once, open " +
        "Settings > Text-to-speech > Google > Install voice data, and download " +
        "$language. Without it this works on Wi-Fi and fails silently on the factory floor."
