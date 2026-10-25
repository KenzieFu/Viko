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
import com.example.viko.type.CallState
import com.example.viko.view.screens.directcall.*
import com.example.viko.view.screens.previewWorker
import com.example.viko.view.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DirectCallUiTest {
    @get:Rule val compose = createComposeRule()
    private val member = previewWorker(name = "Nguyen Minh Tam", zone = "Central Building")
    private var state by mutableStateOf(DirectCallUiState(worker = member, voiceOnly = false))
    private var visible by mutableStateOf(true)
    private var starts = 0
    private var stops = 0
    private var cancels = 0

    @Test fun holdKeepsTheMicFixedAndReleaseStopsSpeaking() {
        showCall()
        compose.onNodeWithText("DIRECT CALL").assertIsDisplayed()
        compose.onNodeWithTag("direct-call-mic").assertWidthIsEqualTo(168.dp)
        val identity = compose.onNodeWithTag("direct-call-identity").fetchSemanticsNode().boundsInRoot
        val microphone = compose.onNodeWithTag("direct-call-mic").fetchSemanticsNode().boundsInRoot
        capture("direct-call-idle.png")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("direct-call-mic").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("direct-call-speaking-pulse").assertExists()
        compose.onNodeWithText("Central Building • Listening...").assertIsDisplayed()
        assertEquals(identity, compose.onNodeWithTag("direct-call-identity").fetchSemanticsNode().boundsInRoot)
        assertEquals(microphone, compose.onNodeWithTag("direct-call-mic").fetchSemanticsNode().boundsInRoot)
        capture("direct-call-speaking.png")
        compose.onNodeWithTag("direct-call-mic").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("direct-call-speaking-pulse").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, stops) }
    }

    @Test fun listeningUsesCompactDisabledMicAndKeepsCompletedTranslation() {
        state = state.copy(callState = CallState.LISTENING,
            translation = "Yes boss, I think we’ve got them handled already now. So it’s fine now.")
        showCall()
        compose.onNodeWithTag("direct-call-listening-pulse").assertExists()
        compose.onNodeWithTag("direct-call-transcript").assertHeightIsEqualTo(220.dp)
        compose.onNodeWithTag("direct-call-mic").assertWidthIsEqualTo(88.dp).assertIsNotEnabled()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(900)
        capture("direct-call-listening.png")
        compose.onNodeWithTag("direct-call-mic").performTouchInput { down(center); up() }
        compose.runOnIdle { assertEquals(0, starts); state = state.copy(callState = CallState.IDLE) }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("direct-call-listening-pulse").assertDoesNotExist()
        compose.onNodeWithTag("direct-call-transcript").assertIsDisplayed()
        compose.onNodeWithTag("direct-call-mic").assertIsEnabled()
    }

    @Test fun cancellationAndLeavingScreenDiscardTheHeldTurn() {
        showCall()
        compose.onNodeWithTag("direct-call-mic").performTouchInput { down(center) }
        compose.onNodeWithTag("direct-call-speaking-pulse").assertExists()
        compose.onNodeWithTag("direct-call-mic").performTouchInput { cancel() }
        compose.onNodeWithTag("direct-call-speaking-pulse").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, stops); assertTrue(cancels > 0) }
        compose.onNodeWithTag("direct-call-mic").performTouchInput { down(center) }
        compose.runOnIdle { visible = false }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(CallState.IDLE, state.callState); assertEquals(0, stops) }
    }

    @Test fun shortScreenWithLargeTextKeepsTranscriptAndMicReachable() {
        state = state.copy(callState = CallState.LISTENING,
            worker = member.copy(name = "Nguyen Minh Tam Long Member Name", zone = "Central Building North Wing"),
            translation = "A long translated message. ".repeat(15), isTranslating = true)
        compose.setContent {
            VikoTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    Box(Modifier.width(320.dp).height(520.dp)) { Content() }
                }
            }
        }
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.onNodeWithTag("direct-call-mic").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Translating…").performScrollTo().assertIsDisplayed()
    }

    @Test fun permissionAndOfflineFeedbackStayReachable() {
        state = state.copy(worker = member.copy(isOnline = false), voiceOnly = true,
            talkError = "Microphone permission needed to talk")
        showCall()
        compose.onNodeWithTag("direct-call-mic").assertIsNotEnabled()
        compose.onNodeWithText("Voice only — translation is off").assertIsDisplayed()
        compose.onNodeWithText("Microphone permission needed to talk").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithTag("direct-call-mic").assertDoesNotExist()
    }

    private fun showCall() {
        compose.setContent { VikoTheme { Surface(Modifier.fillMaxSize(), color = VBackground) { Content() } } }
    }

    @Composable private fun Content() {
        if (visible) DirectCallScreen(state, onBack = { visible = false },
            onStartTalking = { starts++; state = state.copy(callState = CallState.SPEAKING) },
            onStopTalking = { stops++; state = state.copy(callState = CallState.IDLE) },
            onCancelTalking = { cancels++; state = state.copy(callState = CallState.IDLE) })
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
