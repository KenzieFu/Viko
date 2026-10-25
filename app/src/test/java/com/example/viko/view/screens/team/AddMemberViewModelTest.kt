package com.example.viko.view.screens.team

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.settings.model.FactoryWifi
import com.example.viko.domain.settings.repository.FactoryWifiRepository
import com.example.viko.domain.team.model.BadgeSetup
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.awaitCancellation
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.DeviceScanner
import com.example.viko.domain.team.repository.WorkerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddMemberViewModelTest {
    @Test fun selectedRepositoryChannelProvisionsBleAndWaitsForWifiRegistration() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val workers = Workers()
            val channels = Channels()
            val scanner = Scanner()
            val vm = AddMemberViewModel(SavedStateHandle(), workers, channels, Wifi(), scanner)
            store.put("member", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
            runCurrent()

            vm.select(NearbyDevice("WR-AABBCCDD1234", "ViKo-1234", "AA:BB:CC:DD:12:34"))
            vm.onNameChange("Linh")
            vm.selectChannel("team")
            runCurrent()
            assertTrue(vm.uiState.value.canSave)

            vm.save().join()
            runCurrent()

            assertNotNull(scanner.checked)
            val member = workers.rows.value.single()
            assertEquals(scanner.setup?.deviceId, member.id)
            assertEquals("team", member.channelId)
            assertFalse(member.isOnline) // A BLE save is not a Wi-Fi registration.
            assertEquals("Linh", member.name)
            assertEquals("WR-1234", member.id)
            assertEquals("kenzie", scanner.setup?.wifiSsid)
            assertEquals(1, vm.uiState.value.addedCount)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun unreachableBadgeDoesNotAddMember() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val workers = Workers()
            val scanner = Scanner(Result.failure(IllegalStateException("Badge disconnected")))
            val vm = AddMemberViewModel(SavedStateHandle(), workers, Channels(), Wifi(), scanner)
            store.put("member", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
            runCurrent()
            vm.select(NearbyDevice("WR-AABBCCDD1234", "ViKo-1234", "AA:BB:CC:DD:12:34"))
            vm.onNameChange("Linh")
            vm.selectChannel("team")
            runCurrent()
            vm.save().join()
            runCurrent()
            assertNotNull(scanner.checked)
            assertTrue(workers.rows.value.isEmpty())
            assertEquals("Badge disconnected", vm.uiState.value.error)
            assertFalse(vm.uiState.value.isProvisioning)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun scanFailureStopsSpinnerAndExposesRetryMessage() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val scanner = Scanner().apply { scanFailure = "Bluetooth scan failed" }
            val vm = AddMemberViewModel(SavedStateHandle(), Workers(), Channels(), Wifi(), scanner)
            store.put("member", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
            vm.rescan()
            runCurrent()
            assertFalse(vm.uiState.value.isScanning)
            assertEquals("Bluetooth scan failed", vm.uiState.value.error)
            scanner.scanFailure = null
            vm.rescan()
            runCurrent()
            assertNull(vm.uiState.value.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun bluetoothOffDoesNotStartAThirtySecondScan() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val scanner = Scanner().apply { isBluetoothEnabled = false }
            val vm = AddMemberViewModel(SavedStateHandle(), Workers(), Channels(), Wifi(), scanner)
            store.put("member", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
            vm.rescan()
            runCurrent()
            assertEquals(0, scanner.scanStarts)
            assertFalse(vm.uiState.value.isScanning)
            assertFalse(vm.uiState.value.isBluetoothEnabled)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun restartingScanClosesOldScanBeforeNewOneAndSelectionStopsIt() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val scanner = Scanner().apply { keepScanning = true }
            val vm = AddMemberViewModel(SavedStateHandle(), Workers(), Channels(), Wifi(), scanner)
            store.put("member", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
            vm.rescan()
            runCurrent()
            vm.rescan()
            runCurrent()
            assertEquals(listOf("start", "stop", "start"), scanner.events)
            assertTrue(vm.uiState.value.isScanning)
            vm.select(NearbyDevice("badge", "ViKo", "AA:BB:CC:DD:12:34"))
            runCurrent()
            assertEquals(listOf("start", "stop", "start", "stop"), scanner.events)
            assertFalse(vm.uiState.value.isScanning)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    private class Wifi : FactoryWifiRepository {
        val wifi = MutableStateFlow(FactoryWifi("kenzie", ""))
        override fun observeFactoryWifi() = wifi
        override suspend fun saveFactoryWifi(wifi: FactoryWifi) { this.wifi.value = wifi }
    }

    private class Scanner(private val result: Result<Unit> = Result.success(Unit)) : DeviceScanner {
        var checked: NearbyDevice? = null
        var setup: BadgeSetup? = null
        var scanFailure: String? = null
        var scanStarts = 0
        var keepScanning = false
        val events = mutableListOf<String>()
        override var isBluetoothEnabled = true
        override fun scan() = flow {
            scanStarts++
            events += "start"
            try {
                scanFailure?.let { throw IllegalStateException(it) }
                emit(emptyList<NearbyDevice>())
                if (keepScanning) awaitCancellation()
            } finally { events += "stop" }
        }
        override suspend fun provision(device: NearbyDevice, setup: BadgeSetup): Result<Unit> {
            this.checked = device
            this.setup = setup
            return result
        }
    }

    private class Channels : ChannelRepository {
        private val rows = MutableStateFlow(listOf(Channel("team", "Test team")))
        override fun observeChannels() = rows
        override suspend fun getChannel(id: String) = rows.value.find { it.id == id }
        override suspend fun addChannel(channel: Channel) { rows.value += channel }
    }

    private class Workers : WorkerRepository {
        val rows = MutableStateFlow(emptyList<Worker>())
        override fun observeWorkers() = rows
        override fun observeWorker(id: String) = rows.map { list -> list.find { it.id == id } }
        override suspend fun getWorker(id: String) = rows.value.find { it.id == id }
        override suspend fun addWorker(worker: Worker) { rows.value += worker }
        override suspend fun updateWorker(worker: Worker) {
            rows.value = rows.value.map { if (it.id == worker.id) worker else it }
        }
        override suspend fun removeWorker(id: String) { rows.value = rows.value.filterNot { it.id == id } }
        override suspend fun assignToChannel(workerId: String, channelId: String) = Unit
        override suspend fun renameWorker(workerId: String, name: String) = Unit
    }
}
