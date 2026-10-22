package com.example.nexshare.network

import com.example.nexshare.file.FileManager
import com.example.nexshare.file.TemporaryFileManager
import com.example.nexshare.transfer.SelectedFile
import com.example.nexshare.transfer.TransferControl
import com.example.nexshare.transfer.TransferOutcome
import com.example.nexshare.transfer.TransferProgress
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.example.nexshare.security.SecurityPolicy

class TcpTransferManager(
    fileManager: FileManager,
    tempFiles: TemporaryFileManager,
    security: SecurityPolicy = SecurityPolicy(),
    onLog: (String) -> Unit = {},
    approve: (senderKey: String, senderName: String, offer: JSONObject) -> Boolean = { _, _, _ -> false },
    onReceived: (partFile: File, senderName: String, offer: JSONObject) -> Unit = { _, _, _ -> }
) {
    private val protocol = TransferProtocolHandler(fileManager, tempFiles, security, onLog, approve, onReceived)
    private val fileManager = fileManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    fun startServer(deviceName: String): Int {
        stopServer()
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(0))
        serverSocket = server
        acceptJob = scope.launch {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                launch {
                    socket.use { s ->
                        s.soTimeout = READ_TIMEOUT_MS
                        protocol.receive(
                            socketConnection(s),
                            s.inetAddress.hostAddress ?: "unknown",
                            deviceName
                        )
                    }
                }
            }
        }
        return server.localPort
    }

    fun stopServer() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    suspend fun send(
        host: String,
        port: Int,
        deviceName: String,
        file: SelectedFile,
        sha256: String,
        transferId: String = UUID.randomUUID().toString(),
        control: TransferControl = TransferControl(),
        onProgress: (TransferProgress) -> Unit = {}
    ): TransferOutcome = withContext(Dispatchers.IO) {
        if (port <= 0 || host.isBlank() || host == "0.0.0.0") {
            return@withContext TransferOutcome.FAILED
        }
        val socket = Socket()
        try {
            val address = InetSocketAddress(host, port)
            socket.connect(address, CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            protocol.send(socketConnection(socket), deviceName, file, sha256, transferId, control, onProgress)
        } catch (e: Exception) {
            TransferOutcome.FAILED
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun socketConnection(socket: Socket): TransferConnection =
        TransferConnection(socket.getInputStream(), socket.getOutputStream()) {
            runCatching { socket.close() }
        }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}