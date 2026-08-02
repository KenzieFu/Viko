package com.example.viko.data.channel.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.viko.domain.channel.model.Channel

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String
)

fun ChannelEntity.toDomain() = Channel(id = id, name = name, description = description)

fun Channel.toEntity() = ChannelEntity(id = id, name = name, description = description)
