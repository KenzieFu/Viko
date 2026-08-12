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

    @Query("UPDATE workers SET channelId = :channelId WHERE id = :id")
    suspend fun updateChannel(id: String, channelId: String)

    @Query("UPDATE workers SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("DELETE FROM workers WHERE id = :id")
    suspend fun deleteById(id: String)
}
