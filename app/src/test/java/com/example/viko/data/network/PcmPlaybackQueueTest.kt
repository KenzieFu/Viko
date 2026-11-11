package com.example.viko.data.network

import org.junit.Assert.*
import org.junit.Test

class PcmPlaybackQueueTest {
    private fun pcm(value: Int = 1) = ByteArray(640) { value.toByte() }

    @Test fun duplicateAndReorderedPacketsNeverRepeatSpeech() {
        val q = PcmPlaybackQueue()
        assertTrue(q.offer("a", 0, pcm(), 0))
        assertFalse(q.offer("a", 0, pcm(), 1))
        assertTrue(q.offer("a", 2, pcm(2), 40))
        assertFalse(q.offer("a", 1, pcm(), 41))
        assertArrayEquals(pcm(), q.poll(0)!!.pcm)
        assertArrayEquals(ByteArray(640), q.poll(0)!!.pcm)
        assertArrayEquals(pcm(2), q.poll(0)!!.pcm)
        assertNull(q.poll(0))
    }
    @Test fun overloadKeepsOnlyEightNewestFrames() {
        val q = PcmPlaybackQueue()
        repeat(100) { q.offer("a", it.toLong(), pcm(it), it * 20L) }
        repeat(8) { assertArrayEquals(pcm(92 + it), q.poll(0)!!.pcm) }
        assertNull(q.poll(0))
        assertEquals(92L, q.overflow)
    }
    @Test fun differentBadgesDoNotInterleaveAndIdleReleasesChannel() {
        val q = PcmPlaybackQueue()
        q.offer("a", 0, pcm(), 0)
        assertFalse(q.offer("b", 0, pcm(2), 20))
        assertTrue(q.offer("b", 5, pcm(2), 500))
        assertArrayEquals(pcm(2), q.poll(0)!!.pcm)
        assertNull(q.poll(0))
    }
    @Test fun restartLostFirstPacketAndHugeGapAreBounded() {
        val q = PcmPlaybackQueue()
        q.offer("a", 1, pcm(), 0) // first packet lost
        q.offer("a", 0, pcm(2), 100) // next PTT
        assertArrayEquals(pcm(2), q.poll(0)!!.pcm)
        q.offer("a", 1000000, pcm(), 120)
        assertEquals(2L, q.concealed)
        repeat(3) { assertNotNull(q.poll(0)) }
        assertNull(q.poll(0))
    }
    @Test fun malformedPcmAndSequenceAreRejected() {
        val q = PcmPlaybackQueue()
        assertFalse(q.offer("a", -1, pcm(), 0))
        assertFalse(q.offer("a", 0, ByteArray(1), 0))
        assertFalse(q.offer("a", 0, ByteArray(642), 0))
        assertFalse(q.offer("a", 0, ByteArray(0), 0))
        assertNull(q.poll(0))
    }
    @Test fun uint32WrapIsContinuous() {
        val q = PcmPlaybackQueue()
        q.offer("a", 0xffffffffL, pcm(), 0)
        assertTrue(q.offer("a", 0, pcm(2), 20))
        assertArrayEquals(pcm(), q.poll(0)!!.pcm)
        assertArrayEquals(pcm(2), q.poll(0)!!.pcm)
    }
    @Test fun lostPacketsDoNotReplaceQueuedSpeechWithSilence() {
        val q = PcmPlaybackQueue()
        repeat(7) { q.offer("a", it.toLong(), pcm(it + 1), it * 20L) }
        q.offer("a", 10, pcm(11), 200)
        repeat(7) { assertArrayEquals(pcm(it + 1), q.poll(0)!!.pcm) }
        assertArrayEquals(pcm(11), q.poll(0)!!.pcm)
        assertEquals(3L, q.missing)
        assertEquals(0L, q.concealed)
        assertEquals(0L, q.overflow)
        assertNull(q.poll(0))
    }
    @Test fun fullQueueSacrificesOnlyOneOldFrameForRealSpeech() {
        val q = PcmPlaybackQueue()
        repeat(8) { q.offer("a", it.toLong(), pcm(it + 1), it * 20L) }
        q.offer("a", 11, pcm(12), 220)
        repeat(7) { assertArrayEquals(pcm(it + 2), q.poll(0)!!.pcm) }
        assertArrayEquals(pcm(12), q.poll(0)!!.pcm)
        assertEquals(0L, q.concealed)
        assertEquals(1L, q.overflow)
    }
}
