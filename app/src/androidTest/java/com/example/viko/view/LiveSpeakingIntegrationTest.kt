package com.example.viko.view

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viko.VikoApplication
import com.example.viko.data.ai.onevoice.OneVoicePipeline
import com.example.viko.data.network.BadgeAudioServer
import com.example.viko.data.network.TranslationStatus
import com.example.viko.domain.channel.model.Channel as TeamChannel
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import com.example.viko.navigation.NavArgs
import com.example.viko.type.WorkerStatus
import com.example.viko.view.screens.directcall.DirectCallViewModel
import com.example.viko.view.screens.home.HomeViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.UUID

/** Real UDP/TCP -> registry -> Room tests. Never add test members to a real phone. */
class LiveSpeakingIntegrationTest {
    @Test fun sendsFirstAudioBeforeTheRestOfTheReplyIsGenerated() = runBlocking {
        withTestMember { app, id ->
            val audio = app.container.badgeAudio
            audio.rememberBadge(id, InetAddress.getLoopbackAddress())
            withTimeout(3_000) {
                while (id !in audio.translationWorkerIds) delay(10)
            }
            val prior = audio.translationCapture
            val firstFrame = CompletableDeferred<Unit>()
            val received = CompletableDeferred<ByteArray>()
            val start = ByteArray(960) { 1 }
            val end = ByteArray(960) { 2 }
            audio.translationCapture = BadgeAudioServer.TranslationCapture(
                isEnabled = { true },
                worker = { device, pcm, _ -> if (device == id) received.complete(pcm) },
                manager = { _, _, _ -> }
            )
            try {
                assertTrue(withTimeout(3_000) {
                    audio.sendPcm(id, flow {
                        emit(start)
                        firstFrame.await()
                        emit(end)
                    }, firstFrame = { firstFrame.complete(Unit) })
                })
                assertArrayEquals(start + end, withTimeout(3_000) { received.await() })
            } finally {
                audio.translationCapture = prior
            }
        }
    }

    @Test fun homeMicStreamsLiveToOnlineMembersOfTheSelectedChannel() = runBlocking<Unit> {
        assumeTrue("Emulator only", Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as VikoApplication
        val container = app.container
        val audio = container.badgeAudio
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, android.Manifest.permission.RECORD_AUDIO)
        val team = TeamChannel("channel-test", "Operations")
        val other = TeamChannel("other-test", "Facilities")
        fun worker(channelId: String, online: Boolean) = Worker(
            id = "talk-test-${UUID.randomUUID()}", name = "Test badge", channelId = channelId,
            zone = "", deviceAddress = "127.0.0.1", macAddress = "", batteryLevel = 70, isOnline = online
        )
        val teamMembers = listOf(worker(team.id, true), worker(team.id, true))
        val excluded = listOf(worker(team.id, false), worker(other.id, true))
        val workers = teamMembers + excluded
        workers.forEach { audio.rememberBadge(it.id, InetAddress.getLoopbackAddress()) }

        val store = ViewModelStore()
        val model = withContext(Dispatchers.Main) {
            ViewModelProvider.create(store, viewModelFactory {
                initializer {
                    HomeViewModel(
                        object : WorkerRepository by container.workerRepository {
                            override fun observeWorkers() = flowOf(workers)
                        },
                        object : ChannelRepository by container.channelRepository {
                            override fun observeChannels() = flowOf(listOf(team, other))
                        },
                        container.alertRepository, audio
                    )
                }
            })[HomeViewModel::class]
        }
        try {
            withTimeout(3_000) { model.uiState.first { it.isLoaded } }
            withContext(Dispatchers.Main) { model.toggleChannelTalk(team.id) }
            val result = withTimeout(3_000) {
                model.uiState.first { it.talkingChannelId != null || it.talkError != null }
            }
            assertEquals(result.talkError, team.id, result.talkingChannelId)
            val heard = withTimeout(5_000) {
                audio.activeBadges.first { ids -> teamMembers.all { it.id in ids } }
            }
            assertTrue(excluded.none { it.id in heard })

            withContext(Dispatchers.Main) { model.toggleChannelTalk(team.id) }
            withTimeout(3_000) { model.uiState.first { it.talkingChannelId == null } }
            assertFalse(audio.isTalking.value)
            withTimeout(3_000) { audio.activeBadges.first { ids -> workers.none { it.id in ids } } }
        } finally {
            withContext(Dispatchers.Main) {
                model.stopChannelTalk()
                store.clear()
            }
        }
    }

