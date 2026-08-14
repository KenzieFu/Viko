package com.example.viko.data.local

import com.example.viko.domain.alert.repository.AlertRepository
import com.example.viko.domain.channel.repository.ChannelRepository
import com.example.viko.domain.message.repository.MessageRepository
import com.example.viko.domain.team.repository.WorkerRepository
import kotlinx.coroutines.flow.first

/**
 * Fills an empty database with [SeedData] on first launch. Runs through the
 * repositories rather than the DAOs so the seed stays honest about what the
 * app itself can write.
 */
class DatabaseSeeder(
    private val workerRepository: WorkerRepository,
    private val channelRepository: ChannelRepository,
    private val alertRepository: AlertRepository,
    private val messageRepository: MessageRepository
) {
    suspend fun seedIfEmpty() {
        if (channelRepository.observeChannels().first().isNotEmpty()) return

        SeedData.channels.forEach { channelRepository.addChannel(it) }
        SeedData.workers.forEach { workerRepository.addWorker(it) }
        SeedData.alerts.forEach { alertRepository.addAlert(it) }
        // Messages come back newest-first (ORDER BY rowid DESC), so insert the
        // newest-first seed list back-to-front to preserve its order.
        SeedData.messages.asReversed().forEach { messageRepository.addMessage(it) }
    }
}
