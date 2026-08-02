package com.example.viko.di

import android.content.Context
import com.example.viko.data.alert.repository.AlertRepositoryImpl
import com.example.viko.data.channel.repository.ChannelRepositoryImpl
import com.example.viko.data.local.AppDatabase
import com.example.viko.data.message.repository.MessageRepositoryImpl
import com.example.viko.data.team.repository.WorkerRepositoryImpl
import com.example.viko.domain.alert.repository.AlertRepository
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.message.repository.MessageRepository
import com.example.viko.domain.team.repository.WorkerRepository

/**
 * Manual DI container: builds the Room database once and hands out repository
 * singletons. Swap for Hilt later if the dependency graph outgrows this.
 */
class AppContainer(context: Context) {
    private val database = AppDatabase.get(context)

    val workerRepository: WorkerRepository = WorkerRepositoryImpl(database.workerDao())
    val channelRepository: ChannelRepository = ChannelRepositoryImpl(database.channelDao())
    val alertRepository: AlertRepository = AlertRepositoryImpl(database.alertDao())
    val messageRepository: MessageRepository = MessageRepositoryImpl(database.messageDao())
}
