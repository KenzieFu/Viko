package com.example.viko.view.screens.team

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
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
    @Test fun selectedRepositoryChannelAllowsLocalWifiEnrollment() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val workers = Workers()
            val channels = Channels()
            val scanner = Scanner()
            val vm = AddMemberViewModel(SavedStateHandle(), workers, channels, scanner)
            store.put("member", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
            runCurrent()

            vm.select(NearbyDevice("WR-AABBCCDD1234", "ViKo-1234", "192.168.1.12"))
            vm.onNameChange("Linh")
            vm.selectChannel("team")
            runCurrent()
            assertTrue(vm.uiState.value.canSave)

            vm.save().join()
            runCurrent()

            assertNotNull(scanner.checked)
            val member = workers.rows.value.single()
            assertEquals(scanner.checked?.id, member.id)
            assertEquals("team", member.channelId)
            assertTrue(member.isOnline) // A recent Wi-Fi registration is required.
            assertEquals("Linh", member.name)
            assertEquals("WR-AABBCCDD1234", member.id)
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
            val vm = AddMemberViewModel(SavedStateHandle(), workers, Channels(), scanner)
            store.put("member", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
            runCurrent()
            vm.select(NearbyDevice("WR-AABBCCDD1234", "ViKo-1234", "192.168.1.12"))
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

    @Test fun reenrollmentAfterDhcpChangeDoesNotOverwriteMember() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val workers = Workers()
            val existing = Worker("WR-001122334455", "Linh", channelId = "team",
                zone = "Line 2", deviceAddress = "192.168.1.10", macAddress = "",
                batteryLevel = 100, isOnline = true)
            workers.addWorker(existing)
            val scanner = Scanner()
            val vm = AddMemberViewModel(SavedStateHandle(), workers, Channels(), scanner)
            store.put("member", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
            val moved = NearbyDevice(existing.id, "ViKo", "192.168.1.11")
            runCurrent()
            assertTrue(vm.uiState.value.isEnrolled(moved))
            vm.select(moved)
            vm.onNameChange("Accidental duplicate")
            vm.selectChannel("team")
            runCurrent()
            vm.save().join()
            runCurrent()
            assertEquals(listOf(existing), workers.rows.value)
            assertNull(scanner.checked)
            assertFalse(vm.uiState.value.isProvisioning)
            assertNotNull(vm.uiState.value.error)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private class Scanner(private val result: Result<Unit> = Result.success(Unit)) : DeviceScanner {
        var checked: NearbyDevice? = null
        override val isNetworkAvailable = true
        override fun scan() = flowOf(emptyList<NearbyDevice>())
        override suspend fun confirmAvailable(device: NearbyDevice): Result<Unit> {
            this.checked = device
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
