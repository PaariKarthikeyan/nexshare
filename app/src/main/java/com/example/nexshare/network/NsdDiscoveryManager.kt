package com.example.nexshare.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.UUID

/**
 * Registers this device as a _nexshare._tcp. service and discovers other NexShare
 * devices on the same local network. Results are reported through [Listener].
 *
 * Resolving is done one service at a time: Android 14+ removed the old resolveService
 * API, so NsdManager.resolveService is used on older releases and the registration
 * callback handles newer ones.
 */
class NsdDiscoveryManager(context: Context) {

    interface Listener {
        fun onPeerFound(peerId: String, deviceName: String, host: String, port: Int)
        fun onPeerLost(peerId: String)
        fun onError(message: String)
    }

    private val nsd = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as NsdManager

    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val localPeerId = UUID.randomUUID().toString()

    /** Advertises this device on [port] with metadata in the TXT record. */
    fun register(deviceName: String, port: Int, onRegistered: (String) -> Unit = {}, onError: (String) -> Unit = {}) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = "NexShare-${localPeerId.take(8)}"
            serviceType = Protocol.SERVICE_TYPE
            setPort(port)
            setAttribute("peerId", localPeerId)
            setAttribute("deviceName", deviceName.take(40))
            setAttribute("protocolVersion", Protocol.PROTOCOL_VERSION_INT.toString())
            setAttribute("supportedMethods", "HOTSPOT,BLUETOOTH")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                onRegistered(serviceInfo.serviceName)
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                onError("Advertising failed ($errorCode)")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun unregister() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    /** Starts discovery. Found services are resolved to a host and port before reporting. */
    fun startDiscovery(listener: Listener) {
        stopDiscovery()
        val dl = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                listener.onError("Discovery failed to start ($errorCode)")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.startsWith("_nexshare._tcp")) return
                if (service.serviceName.startsWith("NexShare-${localPeerId.take(8)}")) return
                resolve(service, listener)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                listener.onPeerLost(service.serviceName)
            }
        }
        discovery = dl
        nsd.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, dl)
    }

    fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
    }

    @Suppress("DEPRECATION")
    private fun resolve(service: NsdServiceInfo, listener: Listener) {
        nsd.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                listener.onError("Could not resolve ${serviceInfo.serviceName} ($errorCode)")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val attrs = serviceInfo.attributes
                val peerId = attrs["peerId"]?.toString(Charsets.UTF_8) ?: serviceInfo.serviceName
                val name = attrs["deviceName"]?.toString(Charsets.UTF_8) ?: serviceInfo.serviceName
                val host = serviceInfo.host?.hostAddress ?: return
                listener.onPeerFound(peerId, name, host, serviceInfo.port)
            }
        })
    }

    fun shutdown() {
        unregister()
        stopDiscovery()
    }
}