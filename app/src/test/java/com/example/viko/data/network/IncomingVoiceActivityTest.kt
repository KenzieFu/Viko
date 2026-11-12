package com.example.viko.data.network

import org.junit.Assert.*
import org.junit.Test

class IncomingVoiceActivityTest {
    @Test fun firstAudioMarksSpeakingWithoutWaitingForControl() {
        val activity = IncomingVoiceActivity()
        assertTrue(activity.active.value.isEmpty())
        activity.heard("badge", 10)
        assertEquals(setOf("badge"), activity.active.value)
    }

    @Test fun continuousAndSilentAudioKeepTheTalkActive() {
        val activity = IncomingVoiceActivity()
        for (time in 0L..10_000L step 20) {
            activity.heard("badge", time)
            activity.expire(time + 19)
            assertEquals(setOf("badge"), activity.active.value)
        }
    }

    @Test fun lostEndExpiresAfterShortGraceRatherThanStayingStuck() {
        val activity = IncomingVoiceActivity()
        activity.heard("badge", 100)
        activity.expire(749)
        assertEquals(setOf("badge"), activity.active.value)
        activity.expire(750)
        assertTrue(activity.active.value.isEmpty())
    }

    @Test fun shortWifiGapsDoNotFlickerTheStatus() {
        val activity = IncomingVoiceActivity()
        activity.heard("badge", 0)
        activity.expire(500)
        activity.heard("badge", 600)
        activity.expire(1000)
        assertEquals(setOf("badge"), activity.active.value)
    }

    @Test fun eachBadgeExpiresIndependently() {
        val activity = IncomingVoiceActivity()
        activity.heard("a", 0)
        activity.heard("b", 400)
        activity.expire(650)
        assertEquals(setOf("b"), activity.active.value)
    }

    @Test fun disconnectClearsAndNewPacketsCanReopenTalk() {
        val activity = IncomingVoiceActivity()
        activity.heard("badge", 0)
        activity.clear()
        assertTrue(activity.active.value.isEmpty())
        activity.heard("badge", 1)
        assertEquals(setOf("badge"), activity.active.value)
    }
}
