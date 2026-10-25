package com.example.viko.view

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.view.screens.onboarding.*
import com.example.viko.view.screens.splash.SplashScreen
import com.example.viko.view.theme.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OnboardingPartOneUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun replayWalksEveryScreenAndDoesNotChangeRealData() {
        val store = ViewModelStore()
        val untouchedChannels = object : ChannelRepository {
            override fun observeChannels() = flowOf(emptyList<Channel>())
            override suspend fun getChannel(id: String): Channel? = null
            override suspend fun addChannel(channel: Channel) { error("Replay wrote a channel") }
        }
        val untouchedWorkers = object : WorkerRepository {
            override fun observeWorkers() = flowOf(emptyList<Worker>())
            override fun observeWorker(id: String) = flowOf<Worker?>(null)
            override suspend fun getWorker(id: String): Worker? = null
            override suspend fun addWorker(worker: Worker) { error("Replay wrote a member") }
            override suspend fun updateWorker(worker: Worker) { error("Replay updated a member") }
            override suspend fun removeWorker(id: String) { error("Replay removed a member") }
            override suspend fun assignToChannel(workerId: String, channelId: String) { error("Replay assigned a member") }
            override suspend fun renameWorker(workerId: String, name: String) { error("Replay renamed a member") }
        }
        lateinit var vm: OnboardingViewModel
        var finished = false
        compose.runOnUiThread {
            vm = OnboardingViewModel(SavedStateHandle(), untouchedChannels, untouchedWorkers)
            store.put("test", vm)
        }
        try {
            compose.setContent { TestPage { OnboardingFlow({ finished = true }, replay = true, viewModel = vm) } }
            capture("02-create-channel")
            compose.onNodeWithText("Enter Channel Name").performTextInput("Operations")
            compose.onNodeWithText("Continue").performClick()
            compose.onNodeWithText("Your team ready!").assertDoesNotExist()
            compose.onNodeWithText("Search Channel").assertDoesNotExist()
            compose.onNodeWithContentDescription("Back").assertDoesNotExist()
            capture("03-manage-channel")
            compose.onNodeWithContentDescription("Go to your team channel").performTouchInput { click() }
            capture("04-add-member")
            compose.onNodeWithContentDescription("Click to add new member").performTouchInput { click() }
            capture("05-pairing-empty")
            compose.onNodeWithText("Find nearby devices").performClick()
            capture("06-pairing-found")
            compose.onNodeWithText("ViKo Demo (replay)").performClick()
            compose.onNodeWithText("Enter Member Name").performTextInput("Le Khanh Linh")
            compose.onNodeWithText("Le Khanh Linh").performImeAction()
            compose.waitForIdle()
            capture("07-setup-member")
            compose.onNode(hasText("Save") and isEnabled()).performClick()
            compose.onNodeWithText("Le Khanh Linh").assertIsDisplayed()
            compose.onNodeWithText("Your team ready!").assertDoesNotExist()
            capture("08-members")
            compose.onNode(hasText("Save") and isEnabled()).performClick()
            compose.onNodeWithText("Your team ready!").assertIsDisplayed()
            capture("09-team-ready")
            compose.onNodeWithText("Go to Homepage").performClick()
            compose.runOnIdle { assertTrue(finished) }
        } finally { compose.runOnUiThread { store.clear() } }
    }

    @Test fun splashScreenshot() {
        compose.setContent { TestPage { SplashScreen() } }
        capture("01-splash")
    }

    @Test fun largeTextKeepsCreationAndReadyActionsReachable() {
        var ready by mutableStateOf(false)
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 2f)) {
                TestPage {
                    if (ready) TeamReadyOverlay({})
                    else FirstChannelScreen(FirstChannelUiState(name = "Operations"), {}, { ready = true })
                }
            }
        }
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Go to Homepage").performScrollTo().assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(600)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(instrumentation.targetContext.cacheDir, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}

@Composable
private fun TestPage(content: @Composable () -> Unit) {
    VikoTheme { Surface(Modifier.fillMaxSize(), color = VBackground, content = content) }
}
