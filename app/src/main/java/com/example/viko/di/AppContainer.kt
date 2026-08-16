package com.example.viko.di

import android.content.Context
import com.example.viko.data.alert.repository.AlertRepositoryImpl
import com.example.viko.data.channel.repository.ChannelRepositoryImpl
import com.example.viko.data.local.AppDatabase
import com.example.viko.data.local.DatabaseSeeder
import com.example.viko.data.message.repository.MessageRepositoryImpl
import com.example.viko.data.network.BadgeAudioServer
import com.example.viko.data.network.BadgeControlServer
import com.example.viko.data.network.BadgeRegistry
import com.example.viko.data.team.repository.WorkerRepositoryImpl
import com.example.viko.data.team.scanner.BleDeviceScanner
import com.example.viko.domain.alert.repository.AlertRepository
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.message.repository.MessageRepository
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
     * Badge discovery and BLE provisioning. Held as [BleDeviceScanner] rather
     * than [DeviceScanner] because the pairing sheet also asks it whether
     * Bluetooth and its permissions are actually available before scanning.
     */
    val deviceScanner: BleDeviceScanner = BleDeviceScanner(context)

    /**
     * Live speech to and from badges (UDP 5002). Unlike the control channel this
     * one *is* touched by a screen — Direct Call pushes the phone mic through it
     * — so it is exposed rather than hidden behind [badgeRegistry].
     */
    val badgeAudio: BadgeAudioServer = BadgeAudioServer(context)

    /**
     * The badge control channel (TCP 5001 + mDNS). Started by
     * [com.example.viko.VikoApplication]; it writes straight into the
     * repositories above, so no screen depends on it directly.
     */
    val badgeRegistry = BadgeRegistry(
        server = BadgeControlServer(context),
        audio = badgeAudio,
        workers = workerRepository,
        channels = channelRepository,
        alerts = alertRepository
    )

    val databaseSeeder = DatabaseSeeder(
        workerRepository = workerRepository,
        channelRepository = channelRepository,
        alertRepository = alertRepository,
        messageRepository = messageRepository
    )
}
