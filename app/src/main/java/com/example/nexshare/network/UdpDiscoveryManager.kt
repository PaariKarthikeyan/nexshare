package com.example.nexshare.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UdpDiscoveryManager(
    private val context: Context,
    private val onPeerFound: (peerId: String, deviceName: String, host: String, port: Int) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val wifiManager: WifiManager? by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    fun startListening(localDeviceName: String, localPort: Int) {
        stopListening()
        runCatching {
            multicastLock = wifiManager?.createMulticastLock("NexShareUdpLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        scope.launch {
            try {
                val socket = DatagramSocket(UDP_PORT)
                socket.broadcast = true
                socket.reuseAddress = true
                listenSocket = socket
                val buffer = ByteArray(1024)
                while (!socket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    runCatching { socket.receive(packet) }
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    val host = packet.address?.hostAddress ?: continue
                    if (message.startsWith("NXSH_PING:")) {
                        val parts = message.split(":")
                        if (parts.size >= 3) {
                            val senderName = parts[1]
                            val senderPort = parts[2].toIntOrNull() ?: 5000
                            onPeerFound(host, senderName, host, senderPort)

                            // Respond with PONG
                            val pongMsg = "NXSH_PONG:$localDeviceName:$localPort".toByteArray(Charsets.UTF_8)
                            val pongPacket = DatagramPacket(pongMsg, pongMsg.size, packet.address, UDP_PORT)
                            runCatching { socket.send(pongPacket) }
                        }
                    } else if (message.startsWith("NXSH_PONG:")) {
                        val parts = message.split(":")
                        if (parts.size >= 3) {
                            val senderName = parts[1]
                            val senderPort = parts[2].toIntOrNull() ?: 5000
                            onPeerFound(host, senderName, host, senderPort)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UdpDiscovery", "Listen error: ${e.message}")
            }
        }
    }

    fun sendPing(localDeviceName: String, localPort: Int) {
        scope.launch {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val pingMsg = "NXSH_PING:$localDeviceName:$localPort".toByteArray(Charsets.UTF_8)
                val broadcastAddresses = getBroadcastAddresses()
                for (address in broadcastAddresses) {
                    runCatching {
                        val packet = DatagramPacket(pingMsg, pingMsg.size, address, UDP_PORT)
                        socket.send(packet)
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.e("UdpDiscovery", "Ping error: ${e.message}")
            }
        }
    }

    fun sendHandshakePing(targetHost: String, localDeviceName: String, localPort: Int) {
        scope.launch {
            try {
                val socket = DatagramSocket()
                val pingMsg = "NXSH_PING:$localDeviceName:$localPort".toByteArray(Charsets.UTF_8)
                val address = InetAddress.getByName(targetHost)
                val packet = DatagramPacket(pingMsg, pingMsg.size, address, UDP_PORT)
                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                Log.e("UdpDiscovery", "Handshake ping error: ${e.message}")
            }
        }
    }

    fun stopListening() {
        runCatching { listenSocket?.close() }
        listenSocket = null
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }
        multicastLock = null
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        runCatching {
            list.add(InetAddress.getByName("255.255.255.255"))
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            for (iface in interfaces) {
                if (iface.isUp && !iface.isLoopback) {
                    for (addr in iface.interfaceAddresses) {
                        val broadcast = addr.broadcast
                        if (broadcast != null) {
                            list.add(broadcast)
                        }
                    }
                }
            }
        }
        return list.distinct()
    }

    companion object {
        const val UDP_PORT = 5002
    }
}
