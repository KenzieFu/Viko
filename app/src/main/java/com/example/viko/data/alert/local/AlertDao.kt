package com.example.viko.data.alert.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.viko.type.AlertStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts")
    fun observeAlerts(): Flow<List<AlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: AlertEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alerts: List<AlertEntity>)

    @Query("UPDATE alerts SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: AlertStatus)

    @Query("UPDATE alerts SET status = :resolved WHERE status = :active")
    suspend fun updateAllStatus(active: AlertStatus, resolved: AlertStatus)
}
