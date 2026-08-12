package com.example.viko.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.example.viko.data.NearbyDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume

/**
 * BLE GATT provisioning client for the ViKo badge.
 *
 * Mirrors firmware/wearable_badge_network/network/BleProvisioning.h — the UUIDs
 * and the JSON field names below MUST stay in sync with that file, and with the
 * device_register schema in Detailed System.docx.
 *
 * Flow (docx "First-time pairing", steps 2-5):
 *   scan for badges advertising VIKO_SERVICE_UUID
 *   -> connect GATT
 *   -> negotiate a larger MTU (the config JSON is ~200 bytes, default ATT MTU is 23)
 *   -> subscribe to the status characteristic
 *   -> write the config JSON
 *   -> wait for the badge to notify "saved"
 *
 * The badge reboots into Wi-Fi station mode immediately after saving, so the
 * disconnect that follows a "saved" notify is expected, not an error.
 */
class BleProvisioner(context: Context) {

    companion object {
        private const val TAG = "VikoBle"

        val SERVICE_UUID: UUID = UUID.fromString("6e6f7663-0001-4a56-494b-4f4e4556494b")
        val CONFIG_CHAR_UUID: UUID = UUID.fromString("6e6f7663-0002-4a56-494b-4f4e4556494b")
        val STATUS_CHAR_UUID: UUID = UUID.fromString("6e6f7663-0003-4a56-494b-4f4e4556494b")

        /** Standard Client Characteristic Configuration Descriptor. */
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val REQUESTED_MTU = 256
        private const val PROVISION_TIMEOUT_MS = 20_000L
    }

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private val _devices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val devices: StateFlow<List<NearbyDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    // ---------------------------------------------------------------- scanning

    private var scanCallback: ScanCallback? = null

