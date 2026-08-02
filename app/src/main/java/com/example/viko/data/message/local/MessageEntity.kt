package com.example.viko.data.message.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.viko.domain.message.model.TranslationMessage

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val workerId: String,
    val workerName: String,
    val channelId: String,
    val originalText: String,
    val translatedText: String,
    val originalLang: String,
    val targetLang: String,
    val timestamp: String
)

fun MessageEntity.toDomain() = TranslationMessage(
    id = id,
    workerId = workerId,
    workerName = workerName,
    channelId = channelId,
    originalText = originalText,
    translatedText = translatedText,
    originalLang = originalLang,
    targetLang = targetLang,
    timestamp = timestamp
)

fun TranslationMessage.toEntity() = MessageEntity(
    id = id,
    workerId = workerId,
    workerName = workerName,
    channelId = channelId,
    originalText = originalText,
    translatedText = translatedText,
    originalLang = originalLang,
    targetLang = targetLang,
    timestamp = timestamp
)
