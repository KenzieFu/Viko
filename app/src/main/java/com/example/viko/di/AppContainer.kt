package com.example.viko.di

import android.content.Context
import com.example.viko.data.alert.repository.AlertRepositoryImpl
import com.example.viko.data.channel.repository.ChannelRepositoryImpl
import com.example.viko.data.local.AppDatabase
import com.example.viko.data.local.DatabaseSeeder
import com.example.viko.data.message.repository.MessageRepositoryImpl
import com.example.viko.data.team.repository.WorkerRepositoryImpl
import com.example.viko.data.team.scanner.SimulatedDeviceScanner
import com.example.viko.domain.alert.repository.AlertRepository
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.message.repository.MessageRepository
import com.example.viko.domain.team.repository.DeviceScanner
import com.example.viko.domain.team.repository.WorkerRepository

/**
 * Manual DI container: builds the Room database once and hands out repository
 * singletons. Swap for Hilt later if the dependency graph outgrows this.
 *
 * Created once by [com.example.viko.VikoApplication].
 */
class AppContainer(context: Context) {

    private val database = AppDatabase.build(context)

    val workerRepository: WorkerRepository = WorkerRepositoryImpl(database.workerDao())
    val channelRepository: ChannelRepository = ChannelRepositoryImpl(database.channelDao())
    val alertRepository: AlertRepository = AlertRepositoryImpl(database.alertDao())
    val messageRepository: MessageRepository = MessageRepositoryImpl(database.messageDao())

    /**
     * Badge discovery. Simulated until the BLE transport lands — swapping in the
     * real scanner is a one-line change here, because every caller depends on
     * the [DeviceScanner] interface rather than this implementation.
     */
    val deviceScanner: DeviceScanner = SimulatedDeviceScanner()

    val databaseSeeder = DatabaseSeeder(
        workerRepository = workerRepository,
        channelRepository = channelRepository,
        alertRepository = alertRepository,
        messageRepository = messageRepository
    )
}
