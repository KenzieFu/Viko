package com.example.viko.view

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.domain.channel.model.Channel
import com.example.viko.type.AlertStatus
import com.example.viko.view.screens.home.*
import com.example.viko.view.screens.notifications.NotificationsScreen
import com.example.viko.view.screens.notifications.NotificationsUiState
import com.example.viko.view.screens.notifications.components.AlertCard
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.theme.VBackground
import com.example.viko.view.theme.VikoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NotificationBannerUiTest {
    @get:Rule val compose = createComposeRule()
    private val alert = EmergencyAlert("alert-1", "worker-0", "Minh Luat Nguyen", "Zone 3", "Operations Team", "1m ago")

    @Test fun bannerExpiresAfterFiveSecondsWithoutMovingCardsOrResolvingAlert() {
        var resolved = false
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var showNotifications by remember { mutableStateOf(false) }
            VikoTheme {
                Surface(Modifier.fillMaxSize(), color = VBackground) {
                    if (showNotifications) {
                        NotificationsScreen(NotificationsUiState(listOf(alert)), {}, {}, { resolved = true }, {})
                    } else {
                        HomeScreen(
                            uiState = HomeUiState(
                                channels = listOf("Operations", "Facilities", "Maintenance", "Security").mapIndexed { index, name ->
                                    val id = "channel-$index"
                                    ChannelSummary(Channel(id, name), listOf(previewWorker("worker-$index", "Member $index", id)))
                                },
                                activeAlert = alert, activeAlertCount = 1, hasMembers = true, isLoaded = true
                            ),
                            onOpenNotifications = { showNotifications = true }, onOpenSettings = {}, onDirectCall = {},
                            onRenameWorker = { _, _ -> }, onRemoveWorker = {}, onAssignWorkerToChannel = { _, _ -> },
                            onResolveAlert = { resolved = true }, startTutorial = false
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Emergency by ${alert.workerName}").assertIsDisplayed()
        val cardBounds = compose.onNodeWithText("Operations").fetchSemanticsNode().boundsInRoot
        capture("notification-popup.png")
        compose.mainClock.advanceTimeBy(4_700)
        compose.onNodeWithText("Emergency by ${alert.workerName}").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Emergency by ${alert.workerName}").assertDoesNotExist()
        assertEquals(cardBounds, compose.onNodeWithText("Operations").fetchSemanticsNode().boundsInRoot)
        assertFalse(resolved)
        capture("notification-dismissed.png")
        compose.onNodeWithContentDescription("Notification Button").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Emergency by ${alert.workerName}").assertIsDisplayed()
        compose.onNodeWithText("Resolve").assertIsDisplayed()
        capture("notification-page.png")
    }

    @Test fun alertActionsWorkAndResolvingRemovesOnlyTheActions() {
        var calls = 0
        compose.setContent {
            var resolved by remember { mutableStateOf(false) }
            VikoTheme {
                Box(Modifier.width(320.dp)) {
                    AlertCard(alert.copy(status = if (resolved) AlertStatus.RESOLVED else AlertStatus.ACTIVE),
                        onDirectCall = { calls++ }, onResolve = { resolved = true })
                }
            }
        }
        compose.onNodeWithText("Direct Call").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText("Resolve").assertIsDisplayed().performClick()
        compose.onNodeWithText("Resolved").assertIsDisplayed()
        compose.onNodeWithText("Direct Call").assertDoesNotExist()
        compose.onNodeWithText("Emergency by ${alert.workerName}").assertIsDisplayed()
    }

    @Test fun enlargedTextKeepsBothActionsReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                VikoTheme {
                    Box(Modifier.width(320.dp)) { AlertCard(alert, {}, {}) }
                }
            }
        }
        compose.onNodeWithText("Direct Call").assertIsDisplayed()
        compose.onNodeWithText("Resolve").assertIsDisplayed()
    }

    @Test fun notificationPageShowsMixedStatusesAndResolveAllClearsActions() {
        compose.setContent {
            var alerts by remember { mutableStateOf(listOf(
                alert,
                alert.copy(id = "alert-2", timeAgo = "13.00"),
                alert.copy(id = "alert-3", timeAgo = "10.00", status = AlertStatus.RESOLVED)
            )) }
            VikoTheme {
                NotificationsScreen(
                    uiState = NotificationsUiState(alerts), onBack = {}, onDirectCall = {},
                    onResolve = { id -> alerts = alerts.map { if (it.id == id) it.copy(status = AlertStatus.RESOLVED) else it } },
                    onResolveAll = { alerts = alerts.map { it.copy(status = AlertStatus.RESOLVED) } }
                )
            }
        }
        compose.onAllNodesWithContentDescription("Active emergency").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Resolved").assertCountEquals(1)
        compose.onAllNodesWithText("Direct Call").assertCountEquals(2)
        compose.onNodeWithText("Resolved").assertIsDisplayed()
        capture("notification-page-mixed.png")
        compose.onNodeWithText("Resolve All").performClick()
        compose.onAllNodesWithContentDescription("Resolved").assertCountEquals(3)
        compose.onAllNodesWithText("Resolved").assertCountEquals(3)
        compose.onNodeWithText("Direct Call").assertDoesNotExist()
        compose.onNodeWithText("Resolve").assertDoesNotExist()
        capture("notification-page-resolved.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        java.io.File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
