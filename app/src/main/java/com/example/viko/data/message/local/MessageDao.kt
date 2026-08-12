package com.example.viko.data.message.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY rowid DESC")
    fun observeMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE workerId = :workerId ORDER BY rowid DESC")
    fun observeMessagesFor(workerId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)
}
