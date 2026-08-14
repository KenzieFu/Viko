package com.example.viko.data.message.repository

import com.example.viko.data.message.local.MessageDao
import com.example.viko.data.message.local.toDomain
import com.example.viko.data.message.local.toEntity
import com.example.viko.domain.message.model.TranslationMessage
import com.example.viko.domain.message.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MessageRepositoryImpl(private val dao: MessageDao) : MessageRepository {

    override fun observeMessages(): Flow<List<TranslationMessage>> =
        dao.observeMessages().map { list -> list.map { it.toDomain() } }

    override fun observeMessagesFor(workerId: String): Flow<List<TranslationMessage>> =
        dao.observeMessagesFor(workerId).map { list -> list.map { it.toDomain() } }

    override suspend fun addMessage(message: TranslationMessage) = dao.insert(message.toEntity())
}
