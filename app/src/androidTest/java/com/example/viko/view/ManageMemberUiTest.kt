package com.example.viko.view

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viko.domain.channel.model.Channel
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.screens.team.*
import com.example.viko.view.screens.onboarding.*
import com.example.viko.view.theme.VikoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ManageMemberUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyRosterKeepsHeaderPlusWithoutExtraAddButton() {
        compose.setContent {
            VikoTheme { ManageMemberScreen(ManageMemberUiState(isLoaded = true), {}, {}, {}, {}) }
        }
        compose.onNodeWithText("No member yet").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Add Member").assertCountEquals(1)
        compose.onNodeWithText("Add Member").assertDoesNotExist()
    }

    @Test fun demoOnboardingRetainsAddMember() {
        var addClicked = false
        compose.setContent {
            VikoTheme {
                OnboardingFlowContent(
                    state = OnboardingUiState(stage = OnboardingStage.Members, channelName = "Operations", replay = true),
                    onName = {}, onCreate = {}, onOpenChannel = {}, onAddMember = { addClicked = true },
                    onSaveRoster = {}, onRemove = {}, onFinished = {}
                )
            }
        }
        compose.onNodeWithText("Add New Member").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(addClicked) }
    }

    @Test fun rosterOpensDetailsAndFormActionsStillWork() {
        var saved = false
        var removed = false
        val channels = listOf(Channel("c1", "Operations"), Channel("c2", "Maintenance"))
        val workers = listOf("Tran Hoang Nam", "Nguyen Minh Tam", "Tran Quoc Nam", "Pham Bao Anh", "Hoang Mai Phuong").mapIndexed { index, name ->
            previewWorker("member-$index", name, "c1", zone = "Central Building",
                batteryLevel = if (index == 2) 10 else 70, isOnline = index < 3)
        }
        compose.setContent {
            var selected by remember { mutableStateOf<String?>(null) }
            var name by remember { mutableStateOf("") }
            var channelId by remember { mutableStateOf("c1") }
            VikoTheme {
                val worker = workers.find { it.id == selected }
                if (worker == null) {
                    ManageMemberScreen(
                        ManageMemberUiState(workers.map { MemberRow(it, "Operations") }, channels, isLoaded = true),
                        onBack = {}, onMemberClick = { selected = it; name = workers.first { w -> w.id == it }.name },
                        onSearchQueryChange = {}, onChannelFilterChange = {}
                    )
                } else {
                    MemberDetailScreen(
                        MemberDetailUiState(worker, channels, name, channelId),
                        onBack = { selected = null }, onNameChange = { name = it }, onSelectChannel = { channelId = it },
                        onCreateChannelAndSelect = {}, onSaveChanges = { saved = name == "Updated Member" && channelId == "c2" },
                        onRemoveWorker = { removed = true }
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Add Member").assertIsDisplayed()
        capture("manage-members.png")
        compose.onNodeWithText("Tran Hoang Nam").performClick()
        compose.onNodeWithText("Save Changes").assertIsDisplayed()
        compose.onNodeWithText("Remove Member").assertIsDisplayed()
        capture("member-details.png")
        compose.onNode(hasSetTextAction()).performTextReplacement("Updated Member")
        compose.onNodeWithText("Operations").performClick()
        compose.onNodeWithText("Maintenance").performClick()
        compose.onNodeWithText("Save Changes").performClick()
        compose.runOnIdle { assertTrue(saved) }
        compose.onNodeWithText("Remove Member").performClick()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.runOnIdle { assertFalse(removed) }
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(removed) }
        compose.onNodeWithText("Manage Member").assertIsDisplayed()
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
