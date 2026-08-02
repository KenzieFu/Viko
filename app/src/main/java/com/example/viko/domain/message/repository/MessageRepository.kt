package com.example.viko.domain.message.repository

import com.example.viko.domain.message.model.TranslationMessage
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeMessages(): Flow<List<TranslationMessage>>
    suspend fun addMessage(message: TranslationMessage)
}