    @Test fun directCallUpdatesAfterBothTranslatedTurns() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("requireDirectCallTranslation") == "true")
        withTestMember { app, id ->
            val container = app.container
            container.translationPreferences.setEnabled(true)
            withTimeout(12_000) {
                container.badgeRegistry.translationStatus.first { it == TranslationStatus.ACTIVE }
            }
            val fixtures = File(OneVoicePipeline.directory(app).parentFile, "onevoice-test")
            val audio = container.badgeAudio
            val capture = checkNotNull(audio.translationCapture)
            val store = ViewModelStore()
            val model = withContext(Dispatchers.Main) {
                ViewModelProvider.create(store, viewModelFactory {
                    initializer {
                        DirectCallViewModel(
                            SavedStateHandle(mapOf(NavArgs.WORKER_ID to id)),
                            container.workerRepository, container.messageRepository,
                            container.badgeRegistry, container.badgeAudio
                        )
                    }
                })[DirectCallViewModel::class]
            }
            try {
                withTimeout(3_000) { model.uiState.first { it.worker?.id == id } }
                val pending = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(5_000) { model.uiState.first { it.isTranslating } }
                }
                capture.worker(id, File(fixtures, "vi_sample.pcm").readBytes(), android.os.SystemClock.elapsedRealtime())
                pending.await()
                val workerTurn = withTimeout(25_000) {
                    model.uiState.first { it.translation != null && !it.isTranslating }
                }
                assertFalse(workerTurn.translationFromManager)
                // Loop the outgoing Vietnamese packets back through the UDP receiver.
                // Keep the reply from being interpreted as another worker turn.
                audio.rememberBadge(id, InetAddress.getLoopbackAddress())
                audio.translationCapture = capture.copy(worker = { _, _, _ -> })
                capture.manager(id, File(fixtures, "ko_sample.pcm").readBytes(), android.os.SystemClock.elapsedRealtime())
                val managerTurn = withTimeout(25_000) {
                    model.uiState.first { it.translation != null && it.translationFromManager && !it.isTranslating }
                }
                assertNotEquals(workerTurn.translationMessageId, managerTurn.translationMessageId)
                withTimeout(15_000) { audio.activeBadges.first { id in it } }
                withTimeout(12_000) { audio.activeBadges.first { id !in it } }
            } finally {
                audio.translationCapture = capture
                withContext(Dispatchers.Main) { store.clear() }
            }
        }
    }

    @Test fun translationCapturesBothTurnsWhenTcpIsLateOrMissing() = runBlocking {
        withTestMember { app, id ->
            val audio = app.container.badgeAudio
            withTimeout(3_000) {
                while (id !in audio.translationWorkerIds) delay(10)
            }
            val turns = Channel<ByteArray>(2)
            val priorCapture = audio.translationCapture
            audio.translationCapture = BadgeAudioServer.TranslationCapture(
                isEnabled = { true },
                worker = { deviceId, pcm, _ -> if (deviceId == id) turns.trySend(pcm) },
                manager = { _, _, _ -> }
            )
            try {
                withContext(Dispatchers.IO) {
                    DatagramSocket().use { socket ->
                        for (sequence in 0L..3L) {
                            socket.sendFrame(id, sequence, 1)
                            delay(20)
                        }
                        control(id, "ptt_start")
                        control(id, "ptt_end")
                        for (sequence in 4L..7L) {
                            socket.sendFrame(id, sequence, 1)
                            delay(20)
                        }
                        // A new press resets the UDP sequence before the idle window.
                        for (sequence in 0L..7L) {
                            socket.sendFrame(id, sequence, 2)
                            delay(20)
                        }
                    }
                }
                assertArrayEquals(ByteArray(8 * 640) { 1 }, withTimeout(2_000) { turns.receive() })
                assertArrayEquals(ByteArray(8 * 640) { 2 }, withTimeout(2_000) { turns.receive() })
                assertTrue(turns.tryReceive().isFailure)
            } finally {
                audio.translationCapture = priorCapture
                turns.close()
            }
        }
    }

    @Test fun udpAloneStartsAndStopsSpeakingAndLateStartDoesNotReopenIt() = runBlocking {
        withTestMember { app, id ->
            val sender = stream(id)
            try {
                withTimeout(5_000) { app.container.badgeAudio.activeBadges.first { id in it } }
                withTimeout(700) {
                    app.container.workerRepository.observeWorker(id).first { it?.status == WorkerStatus.SPEAKING }
                }
            } finally { sender.cancelAndJoin() }
            withTimeout(1_300) {
                app.container.workerRepository.observeWorker(id).first { it?.status == WorkerStatus.AVAILABLE }
            }
            // A register sent after the old TCP start is a processing barrier:
            // the serial TCP receiver applies the start before this register.
            control(id, "ptt_start")
            register(id, "after-late-start")
            val current = withTimeout(3_000) {
                app.container.workerRepository.observeWorker(id).first { it?.firmwareVersion == "after-late-start" }
            }
            assertEquals(WorkerStatus.AVAILABLE, current!!.status)
        }
    }

    @Test fun delayedTcpEndCannotClearAnActiveUdpStream() = runBlocking {
        withTestMember { app, id ->
            val sender = stream(id)
            try {
                withTimeout(5_000) {
                    app.container.workerRepository.observeWorker(id).first { it?.status == WorkerStatus.SPEAKING }
                }
                control(id, "ptt_end")
                register(id, "after-late-end")
                val current = withTimeout(3_000) {
                    app.container.workerRepository.observeWorker(id).first { it?.firmwareVersion == "after-late-end" }
                }
                assertEquals(WorkerStatus.SPEAKING, current!!.status)
                delay(250)
                assertEquals(WorkerStatus.SPEAKING, app.container.workerRepository.getWorker(id)!!.status)
            } finally { sender.cancelAndJoin() }
        }
    }

    private suspend fun CoroutineScope.withTestMember(block: suspend CoroutineScope.(VikoApplication, String) -> Unit) {
        assumeTrue("Emulator only", Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as VikoApplication
        val id = "status-test-${UUID.randomUUID()}"
        val priorTranslation = app.container.translationPreferences.enabled.value
        app.container.translationPreferences.setEnabled(false)
        try {
            withTimeout(3_000) { app.container.badgeRegistry.translationStatus.first { it == TranslationStatus.OFF } }
            app.container.workerRepository.addWorker(Worker(id = id, name = "Status test", channelId = "",
                zone = "", deviceAddress = "127.0.0.1", macAddress = "", batteryLevel = 70, isOnline = true))
            block(app, id)
        } finally {
            app.container.workerRepository.removeWorker(id)
            app.container.translationPreferences.setEnabled(priorTranslation)
        }
    }

    private fun CoroutineScope.stream(id: String) = launch(Dispatchers.IO) {
        DatagramSocket().use { socket ->
            var sequence = 0L
            while (isActive) {
                socket.sendFrame(id, sequence++, 0)
                delay(20)
            }
        }
    }

    private fun DatagramSocket.sendFrame(id: String, sequence: Long, value: Byte) {
        val header = JSONObject().put("type", "audio").put("device_id", id)
            .put("audio_format", "pcm16").put("sequence", sequence)
            .toString().toByteArray()
        val frame = ByteBuffer.allocate(2 + header.size + 640)
            .putShort(header.size.toShort()).put(header).put(ByteArray(640) { value }).array()
        send(DatagramPacket(frame, frame.size, InetAddress.getLoopbackAddress(), 5002))
    }

    private suspend fun control(id: String, event: String) = sendTcp(JSONObject()
        .put("type", "control").put("device_id", id).put("event", event))

    private suspend fun register(id: String, version: String) = sendTcp(JSONObject()
        .put("type", "device_register").put("device_id", id).put("firmware_version", version))

    private suspend fun sendTcp(json: JSONObject) = withContext(Dispatchers.IO) {
        Socket(InetAddress.getLoopbackAddress(), 5001).use { socket ->
            socket.getOutputStream().write((json.toString() + "\n").toByteArray())
        }
    }
}
