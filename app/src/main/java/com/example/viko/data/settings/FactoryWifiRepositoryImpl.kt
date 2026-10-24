package com.example.viko.data.settings

import android.content.Context
import com.example.viko.domain.settings.model.FactoryWifi
import com.example.viko.domain.settings.repository.FactoryWifiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the factory Wi-Fi in app-private SharedPreferences: two strings do not
 * need a Room table. The file is excluded from cloud backup and device transfer
 * (res/xml), so the factory password stays on this phone.
 */
class FactoryWifiRepositoryImpl(context: Context) : FactoryWifiRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val wifi = MutableStateFlow(
        FactoryWifi(
            ssid = prefs.getString(KEY_SSID, null).orEmpty(),
            password = prefs.getString(KEY_PASSWORD, null).orEmpty()
        )
    )

    override fun observeFactoryWifi(): Flow<FactoryWifi> = wifi.asStateFlow()

    override suspend fun saveFactoryWifi(wifi: FactoryWifi) {
        prefs.edit()
            .putString(KEY_SSID, wifi.ssid)
            .putString(KEY_PASSWORD, wifi.password)
            .apply()
        this.wifi.value = wifi
    }

    private companion object {
        /** Also named in res/xml/backup_rules.xml and data_extraction_rules.xml. */
        const val PREFS_NAME = "factory_wifi"
        const val KEY_SSID = "ssid"
        const val KEY_PASSWORD = "password"
    }
}
