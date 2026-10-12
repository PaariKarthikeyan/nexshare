package com.example.nexshare.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth Classic RFCOMM. Discovery, server socket creation, and client connection.
 * Blocking calls (accept, connect) must run off the main thread.
 */
class BluetoothDeviceManager(private val context: Context) {

    interface Listener {
        fun onDeviceFound(address: String, name: String)
        fun onDiscoveryFinished()
    }

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private var receiver: BroadcastReceiver? = null

    val isEnabled: Boolean get() = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startDiscovery(listener: Listener) {
        val bt = adapter ?: return
        stopDiscovery()
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null) {
                            listener.onDeviceFound(device.address, device.name ?: device.address)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> listener.onDiscoveryFinished()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
        bt.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        adapter?.cancelDiscovery()
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    /** Opens a listening RFCOMM socket. Call accept() on a background thread. */
    @SuppressLint("MissingPermission")
    fun listen(): BluetoothServerSocket? =
        try {
            adapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
        } catch (e: IOException) {
            null
        }

    /** Connects to a paired or discovered device. Blocks; call from a background thread. */
    @SuppressLint("MissingPermission")
    fun connect(address: String): BluetoothSocket? {
        val device = adapter?.getRemoteDevice(address) ?: return null
        stopDiscovery()
        return try {
            device.createRfcommSocketToServiceRecord(SERVICE_UUID).also { it.connect() }
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        const val SERVICE_NAME = "NexShare"
        /** Fixed UUID so both phones agree on the RFCOMM service. */
        val SERVICE_UUID: UUID = UUID.fromString("6f1c3a52-8d2e-4b8a-9f61-7c2e5d4a1b90")
    }
}