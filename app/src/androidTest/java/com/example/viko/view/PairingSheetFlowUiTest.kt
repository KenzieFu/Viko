package com.example.viko.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viko.components.VikoDragHandle
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.settings.model.FactoryWifi
import com.example.viko.domain.settings.repository.FactoryWifiRepository
import com.example.viko.domain.team.model.*
import com.example.viko.domain.team.repository.*
import com.example.viko.view.screens.team.*
import com.example.viko.view.screens.channel.SelectChannelSheetContent
import com.example.viko.view.screens.channel.AddNewChannelSheetContent
import com.example.viko.view.theme.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class PairingSheetFlowUiTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val badge = NearbyDevice("test-badge", "Device 1", "AA:BB:CC:11:22:33")

    @After fun cleanup() { InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() } }

    @Test fun discoverySheetLeavesSpaceAboveAndKeepsSearchReachable() {
        var found by mutableStateOf(false)
        compose.setContent {
            VikoTheme {
                Box(Modifier.fillMaxSize().background(VBackground)) {
                    ModalBottomSheet(onDismissRequest = {},
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = VBackground, dragHandle = { VikoDragHandle() }) {
                        NearbyDeviceSheetContent(
                            devices = if (found) (1..4).map { badge.copy(id = "$it", name = "Device $it") } else emptyList(),
                            isScanning = false, hasPermission = true, isBluetoothEnabled = true,
                            isEnrolled = { false }, addedCount = 0, onSelect = {}, onRescan = { found = true }, onDone = {}, onClose = {}
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("No devices found").assertIsDisplayed()
        compose.onNodeWithText("Find nearby devices").assertIsDisplayed()
        val content = compose.onNodeWithTag("nearby-device-sheet-content").fetchSemanticsNode().boundsInRoot
        assertTrue("Sheet must leave room above it", content.top > 100f)
        capture("pairing-empty.png")
        compose.onNodeWithText("Find nearby devices").performClick()
        compose.onNodeWithText("Device 4").assertIsDisplayed()
        capture("pairing-found.png")
    }

    @Test fun firstChannelOpensCreationDirectlyAndPreservesMemberDraft() {
        val channels = Channels(emptyList())
        val vm = setup(channels)
        compose.onNodeWithText("Select Channel").performClick()
        compose.onNodeWithText("Add New Channel").assertIsDisplayed()
        compose.onNodeWithText("Search Channel").assertDoesNotExist()
        capture("first-channel-popup.png")
        compose.onNodeWithText("Enter Channel Name").performTextInput("Operations")
        compose.onNodeWithText("Continue").performClick()
        compose.waitUntil(5_000) { vm.uiState.value.selectedChannelName == "Operations" }
        compose.onNodeWithText("Operations").assertIsDisplayed()
        compose.onNodeWithText("Linh").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsEnabled()
        capture("first-channel-selected.png")
        assertEquals(1, channels.rows.value.size)
    }

    @Test fun existingChannelsStillOpenThePicker() {
        val vm = setup(Channels(listOf(Channel("c1", "Operations"), Channel("c2", "Maintenance"),
            Channel("c3", "Store"), Channel("c4", "Facilities"))))
        compose.onNodeWithText("Select Channel").performClick()
        compose.onNodeWithText("Search Channel").assertIsDisplayed()
        compose.onNodeWithText("Add New Channel").assertDoesNotExist()
        capture("channel-populated.png")
        compose.onNodeWithText("Operations").performClick()
        compose.waitUntil(5_000) { vm.uiState.value.selectedChannelId == "c1" }
        compose.onNodeWithText("Linh").assertIsDisplayed()
        compose.onNodeWithText("Operations").performClick()
        compose.onNodeWithContentDescription("Selected").assertIsDisplayed()
        capture("channel-selected.png")
        compose.onNodeWithText("Search Channel").performTextInput("Maintenance")
        compose.onNodeWithText("Facilities").assertDoesNotExist()
        compose.onNode(hasText("Maintenance") and !hasSetTextAction()).performClick()
        compose.waitUntil(5_000) { vm.uiState.value.selectedChannelId == "c2" }
    }

    @Test fun emptyPickerCanCreateOrCancelWithoutLosingItsPlace() {
        var adding by mutableStateOf(false)
        var created = ""
        compose.setContent {
            VikoTheme {
                Box(Modifier.fillMaxSize().background(VBackground)) {
                    ModalBottomSheet(onDismissRequest = {},
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = VBackground, dragHandle = { VikoDragHandle() }) {
                        SelectChannelSheetContent(emptyList(), "", {}, { adding = true }, {})
                    }
                    if (adding) ModalBottomSheet(onDismissRequest = { adding = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = VBackground, dragHandle = null) {
                        AddNewChannelSheetContent(onCreate = { created = it; adding = false },
                            onClose = { adding = false })
                    }
                }
            }
        }
        compose.onNodeWithText("No channel yet").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("select-channel-sheet").fetchSemanticsNode().boundsInRoot.top > 100f)
        capture("channel-empty.png")
        compose.onNodeWithText("Add Channel").performClick()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        capture("channel-create.png")
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Search Channel").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add Channel").performClick()
        compose.onNodeWithText("Enter Channel Name").performTextInput("  Warehouse  ")
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals("Warehouse", created) }
    }

    private fun setup(channels: Channels): AddMemberViewModel {
        lateinit var vm: AddMemberViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = AddMemberViewModel(SavedStateHandle(), Workers(), channels, Wifi(), Scanner())
            store.put("member", vm)
            vm.select(badge)
            vm.onNameChange("Linh")
            vm.onZoneChange("Central Building")
        }
        compose.setContent { VikoTheme { AddMemberFlow(onDismiss = {}, viewModel = vm) } }
        compose.onNodeWithText("Linh").assertIsDisplayed()
        return vm
    }

    private class Channels(initial: List<Channel>) : ChannelRepository {
        val rows = MutableStateFlow(initial)
        override fun observeChannels() = rows
        override suspend fun getChannel(id: String) = rows.value.find { it.id == id }
        override suspend fun addChannel(channel: Channel) { rows.value += channel }
    }
    private class Wifi : FactoryWifiRepository {
        override fun observeFactoryWifi() = flowOf(FactoryWifi("Test Wi-Fi", ""))
        override suspend fun saveFactoryWifi(wifi: FactoryWifi) = Unit
    }
    private class Scanner : DeviceScanner {
        override val isBluetoothEnabled = true
        override fun scan() = flowOf(emptyList<NearbyDevice>())
        override suspend fun provision(device: NearbyDevice, setup: BadgeSetup) = Result.success(Unit)
    }
    private class Workers : WorkerRepository {
        override fun observeWorkers() = flowOf(emptyList<Worker>())
        override fun observeWorker(id: String) = flowOf<Worker?>(null)
        override suspend fun getWorker(id: String): Worker? = null
        override suspend fun addWorker(worker: Worker) = Unit
        override suspend fun updateWorker(worker: Worker) = Unit
        override suspend fun removeWorker(id: String) = Unit
        override suspend fun assignToChannel(workerId: String, channelId: String) = Unit
        override suspend fun renameWorker(workerId: String, name: String) = Unit
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
