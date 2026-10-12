package com.example.nexshare.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexshare.network.BluetoothDeviceManager
import com.example.nexshare.network.NsdDiscoveryManager
import com.example.nexshare.network.UdpDiscoveryManager
import com.example.nexshare.transfer.ConnectionMethod
import com.example.nexshare.transfer.Peer
import com.example.nexshare.transfer.PeerStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PeersViewModel(application: Application) : AndroidViewModel(application) {

    private val discovery = NsdDiscoveryManager(application)
    private val btManager = BluetoothDeviceManager(application)
    private val udpDiscovery = UdpDiscoveryManager(application) { id, name, host, port ->
        upsert(id, name, host, port)
    }

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var started = false
    private var registeredPort = 0

    init {
        loadBondedDevices(application)
    }

    @SuppressLint("MissingPermission")
    private fun loadBondedDevices(application: Application) {
        runCatching {
            val adapter = application.getSystemService(BluetoothManager::class.java)?.adapter
            adapter?.bondedDevices?.forEach { dev ->
                upsertBluetoothPeer(dev.address, dev.name ?: dev.address)
            }
        }
    }

    /** Starts discovery once. Safe to call from multiple screens. */
    fun startDiscovery(localDeviceName: String, localPort: Int) {
        if (localPort > 0 && registeredPort != localPort) {
            registeredPort = localPort
            runCatching {
                discovery.register(
                    deviceName = localDeviceName,
                    port = localPort,
                    onError = { _message.value = it }
                )
            }
            runCatching {
                udpDiscovery.startListening(localDeviceName, localPort)
            }
        }

        // Send instant UDP broadcast ping (< 100 ms response)
        runCatching {
            udpDiscovery.sendPing(localDeviceName, if (localPort > 0) localPort else 5000)
        }

        // Active Bluetooth discovery for ALL connectable nearby Bluetooth devices
        runCatching {
            btManager.startDiscovery(object : BluetoothDeviceManager.Listener {
                override fun onDeviceFound(address: String, name: String) {
                    upsertBluetoothPeer(address, name)
                }

                override fun onDiscoveryFinished() {}
            })
        }

        if (started) return
        started = true
        runCatching {
            discovery.startDiscovery(object : NsdDiscoveryManager.Listener {
                override fun onPeerFound(peerId: String, deviceName: String, host: String, port: Int) {
                    upsert(peerId, deviceName, host, port)
                }

                override fun onPeerLost(peerId: String) {
                    _peers.update { list ->
                        list.map { if (it.peerId == peerId) it.copy(status = PeerStatus.OFFLINE) else it }
                    }
                }

                override fun onError(message: String) {
                    _message.value = message
                }
            })
        }
        viewModelScope.launch { expireStalePeers() }
    }

    fun togglePair(peerId: String) {
        _peers.update { list ->
            list.map { if (it.peerId == peerId) it.copy(trusted = !it.trusted) else it }
        }
    }

    fun block(peerId: String) {
        _peers.update { list -> list.filterNot { it.peerId == peerId } }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun upsert(peerId: String, deviceName: String, host: String, port: Int) {
        val now = System.currentTimeMillis()
        _peers.update { list ->
            val existing = list.firstOrNull { it.peerId == peerId }
            val updated = Peer(
                peerId = peerId,
                deviceName = deviceName,
                host = host,
                port = port,
                method = ConnectionMethod.HOTSPOT,
                status = PeerStatus.ONLINE,
                trusted = existing?.trusted ?: false,
                lastSeenMillis = now
            )
            if (existing == null) list + updated
            else list.map { if (it.peerId == peerId) updated else it }
        }
    }

    private fun upsertBluetoothPeer(address: String, name: String) {
        val now = System.currentTimeMillis()
        _peers.update { list ->
            val existing = list.firstOrNull { it.peerId == address }
            val updated = Peer(
                peerId = address,
                deviceName = name,
                host = address,
                port = 0,
                method = ConnectionMethod.BLUETOOTH,
                status = PeerStatus.ONLINE,
                trusted = existing?.trusted ?: false,
                lastSeenMillis = now
            )
            if (existing == null) list + updated
            else list.map { if (it.peerId == address) updated else it }
        }
    }

    /** Marks peers offline when they have not been seen for [STALE_AFTER_MS]. */
    private suspend fun expireStalePeers() {
        while (true) {
            delay(5_000L)
            val cutoff = System.currentTimeMillis() - STALE_AFTER_MS
            _peers.update { list ->
                list.map {
                    if (it.status == PeerStatus.ONLINE && it.lastSeenMillis < cutoff) {
                        it.copy(status = PeerStatus.OFFLINE)
                    } else it
                }
            }
        }
    }

    override fun onCleared() {
        runCatching { discovery.shutdown() }
        runCatching { btManager.stopDiscovery() }
        runCatching { udpDiscovery.stopListening() }
        super.onCleared()
    }

    companion object {
        private const val STALE_AFTER_MS = 20_000L
    }
}
