package com.example.viko.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.viko.data.alert.local.AlertDao
import com.example.viko.data.alert.local.AlertEntity
import com.example.viko.data.channel.local.ChannelDao
import com.example.viko.data.channel.local.ChannelEntity
import com.example.viko.data.message.local.MessageDao
import com.example.viko.data.message.local.MessageEntity
import com.example.viko.data.team.local.WorkerDao
import com.example.viko.data.team.local.WorkerEntity

@Database(
    entities = [WorkerEntity::class, ChannelEntity::class, AlertEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workerDao(): WorkerDao
    abstract fun channelDao(): ChannelDao
    abstract fun alertDao(): AlertDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "viko.db"
                ).build().also { instance = it }
            }
    }
}
