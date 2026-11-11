package com.example.viko.view

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viko.VikoApplication
import com.example.viko.data.network.TranslationStatus
import com.example.viko.data.settings.TranslationPreferences
import com.example.viko.view.screens.settings.SettingsScreen
import com.example.viko.view.theme.VikoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsTranslationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun switchPersistsAndChangesCaptureForNewTurns() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as VikoApplication
        val container = app.container
        val preferences = container.translationPreferences
        val original = preferences.enabled.value
        try {
            preferences.setEnabled(true)
            compose.setContent {
                VikoTheme {
                    SettingsScreen(
                        onBack = {},
                        onOpenManageMembers = {},
                        onOpenManageChannels = {},
                        onReplayOnboarding = {}
                    )
                }
            }
            val registry = container.badgeRegistry
            fun waitForSetupCheck(): TranslationStatus {
                compose.waitUntil(12_000) {
                    val status = registry.translationStatus.value
                    status != TranslationStatus.OFF && status != TranslationStatus.CHECKING
                }
                return registry.translationStatus.value
            }
            val readyStatus = waitForSetupCheck()
            InstrumentationRegistry.getArguments().getString("expectTranslationStatus")?.let {
                assertEquals(TranslationStatus.valueOf(it), readyStatus)
            }
            val toggle = compose.onNodeWithText("AI translation")
            toggle.assertIsOn().performClick()
            compose.waitUntil(5_000) { registry.translationStatus.value == TranslationStatus.OFF }
            toggle.assertIsOff()
            assertNull(container.badgeAudio.translationCapture)
            assertFalse(TranslationPreferences(app).enabled.value)

            toggle.performClick()
            assertEquals(readyStatus, waitForSetupCheck())
            toggle.assertIsOn()
            assertEquals(readyStatus == TranslationStatus.ACTIVE, container.badgeAudio.translationCapture != null)
            assertTrue(TranslationPreferences(app).enabled.value)
        } finally {
            preferences.setEnabled(original)
        }
    }
}
