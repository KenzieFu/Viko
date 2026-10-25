package com.example.viko.view

import androidx.compose.foundation.layout.fillMaxSize
import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.view.screens.home.ChannelSummary
import com.example.viko.view.screens.home.HomeScreen
import com.example.viko.view.screens.home.HomeUiState
import com.example.viko.view.screens.home.tutorial.TutorialOverlay
import com.example.viko.view.screens.home.tutorial.TutorialStep
import com.example.viko.view.screens.team.NearbyDeviceSheetContent
import com.example.viko.view.theme.VikoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PairingTutorialUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun entireTutorialCanFinishIncludingAnnouncementAndTeamSheet() {
        compose.setContent {
            TestTheme {
                HomeScreen(
                    uiState = HomeUiState(channels = listOf(ChannelSummary(Channel("team", "Team"), emptyList())),
                        hasMembers = true, isLoaded = true),
                    onOpenNotifications = {}, onOpenSettings = {}, onDirectCall = {},
                    onRenameWorker = { _, _ -> }, onRemoveWorker = {}, onAssignWorkerToChannel = { _, _ -> },
                    onResolveAlert = {}, startTutorial = true
                )
            }
        }
        compose.onNodeWithText("Start Tutorial").performTouchInput { click() }
        repeat(4) { step ->
            compose.onNodeWithText("Next").assertIsDisplayed().performTouchInput { click() }
            if (step == 0) capture("tutorial-announcement.png")
        }
        compose.onNodeWithText("Done").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithText("Skip All").assertDoesNotExist()
    }

    @Test fun largeTextAndBottomTargetKeepNextAndSkipVisible() {
        var next = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                TestTheme {
                    TutorialOverlay(TutorialStep.Announcement,
                        Rect(0f, 1800f, 1000f, 2200f), { next = true }, {})
                }
            }
        }
        compose.onNodeWithText("Skip All").assertIsDisplayed()
        compose.onNodeWithText("Next").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(next) }
    }

    @Test fun bluetoothOffOffersEnableAndSettingsActions() {
        var enables = 0
        var settings = 0
        compose.setContent {
            TestTheme {
                NearbyDeviceSheetContent(emptyList(), false, true, false, { false }, 0,
                    {}, { enables++ }, {}, {}, onBluetoothSettings = { settings++ })
            }
        }
        capture("pairing-bluetooth-off.png")
        compose.onNodeWithText("Turn on Bluetooth").assertIsDisplayed().performClick()
        compose.onNodeWithText("Bluetooth settings").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, enables); assertEquals(1, settings) }
    }

    @Test fun manyResultsDoNotHideFindNearbyDevices() {
        var scans = 0
        compose.setContent {
            TestTheme {
                NearbyDeviceSheetContent((1..30).map { NearbyDevice("$it", "ViKo-$it", "address-$it") },
                    false, true, true, { false }, 0, {}, { scans++ }, {}, {})
            }
        }
        compose.onNodeWithText("Find nearby devices").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, scans) }
    }

    @Test fun scanProgressAndPermissionActionsAreVisible() {
        compose.setContent {
            TestTheme {
                NearbyDeviceSheetContent(emptyList(), true, true, true, { false }, 0, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Searching nearby devices…").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun deniedPermissionOffersBothPermissionRequestAndAppSettings() {
        var requests = 0
        var settings = 0
        compose.setContent {
            TestTheme {
                NearbyDeviceSheetContent(emptyList(), false, false, false, { false }, 0,
                    {}, { requests++ }, {}, {}, onPermissionSettings = { settings++ })
            }
        }
        compose.onNodeWithText("Allow nearby devices").assertIsDisplayed().performClick()
        compose.onNodeWithText("Open app permissions").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, requests); assertEquals(1, settings) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Compose semantics can settle before SurfaceFlinger presents the window.
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        java.io.File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun launcherActivityRequestsPortrait() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getActivityInfo(ComponentName(context.packageName, "com.example.viko.MainActivity"), 0)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, info.screenOrientation)
        val permissions = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertTrue(permissions.contains("android.permission.BLUETOOTH_SCAN"))
        assertTrue(permissions.contains("android.permission.BLUETOOTH_CONNECT"))
    }
}

@androidx.compose.runtime.Composable
private fun TestTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    VikoTheme {
        androidx.compose.material3.Surface(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            color = com.example.viko.view.theme.VBackground,
            content = content
        )
    }
}
