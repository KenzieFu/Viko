package com.example.viko.data.ai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.measureTimeMillis

/**
 * Pins [LogMel] against `transformers`' `WhisperFeatureExtractor`.
 *
 * This is the highest-value test in the ASR stage. A feature extractor that is
 * subtly wrong — symmetric Hann instead of periodic, zero padding instead of
 * reflect, a missing dynamic-range clamp — produces a spectrogram that looks
 * entirely reasonable and silently degrades every transcript. There is no crash
 * and no error to notice, so it has to be caught by comparison.
 *
 * `vi_sample.wav` and `mel_fixture.json` are bundled as test assets; the fixture
 * comes from `export_onnx.py mel`, which runs the reference extractor on that
 * exact clip.
 *
 * Needs the filterbank pushed with the model (`scripts/push-models.sh whisper-vi`),
 * so it skips on a device with no models — but the fixture and audio are in the
 * APK, so nothing else is needed.
 */
@RunWith(AndroidJUnit4::class)
class LogMelTest {

    private companion object {
        const val TAG = "VikoBench"

        /**
         * int8 is not involved here — this is float arithmetic against float
         * reference values — but the FFT is a different implementation than
         * numpy's pocketfft, and the mel sum is 201 accumulations deep, so exact
         * equality is not reasonable. 1e-3 is far tighter than any difference
         * that would change a transcript, and far looser than FFT round-off.
         */
        const val TOLERANCE = 1e-3f
    }

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    private fun fixture(): JSONObject =
        JSONObject(context.assets.open("mel_fixture.json").bufferedReader().readText())

    /**
     * The PCM16 payload of a RIFF/WAVE file.
     *
     * Walks the chunk list rather than assuming the canonical 44-byte header.
     * `vi_sample.wav` carries a `LIST` chunk between `fmt ` and `data`, so its
     * audio starts at byte 78 — skipping a fixed 44 would feed 17 bytes of header
     * in as leading samples and shift the entire spectrogram, which is exactly how
     * this was found.
     */
    private fun pcm16Payload(name: String): Pair<ByteArray, Int> {
        val bytes = context.assets.open(name).readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "$name is not RIFF/WAVE" }

