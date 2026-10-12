package com.example.nexshare.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.nexshare.network.HotspotManager
import com.example.nexshare.network.TcpTransferManager
import com.example.nexshare.transfer.HotspotStatus
import com.example.nexshare.transfer.HotspotUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Starts the local-only hotspot. On success it reports the credentials and
 * the TCP port the transfer server listens on. Stopping releases the reservation.
 */
class HotspotViewModel(application: Application) : AndroidViewModel(application) {

    private val hotspot = HotspotManager(application)

    private val _state = MutableStateFlow(HotspotUiState())
    val state: StateFlow<HotspotUiState> = _state.asStateFlow()

    /** Set by the app container once the transfer server exists. */
    var transferServer: TcpTransferManager? = null
    var deviceName: String = "NexShare device"
    var onServerPort: (Int) -> Unit = {}

    fun start() {
        _state.value = HotspotUiState(status = HotspotStatus.STARTING)
        hotspot.start(object : HotspotManager.Listener {
            override fun onStarted(ssid: String, password: String, localIp: String) {
                val port = transferServer?.startServer(deviceName) ?: 0
                onServerPort(port)
                _state.value = HotspotUiState(
                    status = HotspotStatus.RUNNING,
                    ssid = ssid,
                    password = password,
                    localIp = localIp,
                    port = port
                )
            }

            override fun onStopped() {
                transferServer?.stopServer()
                _state.value = HotspotUiState()
            }

            override fun onFailed(reason: String) {
                _state.value = HotspotUiState(
                    status = HotspotStatus.ERROR,
                    errorMessage = reason
                )
            }
        })
    }

    fun stop() {
        transferServer?.stopServer()
        hotspot.stop()
        _state.value = HotspotUiState()
    }

    fun bind(server: TcpTransferManager, name: String, onPort: (Int) -> Unit) {
        transferServer = server
        deviceName = name
        onServerPort = onPort
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}