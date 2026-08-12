package com.example.viko.domain.team.repository

import com.example.viko.domain.team.model.Worker
import kotlinx.coroutines.flow.Flow

interface WorkerRepository {
    fun observeWorkers(): Flow<List<Worker>>
    fun observeWorker(id: String): Flow<Worker?>
    suspend fun getWorker(id: String): Worker?
    suspend fun addWorker(worker: Worker)
    suspend fun updateWorker(worker: Worker)
    suspend fun removeWorker(id: String)

    /** Moves a worker to another channel. Pass an empty id to unassign. */
    suspend fun assignToChannel(workerId: String, channelId: String)

    suspend fun renameWorker(workerId: String, name: String)
}
