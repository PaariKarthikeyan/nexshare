package com.example.nexshare.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Wraps WifiManager.startLocalOnlyHotspot(). The reservation must be closed
 * when the app no longer needs the hotspot, so stop() always releases it.
 *
 * Callers must hold the location and nearby-Wi-Fi permissions before calling start().
 */
class HotspotManager(private val context: Context) {

    interface Listener {
        fun onStarted(ssid: String, password: String, localIp: String)
        fun onStopped()
        fun onFailed(reason: String)
    }

    private val wifi = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var reservation: LocalOnlyHotspotReservation? = null

    @SuppressLint("MissingPermission")
    fun start(listener: Listener) {
        if (reservation != null) {
            listener.onFailed("Hotspot is already running")
            return
        }
        val callback = object : LocalOnlyHotspotCallback() {
            override fun onStarted(res: LocalOnlyHotspotReservation) {
                reservation = res
                val config = res.softApConfiguration
                val ssid = config.ssid ?: "NexShare"
                val password = config.passphrase ?: ""
                listener.onStarted(ssid, password, localIpv4() ?: "192.168.43.1")
            }

            override fun onStopped() {
                reservation = null
                listener.onStopped()
            }

            override fun onFailed(reason: Int) {
                reservation = null
                listener.onFailed(failureMessage(reason))
            }
        }
        try {
            wifi.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            listener.onFailed("Permission denied: ${e.message}")
        } catch (e: IllegalStateException) {
            listener.onFailed("Hotspot unavailable: ${e.message}")
        }
    }

    fun stop() {
        reservation?.close()
        reservation = null
    }

    val isRunning: Boolean get() = reservation != null

    private fun failureMessage(reason: Int): String = when (reason) {
        LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "No free Wi-Fi channel"
        LocalOnlyHotspotCallback.ERROR_GENERIC -> "Hotspot failed to start"
        LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "Turn off Wi-Fi tethering first"
        LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "Tethering is disabled by policy"
        else -> "Hotspot failed (code $reason)"
    }

    /** Finds this device's IPv4 address on the hotspot interface, if one exists. */
    private fun localIpv4(): String? =
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
}