    /**
     * Scans for badges in provisioning mode. Results accumulate into [devices];
     * a badge already provisioned is not advertising and will not appear.
     *
     * Rescanning replaces the list rather than merging, so a badge that has been
     * provisioned and rebooted disappears instead of lingering as a stale entry
     * the supervisor could tap and then hang on.
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "no BLE scanner — bluetooth off?")
            return
        }
        stopScan()
        _devices.value = emptyList()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val address = device.address ?: return
                // scanRecord name is used in preference to device.name: the latter
                // can be a cached value from a previous session and go stale.
                val name = result.scanRecord?.deviceName ?: device.name ?: "ViKo badge"
                val existing = _devices.value
                val idx = existing.indexOfFirst { it.address == address }
                val entry = NearbyDevice(
                    id = address,
                    name = name,
                    address = address,
                    signalStrength = result.rssi
                )
                _devices.value =
                    if (idx >= 0) existing.toMutableList().also { it[idx] = entry }
                    else existing + entry
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "scan failed: $errorCode")
                _isScanning.value = false
            }
        }
        scanCallback = cb
        _isScanning.value = true
        scanner.startScan(listOf(filter), settings, cb)
        Log.i(TAG, "scan started")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val cb = scanCallback ?: return
        scanCallback = null
        _isScanning.value = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(cb) }
    }

    // ------------------------------------------------------------ provisioning

    data class Config(
        val ssid: String,
        val password: String,
        val deviceId: String,
        val workerId: String,
        val workerName: String,
        val language: String = "vi",
        val targetLanguage: String = "ko",
        val factoryZone: String
    ) {
        /** Field names must match BleProvisioning.h handleConfigWrite(). */
        fun toJson(): String = JSONObject()
            .put("ssid", ssid)
            .put("password", password)
            .put("device_id", deviceId)
            .put("worker_id", workerId)
            .put("worker_name", workerName)
            .put("language", language)
            .put("target_language", targetLanguage)
            .put("factory_zone", factoryZone)
            .toString()
    }

    sealed class Result {
        object Success : Result()
        data class Failure(val message: String) : Result()
    }

    /**
     * Connects to [address], writes [config], and waits for the badge to confirm.
     * Always resumes exactly once, including on timeout, and always closes the
     * GATT connection — a leaked BluetoothGatt exhausts the Android BLE client
     * slots (there are only ~7) and every later attempt fails with status 133
     * for reasons that look nothing like the actual cause.
     */
    @SuppressLint("MissingPermission")
    suspend fun provision(address: String, config: Config): Result =
        suspendCancellableCoroutine { cont ->
            val device = try {
                adapter?.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                null
            }
            if (device == null) {
                cont.resume(Result.Failure("Invalid device address"))
                return@suspendCancellableCoroutine
            }

            val payload = config.toJson().toByteArray(Charsets.UTF_8)
            Log.i(TAG, "provisioning $address, payload ${payload.size} bytes")

            var gatt: BluetoothGatt? = null
            var finished = false
            val handler = Handler(Looper.getMainLooper())

            fun finish(result: Result) {
                if (finished) return
                finished = true
                handler.removeCallbacksAndMessages(null)
                runCatching { gatt?.close() }
                if (cont.isActive) cont.resume(result)
            }

            val timeout = Runnable {
                finish(Result.Failure("Timed out. Is the badge still in pairing mode?"))
            }
            handler.postDelayed(timeout, PROVISION_TIMEOUT_MS)

            val callback = object : BluetoothGattCallback() {

                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.i(TAG, "connected, discovering services")
                            g.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            // A disconnect AFTER the badge notified "saved" is
                            // normal — it reboots into station mode. finish() is
                            // idempotent so that success is not overwritten here.
                            finish(Result.Failure("Disconnected (status $status)"))
                        }
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Result.Failure("Service discovery failed ($status)"))
                        return
                    }
                    if (g.getService(SERVICE_UUID) == null) {
                        finish(Result.Failure("Not a ViKo badge — service not found"))
                        return
                    }
                    g.requestMtu(REQUESTED_MTU)
                }

                override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                    // ATT overhead is 3 bytes; a 23-byte MTU leaves 20 usable and
                    // would silently truncate the JSON on the badge side.
                    Log.i(TAG, "MTU now $mtu (usable ${mtu - 3}), payload ${payload.size}")
                    if (mtu - 3 < payload.size) {
                        finish(Result.Failure("MTU too small ($mtu) for ${payload.size}-byte config"))
                        return
                    }
                    subscribeToStatus(g)
                }

                private fun subscribeToStatus(g: BluetoothGatt) {
                    val statusChar = g.getService(SERVICE_UUID)?.getCharacteristic(STATUS_CHAR_UUID)
                    if (statusChar == null) {
                        finish(Result.Failure("Status characteristic missing"))
                        return
                    }
                    g.setCharacteristicNotification(statusChar, true)
                    val cccd = statusChar.getDescriptor(CCCD_UUID)
                    if (cccd == null) {
                        // No CCCD: can't subscribe. Write anyway and fall back to
                        // treating a successful write as success.
                        writeConfig(g)
                        return
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(cccd)
                    }
                }

                override fun onDescriptorWrite(
                    g: BluetoothGatt,
                    descriptor: BluetoothGattDescriptor,
                    status: Int
                ) {
                    writeConfig(g)
                }

                private fun writeConfig(g: BluetoothGatt) {
                    val configChar = g.getService(SERVICE_UUID)?.getCharacteristic(CONFIG_CHAR_UUID)
                    if (configChar == null) {
                        finish(Result.Failure("Config characteristic missing"))
                        return
                    }
                    val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeCharacteristic(
                            configChar,
                            payload,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        ) == BluetoothGatt.GATT_SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        configChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        @Suppress("DEPRECATION")
                        configChar.value = payload
                        @Suppress("DEPRECATION")
                        g.writeCharacteristic(configChar)
                    }
                    if (!ok) finish(Result.Failure("Failed to send config"))
                }

                override fun onCharacteristicWrite(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Result.Failure("Config write rejected ($status)"))
                    }
                    // Success path: wait for the badge's "saved" notify below.
                }

                // API 33+ delivers the value as a parameter.
                override fun onCharacteristicChanged(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) = handleStatus(String(value, Charsets.UTF_8))

                @Deprecated("Pre-API-33 callback", ReplaceWith(""))
                @Suppress("DEPRECATION")
                override fun onCharacteristicChanged(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) = handleStatus(String(characteristic.value ?: byteArrayOf(), Charsets.UTF_8))

                private fun handleStatus(status: String) {
                    Log.i(TAG, "badge status: $status")
                    when (status.trim()) {
                        "saved" -> finish(Result.Success)
                        "bad_json" -> finish(Result.Failure("Badge rejected the config (bad JSON)"))
                        "missing_fields" -> finish(Result.Failure("Badge rejected the config (missing fields)"))
                        // "waiting" is the initial value — ignore.
                    }
                }
            }

            // TRANSPORT_LE is explicit: without it Android may attempt BR/EDR on
            // dual-mode adapters and fail with the notoriously unhelpful status 133.
            gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)

            cont.invokeOnCancellation { finish(Result.Failure("Cancelled")) }
        }
}
