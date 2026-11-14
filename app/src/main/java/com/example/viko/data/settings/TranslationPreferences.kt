package com.example.viko.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TranslationPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)
    private val requested = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled = requested.asStateFlow()
    private val diagnostics = MutableStateFlow(prefs.getBoolean(KEY_DIAGNOSTICS, false))
    val diagnosticsEnabled = diagnostics.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        requested.value = enabled
    }

    fun setDiagnosticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DIAGNOSTICS, enabled).apply()
        diagnostics.value = enabled
    }

    private companion object {
        const val KEY_ENABLED = "ai_translation_enabled"
        const val KEY_DIAGNOSTICS = "translation_diagnostics_enabled"
    }
}
