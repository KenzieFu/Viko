package com.example.viko.domain.team.repository

import com.example.viko.domain.team.model.Worker
import kotlinx.coroutines.flow.Flow

interface WorkerRepository {
    fun observeWorkers(): Flow<List<Worker>>
    suspend fun getWorker(id: String): Worker?
    suspend fun addWorker(worker: Worker)
    suspend fun updateWorker(worker: Worker)
    suspend fun removeWorker(id: String)
}
