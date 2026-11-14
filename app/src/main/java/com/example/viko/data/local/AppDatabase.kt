package com.example.viko.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workerDao(): WorkerDao
    abstract fun channelDao(): ChannelDao
    abstract fun alertDao(): AlertDao
    abstract fun messageDao(): MessageDao

    companion object {
        private val badgeTelemetryMigration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workers ADD COLUMN wifiRssiDbm INTEGER")
                // Old real-member rows may still hold a placeholder percentage.
                db.execSQL("UPDATE workers SET batteryLevel = -1 WHERE id GLOB 'WR-*'")
            }
        }

        /**
         * Built exactly once, by [com.example.viko.di.AppContainer] — nothing
         * else should call this.
         */
        fun build(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "viko.db"
        ).addMigrations(badgeTelemetryMigration).build()
    }
}
