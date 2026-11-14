package com.example.viko.data.team.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkerDao {
    @Query("SELECT * FROM workers")
    fun observeWorkers(): Flow<List<WorkerEntity>>

    @Query("SELECT * FROM workers WHERE id = :id")
    fun observeWorker(id: String): Flow<WorkerEntity?>

    @Query("SELECT * FROM workers WHERE id = :id")
    suspend fun getWorker(id: String): WorkerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(worker: WorkerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(workers: List<WorkerEntity>)

    @Update
    suspend fun update(worker: WorkerEntity)

    @Query("""
        UPDATE workers
        SET deviceAddress = :address, macAddress = :mac, firmwareVersion = :version,
            isOnline = 1, isPaired = 1, batteryLevel = :battery, wifiRssiDbm = :rssi
        WHERE id = :id AND (deviceAddress != :address OR macAddress != :mac
            OR firmwareVersion != :version OR isOnline != 1 OR isPaired != 1
            OR batteryLevel != :battery OR wifiRssiDbm IS NOT :rssi)
    """)
    suspend fun registerBadge(id: String, address: String, mac: String, version: String, battery: Int, rssi: Int?)

    @Query("""
        UPDATE workers SET batteryLevel = :battery, wifiRssiDbm = :rssi, isOnline = 1 WHERE id = :id
    """)
    suspend fun updateBadgeTelemetry(id: String, battery: Int, rssi: Int?)

    @Query("""
        UPDATE workers SET batteryLevel = -1, wifiRssiDbm = NULL
        WHERE id GLOB 'WR-*' AND (batteryLevel != -1 OR wifiRssiDbm IS NOT NULL)
    """)
    suspend fun clearStoredBadgeTelemetry()

    @Query("UPDATE workers SET batteryLevel = -1, wifiRssiDbm = NULL WHERE id IN (:ids)")
    suspend fun clearBadgeTelemetry(ids: List<String>)

    @Query("UPDATE workers SET channelId = :channelId WHERE id = :id")
    suspend fun updateChannel(id: String, channelId: String)

    @Query("UPDATE workers SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("DELETE FROM workers WHERE id = :id")
    suspend fun deleteById(id: String)
}
