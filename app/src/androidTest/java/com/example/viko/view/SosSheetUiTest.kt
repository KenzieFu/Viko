package com.example.viko.view

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viko.domain.channel.model.Channel
import com.example.viko.view.screens.home.ChannelSummary
import com.example.viko.view.screens.home.HomeScreen
import com.example.viko.view.screens.home.HomeUiState
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VikoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SosSheetUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sheetSelectionSpeakingAndDismissalKeepTheLayoutStable() {
        compose.setContent {
            VikoTheme {
                Surface(Modifier.fillMaxSize(), color = VBackground) {
                    HomeScreen(
                        uiState = HomeUiState(
                            channels = listOf("Operations", "Facilities", "Maintenance", "Security").mapIndexed { index, name ->
                                val id = "channel-$index"
                                ChannelSummary(Channel(id, name), listOf(previewWorker("worker-$index", "Member $index", id)))
                            }, hasMembers = true, isLoaded = true
                        ),
                        onOpenNotifications = {}, onOpenSettings = {}, onDirectCall = {},
                        onRenameWorker = { _, _ -> }, onRemoveWorker = {}, onAssignWorkerToChannel = { _, _ -> },
                        onResolveAlert = {}, startTutorial = false
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("SOS").performClick()
        compose.onNodeWithContentDescription("Announce").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        capture("sos-idle.png")
        val gridBounds = compose.onNodeWithText("Fire").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Fire").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Fire").assertIsSelected()
        capture("sos-fire.png")
        compose.onNodeWithText("All Clear").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("All Clear").assertIsSelected()
        compose.onNodeWithText("Fire").assertIsNotSelected()
        capture("sos-clear.png")
        compose.onNodeWithText("All Clear").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("All Clear").assertIsNotSelected()
        compose.onNodeWithContentDescription("Announce").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Speaking...").assertIsDisplayed()
        compose.onNodeWithTag("sos-speaking-wave").assertExists()
        compose.onNodeWithText("Fire").assertIsNotEnabled()
        assertEquals(gridBounds, compose.onNodeWithText("Fire").fetchSemanticsNode().boundsInRoot)
        capture("sos-speaking.png")
        val waveBounds = compose.onNodeWithTag("sos-speaking-wave").fetchSemanticsNode().boundsInRoot
        val announceBounds = compose.onNodeWithContentDescription("Stop broadcasting").fetchSemanticsNode().boundsInRoot
        assertTrue(waveBounds.top >= announceBounds.top)
        compose.mainClock.advanceTimeBy(2_200)
        capture("sos-speaking-late.png")
        compose.onNodeWithContentDescription("Stop broadcasting").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("sos-speaking-wave").assertDoesNotExist()
        compose.onNodeWithText("Fire").assertIsEnabled()
        compose.onNodeWithContentDescription("Close SOS").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("sos-content").assertDoesNotExist()
        compose.onNodeWithText("Operations").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(200)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        java.io.File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
