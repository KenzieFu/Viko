package com.example.viko.data.ai.onevoice.tts

import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.FloatBuffer

internal class SeaG2p(dictionary: File) : Closeable {
    private var handle = openNative(dictionary.path.toByteArray(Charsets.UTF_8))

    init {
        check(handle != 0L) { "Unable to open the SEA-G2P dictionary" }
    }

    fun convert(text: String): String =
        checkNotNull(convertNative(handle, text.toByteArray(Charsets.UTF_8))).toString(Charsets.UTF_8)

    external fun project(weights: FloatBuffer, offset: Int, rows: Int, vector: FloatArray): FloatArray
    external fun projectInt8(
        weights: ByteBuffer, scales: FloatBuffer, firstRow: Int, rows: Int, vector: FloatArray
    ): FloatArray
    private external fun openNative(path: ByteArray): Long
    private external fun convertNative(handle: Long, input: ByteArray): ByteArray?
    private external fun closeNative(handle: Long)

    override fun close() {
        if (handle != 0L) closeNative(handle)
        handle = 0
    }

    companion object {
        init { System.loadLibrary("viko_vieneu") }
    }
}
