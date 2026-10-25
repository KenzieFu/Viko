package com.example.viko.data.team.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.bluetooth.BluetoothManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * Runtime permission set for BLE provisioning. The required permissions changed
 * fundamentally in Android 12 (API 31): before it, BLE scanning was gated behind
 * *location* permission; from 12 on there are dedicated Bluetooth permissions.
 *
 * The pre-12 case is the one that bites — with ACCESS_FINE_LOCATION missing, or
 * with the device's Location Services toggle off, a scan starts successfully and
 * simply never reports a result. No error, no callback, no devices.
 */
object BlePermissions {
    fun isSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun isEnabled(context: Context): Boolean = allGranted(context) && runCatching {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }.getOrDefault(false)

    fun isLocationReady(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ||
        context.getSystemService(LocationManager::class.java)?.let {
            LocationManagerCompat.isLocationEnabled(it)
        } == true

    val required: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun allGranted(context: Context): Boolean = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Process-wide holder. BLE is a single shared radio and the scan results need to
 * survive navigation between the pairing screen and the setup screen, so one
 * instance is kept rather than one per composable.
 */
object VikoBle {
    @Volatile
    private var instance: BleProvisioner? = null

    fun get(context: Context): BleProvisioner =
        instance ?: synchronized(this) {
            instance ?: BleProvisioner(context.applicationContext).also { instance = it }
        }
}
