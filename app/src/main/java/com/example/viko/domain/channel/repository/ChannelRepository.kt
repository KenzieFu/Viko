package com.example.viko.domain.channel.repository

import com.example.viko.domain.channel.model.Channel
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    fun observeChannels(): Flow<List<Channel>>
    suspend fun getChannel(id: String): Channel?
}
