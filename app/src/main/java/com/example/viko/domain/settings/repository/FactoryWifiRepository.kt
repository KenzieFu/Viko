package com.example.viko.domain.settings.repository

import com.example.viko.domain.settings.model.FactoryWifi
import kotlinx.coroutines.flow.Flow

interface FactoryWifiRepository {
    fun observeFactoryWifi(): Flow<FactoryWifi>
    suspend fun saveFactoryWifi(wifi: FactoryWifi)
}
