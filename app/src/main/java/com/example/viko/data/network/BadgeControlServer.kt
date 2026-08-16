package com.example.viko.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

/**
 * The manager side of the badge control channel.
 *
 * Mirrors firmware/wearable_badge_network/network/BadgeNetwork.h — the port,
 * the mDNS service type and the JSON field names below MUST stay in sync with
 * that file and with docs/networking-decisions.md.
 *
 *  - **TCP 5001**, newline-delimited JSON, one connection per message. The
 *    badge connects, sends, and closes; a held-open socket would die on every
 *    AP roam anyway.
 *  - **mDNS `_viko._tcp`** so badges find the phone without a hard-coded IP.
 *    Android's [NsdManager] can only publish a *service*, never a hostname, so
 *    the badge resolves this service rather than the `manager.local` hostname
 *    the docx originally sketched — `MDNS.queryHost("manager")` can never
 *    succeed against an Android phone.
 *
 * Speech itself travels on UDP 5002 — see [BadgeAudioServer]. This channel only
 * carries the events around it: who joined, who is holding their button, who
 * needs help.
 */
class BadgeControlServer(context: Context) {

    companion object {
        private const val TAG = "VikoNet"

        /** Must match VIKO_CONTROL_PORT in BadgeNetwork.h. */
        const val CONTROL_PORT = 5001

        /** Must match the MDNS.queryService() arguments in BadgeNetwork.h. */
        private const val SERVICE_TYPE = "_viko._tcp"
        private const val SERVICE_NAME = "ViKo Manager"

        private const val READ_TIMEOUT_MS = 3_000
        private const val REBIND_DELAY_MS = 2_000L
    }

    private val appContext = context.applicationContext
    private val nsdManager =
        appContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _packets = MutableSharedFlow<BadgePacket>(extraBufferCapacity = 16)

    /** Every control packet a badge has sent since [start]. */
    val packets: SharedFlow<BadgePacket> = _packets.asSharedFlow()

    private var job: Job? = null

    /**
     * Advertises the manager over mDNS and accepts badge connections until
     * [scope] is cancelled. Safe to call twice; the second call is a no-op.
     */
    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            advertise()
            while (isActive) {
                try {
                    ServerSocket(CONTROL_PORT).use { server ->
                        Log.i(TAG, "control channel listening on :$CONTROL_PORT")
                        while (isActive) accept(server)
                    }
                } catch (e: IOException) {
                    // Losing the bind (Wi-Fi off, port stolen) must not silently
                    // end badge reception for the rest of the process lifetime.
                    Log.e(TAG, "control socket failed, rebinding in ${REBIND_DELAY_MS}ms", e)
                    delay(REBIND_DELAY_MS)
                }
            }
        }
    }

    /**
     * Handles exactly one badge connection.
     *
     * ponytail: serial accept — one badge at a time. Each message is a few
     * hundred bytes and the read is capped by [READ_TIMEOUT_MS], so a handful
     * of simultaneous badges queue for milliseconds. Move the body into its own
     * `launch {}` if a whole factory of them ever collides here.
     */
    private suspend fun accept(server: ServerSocket) {
        val (line, source) = try {
            server.accept().use { socket ->
                socket.soTimeout = READ_TIMEOUT_MS
                socket.getInputStream().bufferedReader().readLine() to socket.inetAddress
            }
        } catch (e: IOException) {
            // Caught per-connection, not per-bind: one badge dropping mid-message
            // shouldn't tear down and rebind the listening socket.
            Log.w(TAG, "badge connection dropped mid-message", e)
            return
        }

        val packet = line?.let { parse(it, source) }
        if (packet == null) {
            Log.w(TAG, "ignoring unparseable control line: $line")
            return
        }
        Log.i(TAG, "received $packet")
        _packets.emit(packet)
    }

    /** Field names come from PacketProtocol.h — keep them in lockstep. */
    private fun parse(line: String, source: InetAddress): BadgePacket? {
        val json = runCatching { JSONObject(line) }.getOrNull() ?: return null
        val deviceId = json.optString("device_id").ifEmpty { return null }

        return when (json.optString("type")) {
            "device_register" -> BadgePacket.Register(
                deviceId = deviceId,
                source = source,
                workerId = json.optString("worker_id"),
                workerName = json.optString("worker_name"),
                language = json.optString("language", "vi"),
                targetLanguage = json.optString("target_language", "ko"),
                factoryZone = json.optString("factory_zone"),
                macAddress = json.optString("mac_address"),
                firmwareVersion = json.optString("firmware_version")
            )

            "control" -> when (val event = json.optString("event")) {
                "ptt_start", "ptt_end" -> BadgePacket.Ptt(
                    deviceId = deviceId,
                    source = source,
                    workerName = json.optString("worker_name"),
                    speaking = event == "ptt_start"
                )
                else -> null
            }

            "emergency_alert" -> BadgePacket.Emergency(
                deviceId = deviceId,
                source = source,
                workerName = json.optString("worker_name"),
                zone = json.optString("zone"),
                battery = json.optInt("battery", -1),
                rssi = json.optInt("rssi", 0)
            )

            else -> null
        }
    }

    private fun advertise() {
        val info = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            port = CONTROL_PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "advertising ${info.serviceName} as $SERVICE_TYPE:$CONTROL_PORT")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                // Badges fall back to the bench IP in BadgeNetwork.h when mDNS
                // fails, so this degrades rather than breaks.
                Log.e(TAG, "mDNS registration failed ($errorCode) — badges must use the bench IP")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        @Suppress("DEPRECATION") // the Executor overload is API 34+; minSdk here is 24
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }
}

/** One control message from a badge. Schemas live in `Detailed System.docx`. */
sealed interface BadgePacket {
    val deviceId: String

    /**
     * Where the message came from. Not part of any schema — it is the socket's
     * own view of the sender, and the only reason it is carried is that the
     * manager needs somewhere to send audio *back* to. A badge only sends UDP
     * while its button is held, so without this a supervisor could not speak to
     * a badge that had merely registered. Always trust the newest one: a DHCP
     * lease changes on every reconnect.
     */
    val source: InetAddress

    data class Register(
        override val deviceId: String,
        override val source: InetAddress,
        val workerId: String,
        val workerName: String,
        val language: String,
        val targetLanguage: String,
        val factoryZone: String,
        val macAddress: String,
        val firmwareVersion: String
    ) : BadgePacket

    /** `ptt_start` / `ptt_end` — push-to-talk held down and released. */
    data class Ptt(
        override val deviceId: String,
        override val source: InetAddress,
        val workerName: String,
        val speaking: Boolean
    ) : BadgePacket

    data class Emergency(
        override val deviceId: String,
        override val source: InetAddress,
        val workerName: String,
        val zone: String,
        /** -1 when the badge didn't report one. */
        val battery: Int,
        val rssi: Int
    ) : BadgePacket
}
