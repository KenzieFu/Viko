package com.example.viko.data.channel.repository

import com.example.viko.data.channel.local.ChannelDao
import com.example.viko.data.channel.local.toDomain
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.channel.repository.ChannelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChannelRepositoryImpl(private val dao: ChannelDao) : ChannelRepository {
    override fun observeChannels(): Flow<List<Channel>> =
        dao.observeChannels().map { list -> list.map { it.toDomain() } }

    override suspend fun getChannel(id: String): Channel? =
        dao.getChannel(id)?.toDomain()
}
