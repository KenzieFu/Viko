package com.example.viko.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** PTT streams include silent PCM: activity follows packet arrival, not loudness. */
internal class IncomingVoiceActivity(private val idleMs: Long = 650) {
    private val lastHeard = mutableMapOf<String, Long>()
    private val mutableActive = MutableStateFlow<Set<String>>(emptySet())
    val active = mutableActive.asStateFlow()

    @Synchronized fun heard(deviceId: String, nowMs: Long): Boolean {
        lastHeard[deviceId] = nowMs
        if (deviceId in mutableActive.value) return false
        mutableActive.value = lastHeard.keys.toSet()
        return true
    }

    @Synchronized fun expire(nowMs: Long) {
        val removed = lastHeard.entries.removeAll { nowMs - it.value >= idleMs }
        if (removed) mutableActive.value = lastHeard.keys.toSet()
    }

    @Synchronized fun clear() {
        lastHeard.clear()
        mutableActive.value = emptySet()
    }
}
