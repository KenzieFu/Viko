package com.example.viko.domain.team.model

/**
 * Everything a badge needs written into its flash before it can join the
 * factory network. Mirrors the JSON blob `BleProvisioning.h` expects on the
 * config characteristic — the badge saves this to NVS and reboots into Wi-Fi
 * station mode.
 */
data class BadgeSetup(
    val deviceId: String,
    val workerName: String,
    val factoryZone: String,
    val wifiSsid: String,
    val wifiPassword: String,
    val language: String = "vi",
    val targetLanguage: String = "ko"
)

/**
 * The permanent `device_id` assigned to the badge at [bleAddress].
 *
 * Derived from the BLE MAC rather than a counter so that re-provisioning the
 * same hardware produces the same id: the manager tracks badges by `device_id`
 * for their whole life (Detailed System.docx, "Topology"), and a badge that
 * came back with a fresh id would appear as a second worker.
 */
fun badgeDeviceId(bleAddress: String): String =
    "WR-" + bleAddress.replace(":", "").takeLast(4).uppercase()
