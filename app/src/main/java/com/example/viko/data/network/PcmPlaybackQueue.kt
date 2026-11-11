package com.example.viko.data.network

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Bounded FIFO jitter buffer. Late packets are dropped, never played backwards. */
internal class PcmPlaybackQueue {
    class Frame(val pcm: ByteArray, val receivedMs: Long)
    private val frames = ArrayBlockingQueue<Frame>(8) // at most 160ms PCM
    private var device: String? = null
    private var expected = 0L
    private var lastReceived = 0L
    var dropped = 0L
        private set
    var concealed = 0L
        private set
    var missing = 0L
        private set
    var overflow = 0L
        private set

    @Synchronized
    fun offer(deviceId: String, sequence: Long, pcm: ByteArray, nowMs: Long): Boolean {
        if (sequence !in 0..0xffffffffL || pcm.isEmpty() || pcm.size > 640 || pcm.size % 2 != 0)
            return false
        val idle = device == null || nowMs - lastReceived > 400
        if (!idle && device != deviceId) { dropped++; return false }
        val restart = idle || (sequence == 0L && expected > 1)
        if (restart) {
            frames.clear()
            device = deviceId
            expected = sequence
        }
        // Signed 32-bit difference also handles uint32 rollover.
        val gap = (sequence - expected).toInt()
        if (gap < 0) { dropped++; return false }
        missing += gap
        // Never evict received speech to make room for synthetic silence.
        // Reserve one free slot for the actual packet, even after a Wi-Fi burst.
        val concealCount = minOf(gap, 2, maxOf(0, frames.remainingCapacity() - 1))
        repeat(concealCount) {
            push(Frame(ByteArray(640), nowMs))
            concealed++
        }
        push(Frame(pcm, nowMs))
        expected = (sequence + 1) and 0xffffffffL
        lastReceived = nowMs
        return true
    }

    private fun push(frame: Frame) {
        if (!frames.offer(frame)) {
            frames.poll()
            frames.offer(frame)
            overflow++
        }
    }

    @Synchronized
    fun stats(): String = "queued=${frames.size} dropped=$dropped missing=$missing silence=$concealed overflow=$overflow"

    fun poll(timeoutMs: Long): Frame? = frames.poll(timeoutMs, TimeUnit.MILLISECONDS)

    @Synchronized
    fun clear() {
        frames.clear()
        device = null
        expected = 0
    }
}
