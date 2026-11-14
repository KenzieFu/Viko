package com.example.viko.data.network

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun File.writeVoiceWav(pcm: ByteArray) {
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray())
        putInt(36 + pcm.size)
        put("WAVEfmt ".toByteArray())
        putInt(16)
        putShort(1) // PCM
        putShort(1) // mono
        putInt(16_000)
        putInt(32_000)
        putShort(2)
        putShort(16)
        put("data".toByteArray())
        putInt(pcm.size)
    }.array()
    outputStream().use { out ->
        out.write(header)
        out.write(pcm)
    }
}
