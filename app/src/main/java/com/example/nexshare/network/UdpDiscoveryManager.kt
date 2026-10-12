package com.example.nexshare.network

import android.content.Context
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
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

    fun startListening(localDeviceName: String, localPort: Int) {
        stopListening()
        scope.launch {
            try {
                val socket = DatagramSocket(UDP_PORT)
                socket.broadcast = true
                listenSocket = socket
                val buffer = ByteArray(1024)
                while (!socket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    runCatching { socket.receive(packet) }
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    val host = packet.address.hostAddress ?: continue
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
                // Ignore socket errors
            }
        }
    }

    fun sendPing(localDeviceName: String, localPort: Int) {
        scope.launch {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val pingMsg = "NXSH_PING:$localDeviceName:$localPort".toByteArray(Charsets.UTF_8)
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(pingMsg, pingMsg.size, broadcastAddr, UDP_PORT)
                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                // Ignore send errors
            }
        }
    }

    fun stopListening() {
        runCatching { listenSocket?.close() }
        listenSocket = null
    }

    companion object {
        const val UDP_PORT = 5002
    }
}
