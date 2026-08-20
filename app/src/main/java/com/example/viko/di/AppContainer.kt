package com.example.viko.di

import android.content.Context
import com.example.viko.data.ai.Speaker
import com.example.viko.data.ai.SpeechPipeline
import com.example.viko.data.ai.TagClassifier
import com.example.viko.data.ai.TranslationPipeline
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
    /**
     * On-device Vietnamese <-> Korean translation.
     *
     * Constructed eagerly because the constructor only resolves a directory path —
     * the ~1.8GB of ONNX sessions behind it are opened on first use, so a phone with
     * no models pushed (the default) never pays for one.
     *
     * Shared rather than per-screen: one set of graphs is all the device can afford,
     * and both directions run through the same weights.
     */
    val translationPipeline = TranslationPipeline(context)

    /**
     * Predicts the context tag that [translationPipeline] expects on its input.
     * Separate objects rather than one because they are pushed, and fail,
     * independently: a phone with the tagger but no translator should still tag.
     */
    val tagClassifier = TagClassifier(context)

    /**
     * Speech in, translated text out — the whole cascade, in the order the
     * reference defines. Holds the ASR models lazily, so a phone that only ever
     * hears badge Vietnamese never maps the Korean model's 451MB.
     */
    val speechPipeline = SpeechPipeline(context, tagClassifier, translationPipeline)

    /** Korean out of the loudspeaker. No model — Android's built-in engine. */
    val speaker = Speaker(context)

    val badgeRegistry = BadgeRegistry(
        server = BadgeControlServer(context),
        audio = badgeAudio,
        workers = workerRepository,
        channels = channelRepository,
        alerts = alertRepository,
        messages = messageRepository,
        speech = speechPipeline,
        speaker = speaker
    )


    val databaseSeeder = DatabaseSeeder(
        workerRepository = workerRepository,
        channelRepository = channelRepository,
        alertRepository = alertRepository,
        messageRepository = messageRepository
    )
}
