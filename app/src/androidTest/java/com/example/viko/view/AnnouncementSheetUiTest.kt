package com.example.viko.view

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
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
import org.junit.Rule
import org.junit.Test

class AnnouncementSheetUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun announcementSheetKeepsCircleStableWhilePulseStartsAndStops() {
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
        compose.onNodeWithText("Make Announcement").performClick()
        compose.onNodeWithContentDescription("Start announcement").assertIsDisplayed()
        compose.onNodeWithTag("announcement-icon-wrapper").assertHeightIsEqualTo(320.dp)
        compose.mainClock.autoAdvance = false
        capture("announcement-idle.png")
        val buttonBounds = compose.onNodeWithContentDescription("Start announcement").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Start announcement").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Speaking...").assertIsDisplayed()
        compose.onNodeWithTag("announcement-speaking-pulse").assertExists()
        assertEquals(buttonBounds, compose.onNodeWithContentDescription("Stop announcement").fetchSemanticsNode().boundsInRoot)
        capture("announcement-speaking.png")
        compose.mainClock.advanceTimeBy(800)
        capture("announcement-wave-expanded.png")
        compose.onNodeWithContentDescription("Stop announcement").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("announcement-speaking-pulse").assertDoesNotExist()
        compose.onNodeWithContentDescription("Start announcement").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close announcement").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("announcement-icon-wrapper").assertDoesNotExist()
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
