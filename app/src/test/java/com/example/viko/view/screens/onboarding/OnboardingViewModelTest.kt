package com.example.viko.view.screens.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @Test fun readyWaitsForMemberAndExplicitRosterSave() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val channels = Channels(); val workers = Workers()
            val vm = OnboardingViewModel(SavedStateHandle(), channels, workers)
            store.put("onboarding", vm)
            vm.nameChanged(" Operations "); vm.createChannel(); runCurrent()
            assertEquals(OnboardingStage.ManageChannel, vm.uiState.value.stage)
            assertEquals("Operations", channels.rows.value.single().name)
            vm.openMembers(); vm.saveRoster(); runCurrent()
            assertEquals(OnboardingStage.Members, vm.uiState.value.stage)
            workers.addWorker(Worker("badge", "Linh", channelId = channels.rows.value.single().id,
                zone = "Site", deviceAddress = "BLE", macAddress = "", batteryLevel = 0, isOnline = false))
            runCurrent()
            assertEquals(OnboardingStage.Members, vm.uiState.value.stage)
            vm.saveRoster(); runCurrent()
            assertEquals(OnboardingStage.TeamReady, vm.uiState.value.stage)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun replayCreateAddRemoveAndRestoreNeverWriteToRepositories() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val channels = Channels(); val workers = Workers(); val saved = SavedStateHandle()
            val vm = OnboardingViewModel(saved, channels, workers); store.put("first", vm)
            vm.setReplay(true); vm.nameChanged("Demo team"); vm.createChannel(); runCurrent()
            vm.openMembers(); vm.addDemoMember("Linh"); runCurrent()
            val restored = OnboardingViewModel(saved, channels, workers); store.put("restored", restored)
            runCurrent()
            assertEquals("Linh", restored.uiState.value.members.single().name)
            assertEquals(OnboardingStage.Members, restored.uiState.value.stage)
            restored.removeMember(restored.uiState.value.members.single().id); runCurrent()
            restored.saveRoster(); runCurrent()
            assertEquals(OnboardingStage.Members, restored.uiState.value.stage)
            restored.addDemoMember("Nam"); runCurrent(); restored.saveRoster(); runCurrent()
            assertEquals(OnboardingStage.TeamReady, restored.uiState.value.stage)
            assertTrue(channels.rows.value.isEmpty()); assertEquals(0, workers.writes)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedChannelCreationCanBeRetriedWithoutAdvancing() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val channels = Channels().apply { fail = true }
            val vm = OnboardingViewModel(SavedStateHandle(), channels, Workers()); store.put("vm", vm)
            vm.nameChanged("Operations"); vm.createChannel(); runCurrent()
            assertEquals(OnboardingStage.CreateChannel, vm.uiState.value.stage)
            assertNotNull(vm.uiState.value.error); assertFalse(vm.uiState.value.saving)
            channels.fail = false; vm.createChannel(); runCurrent()
            assertEquals(1, channels.rows.value.size)
            assertEquals(OnboardingStage.ManageChannel, vm.uiState.value.stage)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    private class Channels : ChannelRepository {
        val rows = MutableStateFlow(emptyList<Channel>()); var fail = false
        override fun observeChannels() = rows
        override suspend fun getChannel(id: String) = rows.value.find { it.id == id }
        override suspend fun addChannel(channel: Channel) { if (fail) error("Database unavailable"); rows.value += channel }
    }
    private class Workers : WorkerRepository {
        val rows = MutableStateFlow(emptyList<Worker>()); var writes = 0
        override fun observeWorkers() = rows
        override fun observeWorker(id: String) = rows.map { list -> list.find { it.id == id } }
        override suspend fun getWorker(id: String) = rows.value.find { it.id == id }
        override suspend fun addWorker(worker: Worker) { writes++; rows.value += worker }
        override suspend fun updateWorker(worker: Worker) { writes++ }
        override suspend fun removeWorker(id: String) { writes++; rows.value = rows.value.filterNot { it.id == id } }
        override suspend fun assignToChannel(workerId: String, channelId: String) { writes++ }
        override suspend fun renameWorker(workerId: String, name: String) { writes++ }
    }
}
