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
    @Test fun overloadKeepsOnlySixNewestFrames() {
        val q = PcmPlaybackQueue()
        repeat(100) { q.offer("a", it.toLong(), pcm(it), it * 20L) }
        repeat(6) { assertArrayEquals(pcm(94 + it), q.poll(0)!!.pcm) }
        assertNull(q.poll(0))
        assertEquals(94L, q.overflow)
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
}
