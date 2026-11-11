package com.example.viko.data.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.util.Log

/** Request awake Wi-Fi only while voice is being sent/received; release after idle. */
internal class VoiceWifiLock(context: Context) {
    @Suppress("DEPRECATION")
    private val lock = context.applicationContext.getSystemService(WifiManager::class.java)
        ?.createWifiLock(
            if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            else WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "Viko:live-voice"
        )?.apply { setReferenceCounted(false) }
    private var lastVoiceMs = 0L

    @Synchronized fun touch() {
        lastVoiceMs = SystemClock.elapsedRealtime()
        try {
            if (lock?.isHeld == false) {
                lock.acquire()
                Log.i("VikoAudio", "Wi-Fi voice lock acquired")
            }
        } catch (e: RuntimeException) {
            Log.w("VikoAudio", "Wi-Fi voice lock unavailable", e)
        }
    }

    @Synchronized fun releaseIfIdle(capturing: Boolean) {
        if (!capturing && SystemClock.elapsedRealtime() - lastVoiceMs > 1000) release()
    }

    @Synchronized fun release() {
        try {
            if (lock?.isHeld == true) {
                lock.release()
                Log.i("VikoAudio", "Wi-Fi voice lock released")
            }
        } catch (e: RuntimeException) {
            Log.w("VikoAudio", "Could not release Wi-Fi voice lock", e)
        }
    }
}
