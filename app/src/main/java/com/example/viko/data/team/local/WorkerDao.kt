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
    suspend fun getWorker(id: String): WorkerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(worker: WorkerEntity)

    @Update
    suspend fun update(worker: WorkerEntity)

    @Query("DELETE FROM workers WHERE id = :id")
    suspend fun deleteById(id: String)
}
