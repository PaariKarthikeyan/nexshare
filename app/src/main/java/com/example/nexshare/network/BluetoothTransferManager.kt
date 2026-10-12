package com.example.nexshare.network

import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import com.example.nexshare.file.FileManager
import com.example.nexshare.file.TemporaryFileManager
import com.example.nexshare.transfer.SelectedFile
import com.example.nexshare.transfer.TransferControl
import com.example.nexshare.transfer.TransferOutcome
import com.example.nexshare.transfer.TransferProgress
import com.example.nexshare.security.SecurityPolicy
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * RFCOMM server and client. Uses the same [TransferProtocolHandler] as TCP,
 * so the file protocol is not duplicated.
 */
class BluetoothTransferManager(
    private val device: BluetoothDeviceManager,
    fileManager: FileManager,
    tempFiles: TemporaryFileManager,
    security: SecurityPolicy = SecurityPolicy(),
    onLog: (String) -> Unit = {},
    approve: (senderKey: String, senderName: String, offer: JSONObject) -> Boolean = { _, _, _ -> false },
    onReceived: (partFile: File, senderName: String, offer: JSONObject) -> Unit = { _, _, _ -> }
) {

    private val protocol = TransferProtocolHandler(
        fileManager = fileManager,
        tempFiles = tempFiles,
        security = security,
        onLog = onLog,
        approve = approve,
        onReceived = onReceived
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: BluetoothServerSocket? = null
    private var acceptJob: Job? = null

    /** Starts accepting RFCOMM connections on a background coroutine. */
    fun startServer(deviceName: String, onLog: (String) -> Unit = {}): Boolean {
        stopServer()
        val serverSocket = device.listen() ?: return false
        server = serverSocket
        acceptJob = scope.launch {
            while (true) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: Exception) {
                    break
                }
                launch {
                    socket.use { s ->
                        protocol.receive(
                            socketConnection(s),
                            s.remoteDevice?.address ?: "bluetooth",
                            deviceName
                        )
                    }
                }
            }
        }
        return true
    }

    fun stopServer() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { server?.close() }
        server = null
    }

    /** Sends one file over RFCOMM to the device at [address]. */
    suspend fun send(
        address: String,
        deviceName: String,
        file: SelectedFile,
        sha256: String,
        transferId: String = UUID.randomUUID().toString(),
        control: TransferControl = TransferControl(),
        onProgress: (TransferProgress) -> Unit = {}
    ): TransferOutcome = withContext(Dispatchers.IO) {
        val socket = device.connect(address) ?: return@withContext TransferOutcome.FAILED
        try {
            protocol.send(socketConnection(socket), deviceName, file, sha256, transferId, control, onProgress)
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun socketConnection(socket: BluetoothSocket): TransferConnection =
        TransferConnection(socket.inputStream, socket.outputStream) {
            runCatching { socket.close() }
        }
}