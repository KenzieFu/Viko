package com.example.viko.data.team.scanner

import android.content.Context
import com.example.viko.data.team.ble.BlePermissions
import com.example.viko.data.team.ble.BleProvisioner
import com.example.viko.data.team.ble.VikoBle
import com.example.viko.domain.team.model.BadgeSetup
import com.example.viko.domain.team.model.NearbyDevice
import com.example.viko.domain.team.repository.DeviceScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The real badge discovery path: a BLE scan for the ViKo GATT service, and a
 * GATT write of the setup blob. Everything Android-specific lives in
 * [BleProvisioner]; this only adapts it to the domain's [DeviceScanner].
 */
class BleDeviceScanner(context: Context) : DeviceScanner {

    private val appContext = context.applicationContext
    private val ble = VikoBle.get(appContext)

    /** True once the OS has granted BLUETOOTH_SCAN/CONNECT (or location pre-12). */
    val hasPermissions: Boolean get() = BlePermissions.allGranted(appContext)

    override val isBluetoothEnabled: Boolean get() = ble.isBluetoothEnabled

    /**
     * channelFlow rather than flow: the scan window is enforced with
     * [withTimeoutOrNull], which runs the collection in a child coroutine, and
     * a plain flow builder rejects emissions from one.
     */
    override fun scan(): Flow<List<NearbyDevice>> = channelFlow {
        // Without the runtime permission startScan throws SecurityException;
        // the sheet asks for it first, this is the belt-and-braces.
        if (!hasPermissions) {
            send(emptyList())
            return@channelFlow
        }
        try {
            ble.startScan()
            withTimeoutOrNull(SCAN_WINDOW_MS) {
                combine(ble.devices, ble.scanError) { devices, error ->
                    if (error != null) throw IllegalStateException(error)
                    devices
                }.collect { send(it) }
            }
        } finally {
            // Scanning is expensive on the radio, so the window closes on its
            // own; the supervisor can start another from the sheet.
            ble.stopScan()
        }
    }

    override suspend fun provision(device: NearbyDevice, setup: BadgeSetup): Result<Unit> {
        // Provisioning and scanning share one radio, and a scan running through
        // a GATT connect is the classic cause of status-133 connection failures.
        ble.stopScan()

        val config = BleProvisioner.Config(
            ssid = setup.wifiSsid,
            password = setup.wifiPassword,
            deviceId = setup.deviceId,
            // The docx keeps worker_id separate from device_id, but nothing in
            // the manager keys off it — workers are tracked by device_id — so
            // inventing a second identifier would only be another thing to
            // collide. Revisit if payroll ids ever come from a real HR system.
            workerId = setup.deviceId,
            workerName = setup.workerName,
            language = setup.language,
            targetLanguage = setup.targetLanguage,
            factoryZone = setup.factoryZone
        )

        return when (val result = ble.provision(device.address, config)) {
            is BleProvisioner.Result.Success -> Result.success(Unit)
            is BleProvisioner.Result.Failure -> Result.failure(BadgeSetupFailed(result.message))
        }
    }

    private companion object {
        /** Bounded scan; the visible Find nearby devices button starts another. */
        const val SCAN_WINDOW_MS = 30_000L
    }
}

/** Carries the badge's own words back to the sheet, which shows them verbatim. */
class BadgeSetupFailed(message: String) : Exception(message)
