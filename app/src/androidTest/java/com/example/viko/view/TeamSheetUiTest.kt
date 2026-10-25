package com.example.viko.view

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viko.domain.channel.model.Channel
import com.example.viko.type.WorkerStatus
import com.example.viko.view.screens.home.*
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TeamSheetUiTest {
    @get:Rule val compose = createComposeRule()
    private var workers by mutableStateOf(listOf(
        previewWorker("1", "Le Khanh Linh", "team", zone = "Zone 4", batteryLevel = 70),
        previewWorker("2", "Nguyen Minh Tam", "team", zone = "Central Building", batteryLevel = 70),
        previewWorker("3", "Tran Hoang Nam", "team", zone = "Central Building", batteryLevel = 10),
        previewWorker("4", "Pham Bao Anh", "team", isOnline = false),
        previewWorker("5", "Hoang Mai Phuong", "team", isOnline = false)
    ))
    private val removed = mutableListOf<String>()
    private val renamed = mutableListOf<Pair<String, String>>()
    private var called: String? = null
    private var assigned: Pair<String, String>? = null

    @Test fun liveSpeakingInterruptsTeamTalkWithoutAutomaticallyRestarting() {
        openTeam()
        compose.onNodeWithText("Operations (3/5)").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("team-sheet").fetchSemanticsNode().boundsInRoot.top > 100f)
        compose.onNodeWithContentDescription("Call Pham Bao Anh").assertIsNotEnabled()
        capture("team-default.png")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("talk-to-team").performClick()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText("Speaking...").assertIsDisplayed()
        compose.onNodeWithTag("team-speaking-pulse").assertExists()
        capture("team-speaking.png")
        compose.runOnIdle { workers = workers.map { if (it.id == "1") it.copy(status = WorkerStatus.SPEAKING) else it } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("talk-to-team").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Le Khanh Linh is speaking").assertIsDisplayed()
        compose.onNodeWithTag("team-speaking-pulse").assertDoesNotExist()
        capture("team-member-speaking.png")
        compose.runOnIdle { workers = workers.map { it.copy(status = WorkerStatus.AVAILABLE) } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("talk-to-team").assertIsEnabled()
        compose.onNodeWithText("Speaking...").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Call Le Khanh Linh").performClick()
        compose.runOnIdle { assertEquals("1", called) }
        compose.onNodeWithTag("team-sheet").assertDoesNotExist()
    }

    @Test fun editsAreSavedExplicitlyAndDeletingRequiresConfirmation() {
        openTeam()
        compose.onNodeWithContentDescription("Edit team").performClick()
        compose.onNodeWithText("Save").assertIsDisplayed()
        capture("team-edit.png")
        compose.onNodeWithText("Nguyen Minh Tam").performTextReplacement("Nguyen Tam")
        compose.runOnIdle { assertTrue(renamed.isEmpty()) }
        compose.onNodeWithContentDescription("Delete Hoang Mai Phuong").performClick()
        compose.onNodeWithText("Delete member?").assertIsDisplayed()
        capture("team-delete-confirm.png")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(removed.isEmpty()) }
        compose.onNodeWithText("Hoang Mai Phuong").assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete Hoang Mai Phuong").performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.runOnIdle { assertTrue(removed.isEmpty()) }
        compose.onNodeWithContentDescription("Delete Hoang Mai Phuong").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(listOf("5"), removed) }
        compose.onNodeWithText("Hoang Mai Phuong").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf("2" to "Nguyen Tam"), renamed) }
        compose.onNodeWithText("Operations (3/4)").assertIsDisplayed()
        compose.onNodeWithText("Nguyen Tam").assertIsDisplayed()
    }

    @Test fun addNewReusesMemberPickerAndEditingStopsTeamTalk() {
        openTeam()
        compose.onNodeWithTag("talk-to-team").performClick()
        compose.onNodeWithText("Speaking...").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit team").performClick()
        compose.onNodeWithTag("team-speaking-pulse").assertDoesNotExist()
        compose.onNodeWithText("Add New").performClick()
        compose.onNodeWithText("Search Member").assertIsDisplayed()
        compose.onNodeWithText("New colleague").performClick()
        compose.runOnIdle { assertEquals("6" to "team", assigned) }
        compose.onNodeWithText("Search Member").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Talk to Team").assertIsDisplayed()
        compose.onNodeWithText("Speaking...").assertDoesNotExist()
    }

    private fun openTeam() {
        compose.setContent {
            VikoTheme {
                Surface(Modifier.fillMaxSize(), color = VBackground) {
                    HomeScreen(
                        uiState = HomeUiState(channels = listOf(ChannelSummary(Channel("team", "Operations"), workers)),
                            unassignedToChannel = mapOf("team" to listOf(previewWorker("6", "New colleague", "other"))),
                            hasMembers = true, isLoaded = true),
                        onOpenNotifications = {}, onOpenSettings = {}, onDirectCall = { called = it },
                        onRenameWorker = { id, name -> renamed += id to name
                            workers = workers.map { if (it.id == id) it.copy(name = name) else it } },
                        onRemoveWorker = { id -> removed += id; workers = workers.filterNot { it.id == id } },
                        onAssignWorkerToChannel = { id, channel -> assigned = id to channel },
                        onResolveAlert = {}
                    )
                }
            }
        }
        compose.onNodeWithText("3/5").performClick()
        compose.onNodeWithText("Operations (3/5)").assertIsDisplayed()
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
