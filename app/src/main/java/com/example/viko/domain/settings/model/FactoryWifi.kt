package com.example.viko.domain.settings.model

/**
 * The factory's Wi-Fi network. One for the whole site — every access point
 * broadcasts the same SSID and password — so it is set once and written into
 * every earpiece at pairing, rather than typed per member.
 */
data class FactoryWifi(
    val ssid: String = "",
    val password: String = ""
) {
    /** A badge provisioned without an SSID saves a blank network and never comes back. */
    val isConfigured: Boolean get() = ssid.isNotBlank()
}
