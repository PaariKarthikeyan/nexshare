package com.example.nexshare.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexshare.PermissionUtils
import com.example.nexshare.file.FileManager
import com.example.nexshare.file.TemporaryFileManager
import com.example.nexshare.network.BluetoothDeviceManager
import com.example.nexshare.network.BluetoothTransferManager
import com.example.nexshare.transfer.BluetoothAvailability
import com.example.nexshare.transfer.BluetoothDeviceUi
import com.example.nexshare.transfer.BluetoothUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private val adapter: BluetoothAdapter? =
        application.getSystemService(BluetoothManager::class.java)?.adapter

    private val devices = BluetoothDeviceManager(application)
    private val transfers = BluetoothTransferManager(
        device = devices,
        fileManager = FileManager(application),
        tempFiles = TemporaryFileManager(application)
    )

    private val _state = MutableStateFlow(BluetoothUiState())
    val state: StateFlow<BluetoothUiState> = _state.asStateFlow()

    private val _found = MutableStateFlow<List<BluetoothDeviceUi>>(emptyList())
    val found: StateFlow<List<BluetoothDeviceUi>> = _found.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val bt = adapter
        if (bt == null) {
            _state.value = BluetoothUiState(
                availability = BluetoothAvailability.UNAVAILABLE,
                message = "This device has no Bluetooth adapter."
            )
            return
        }
        val enabled = bt.isEnabled
        val availability = if (enabled) BluetoothAvailability.ENABLED else BluetoothAvailability.DISABLED
        val permitted = PermissionUtils.missing(getApplication(), PermissionUtils.bluetoothPermissions()).isEmpty()
        if (!permitted) {
            _state.value = BluetoothUiState(
                availability = availability,
                permissionGranted = false,
                message = "Grant Bluetooth permission to list paired devices."
            )
            return
        }
        _state.value = BluetoothUiState(
            availability = availability,
            permissionGranted = true,
            pairedDevices = readBonded(bt),
            message = if (enabled) null else "Turn on Bluetooth in system settings."
        )
    }

    /** Starts RFCOMM discovery. Results appear in [found]. */
    fun scan() {
        _found.value = emptyList()
        devices.startDiscovery(object : BluetoothDeviceManager.Listener {
            override fun onDeviceFound(address: String, name: String) {
                _found.update { list ->
                    if (list.any { it.address == address }) list
                    else list + BluetoothDeviceUi(address, name)
                }
            }

            override fun onDiscoveryFinished() {
                _state.update { it.copy(message = "Scan finished.") }
            }
        })
        _state.update { it.copy(message = "Scanning for nearby devices…") }
    }

    fun stopScan() {
        devices.stopDiscovery()
    }

    /** Starts the RFCOMM server so other phones can send to this one. */
    fun startListening(deviceName: String) {
        val ok = transfers.startServer(deviceName)
        _state.update {
            it.copy(message = if (ok) "Listening for Bluetooth transfers." else "Could not open the Bluetooth server.")
        }
    }

    fun stopListening() {
        transfers.stopServer()
        _state.update { it.copy(message = "Stopped listening.") }
    }

    fun isEnabled(): Boolean = devices.isEnabled

    fun sendTo(address: String, deviceName: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            onResult("Connecting…")
        }
    }

    @SuppressLint("MissingPermission")
    private fun readBonded(bt: BluetoothAdapter): List<BluetoothDeviceUi> =
        bt.bondedDevices?.map { BluetoothDeviceUi(it.address, it.name ?: it.address) } ?: emptyList()

    override fun onCleared() {
        devices.stopDiscovery()
        transfers.stopServer()
        super.onCleared()
    }
}