        var i = 12
        while (i + 8 <= bytes.size) {
            val id = String(bytes, i, 4)
            val size = buf.getInt(i + 4)
            if (id == "data") return bytes.copyOfRange(i + 8, i + 8 + size) to size
            i += 8 + size + (size and 1)      // chunks are word-aligned
        }
        error("$name has no data chunk")
    }

    private fun sampleAudio(): FloatArray {
        val (pcm, size) = pcm16Payload("vi_sample.wav")
        val buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(size / 2) { buf.short / 32768f }
    }

    private fun logMel(): LogMel {
        val filters = File(WhisperAsr.directory(target, "vi"), "mel_filters.bin")
        assumeTrue(
            "no mel_filters.bin — run scripts/push-models.sh whisper-vi",
            filters.exists()
        )
        return LogMel(filters)
    }

    @Test
    fun matchesTheReferenceExtractorOnRealAudio() {
        val mel = logMel()
        val audio = sampleAudio()
        val f = fixture()
        assertEquals("test asset is not the clip the fixture was made from",
            f.getInt("samples"), audio.size)

        lateinit var out: FloatArray
        val ms = measureTimeMillis { out = mel.extract(audio) }
        Log.i(TAG, "mel ${LogMel.MELS}x${LogMel.FRAMES} in ${ms}ms")

        assertEquals(LogMel.MELS * LogMel.FRAMES, out.size)

        // Statistics first: they fold in every frame, so a wrong window or hop
        // shows up here even when a hand-picked sample happens to agree.
        val mean = out.average().toFloat()
        val min = out.min()
        val max = out.max()
        val std = kotlin.math.sqrt(out.map { (it - mean) * (it - mean) }.average()).toFloat()
        Log.i(TAG, "mel mean=$mean std=$std min=$min max=$max")

        assertClose("mean", f.getDouble("mean").toFloat(), mean)
        assertClose("std", f.getDouble("std").toFloat(), std)
        assertClose("min", f.getDouble("min").toFloat(), min)
        assertClose("max", f.getDouble("max").toFloat(), max)
    }

    /**
     * Two individual columns. The statistics above would survive a spectrogram
     * that is correct overall but shifted in time; these would not.
     */
    @Test
    fun matchesTheReferenceAtSpecificFrames() {
        val out = logMel().extract(sampleAudio())
        val f = fixture()

        for ((frame, key) in listOf(0 to "frame0_first8", 100 to "frame100_first8")) {
            val want = f.getJSONArray(key)
            for (m in 0 until want.length()) {
                assertClose(
                    "mel[$m][$frame]",
                    want.getDouble(m).toFloat(),
                    out[m * LogMel.FRAMES + frame]
                )
            }
        }
    }

    /** The default window is still the full 30s, whatever the audio length. */
    @Test
    fun defaultsToTheFullThirtySecondWindow() {
        val mel = logMel()
        assertEquals(LogMel.MELS * LogMel.FRAMES, mel.extract(FloatArray(1600)).size)
        assertEquals(LogMel.MELS * LogMel.FRAMES, mel.extract(FloatArray(LogMel.N_SAMPLES * 2)).size)
    }

    /**
     * The variable window is what makes ASR affordable — the encoder is quadratic
     * in length — so the frame count must track the audio, stay even for the
     * stride-2 convolution, and never exceed what the model was trained on.
     */
    @Test
    fun sizesTheWindowToTheAudio() {
        val mel = logMel()
        for (seconds in listOf(0.5, 1.0, 4.6, 12.0, 45.0)) {
            val samples = (seconds * LogMel.SAMPLE_RATE).toInt()
            val frames = mel.framesFor(samples)
            assertTrue("$seconds s -> $frames frames, must be even", frames % 2 == 0)
            assertTrue("$seconds s -> $frames frames, out of range",
                frames in LogMel.MIN_FRAMES..LogMel.FRAMES)
            assertEquals(LogMel.MELS * frames, mel.extract(FloatArray(samples), frames).size)
        }
        // 45s of audio is clamped to the trained 30s window, not extrapolated.
        assertEquals(LogMel.FRAMES, mel.framesFor(45 * LogMel.SAMPLE_RATE))
    }

    /**
     * A shorter window must not change the frames it shares with a longer one —
     * beyond the dynamic-range clamp, which is relative to each window's own peak
     * and so legitimately differs. Checked on a region well inside both.
     */
    @Test
    fun aShorterWindowAgreesWithTheLongOneOnSharedFrames() {
        val mel = logMel()
        val audio = sampleAudio()
        val short = mel.framesFor(audio.size)
        assumeTrue("clip needs to be shorter than the full window", short < LogMel.FRAMES)

        val narrow = mel.extract(audio, short)
        val wide = mel.extract(audio, LogMel.FRAMES)
        Log.i(TAG, "window $short vs ${LogMel.FRAMES} frames")

        var worst = 0f
        for (m in 0 until LogMel.MELS) {
            for (t in 0 until short) {
                val d = kotlin.math.abs(narrow[m * short + t] - wide[m * LogMel.FRAMES + t])
                if (d > worst) worst = d
            }
        }
        Log.i(TAG, "worst shared-frame difference: $worst")
        // The clamp floor is (peak - 8), and silence padding cannot raise the peak,
        // so the two windows share a floor and should agree closely.
        assertTrue("windows disagree by $worst on shared frames", worst < 0.05f)
    }

    /** The PCM16 path is what the badge actually feeds, so check it agrees with floats. */
    @Test
    fun pcm16AndFloatPathsAgree() {
        val mel = logMel()
        val (pcm, size) = pcm16Payload("vi_sample.wav")

        val fromBytes = mel.fromPcm16(pcm, size)
        val fromFloats = mel.extract(sampleAudio())
        for (i in fromFloats.indices step 997) {
            assertClose("mel[$i]", fromFloats[i], fromBytes[i])
        }
    }

    private fun assertClose(what: String, want: Float, got: Float) {
        assertEquals("$what: reference $want, got $got", want, got, TOLERANCE)
    }
}
