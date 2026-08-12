package com.example.viko.data.team.repository

import com.example.viko.data.team.local.WorkerDao
import com.example.viko.data.team.local.toDomain
import com.example.viko.data.team.local.toEntity
import com.example.viko.domain.team.model.Worker
import com.example.viko.domain.team.repository.WorkerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkerRepositoryImpl(private val dao: WorkerDao) : WorkerRepository {

    override fun observeWorkers(): Flow<List<Worker>> =
        dao.observeWorkers().map { list -> list.map { it.toDomain() } }

    override fun observeWorker(id: String): Flow<Worker?> =
        dao.observeWorker(id).map { it?.toDomain() }

    override suspend fun getWorker(id: String): Worker? =
        dao.getWorker(id)?.toDomain()

    override suspend fun addWorker(worker: Worker) = dao.insert(worker.toEntity())

    override suspend fun updateWorker(worker: Worker) = dao.update(worker.toEntity())

    override suspend fun removeWorker(id: String) = dao.deleteById(id)

    override suspend fun assignToChannel(workerId: String, channelId: String) =
        dao.updateChannel(workerId, channelId)

    override suspend fun renameWorker(workerId: String, name: String) =
        dao.updateName(workerId, name)
}
