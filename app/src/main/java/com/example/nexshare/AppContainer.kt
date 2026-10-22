package com.example.nexshare

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.nexshare.data.AppDatabase
import com.example.nexshare.file.FileManager
import com.example.nexshare.file.TemporaryFileManager
import com.example.nexshare.network.TcpTransferManager
import com.example.nexshare.notification.NotificationHelper
import com.example.nexshare.security.PairingManager
import com.example.nexshare.security.SecurityPolicy
import com.example.nexshare.transfer.PendingRequest
import com.example.nexshare.transfer.TransferRepository
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import com.example.nexshare.transfer.TransferService

class AppContainer(private val appContext: Context) {

    val context: Context get() = appContext
    val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val transferRepository: TransferRepository by lazy { TransferRepository(database) }
    val pairing = PairingManager()
    val security = SecurityPolicy()
    val fileManager: FileManager by lazy { FileManager(appContext) }
    val tempFiles: TemporaryFileManager by lazy { TemporaryFileManager(appContext) }
    val webShareServer: com.example.nexshare.network.WebShareServer by lazy {
        com.example.nexshare.network.WebShareServer(appContext, fileManager)
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pending = MutableStateFlow<PendingRequest?>(null)
    val pendingRequest: StateFlow<PendingRequest?> = _pending.asStateFlow()

    val transferServer: TcpTransferManager by lazy {
        TcpTransferManager(
            fileManager = fileManager,
            tempFiles = tempFiles,
            security = security,
            onLog = { Log.d(TAG, it) },
            approve = { key, name, offer -> awaitApproval(key, name, offer) },
            onReceived = { part, senderName, offer -> recordReceived(part, senderName, offer) }
        )
    }

    val bluetoothManager: com.example.nexshare.network.BluetoothTransferManager by lazy {
        com.example.nexshare.network.BluetoothTransferManager(
            device = com.example.nexshare.network.BluetoothDeviceManager(appContext),
            fileManager = fileManager,
            tempFiles = tempFiles,
            security = security,
            onLog = { Log.d(TAG, it) },
            approve = { key, name, offer -> awaitApproval(key, name, offer) },
            onReceived = { part, senderName, offer -> recordReceived(part, senderName, offer) }
        )
    }

    val transferService: TransferService by lazy {
        TransferService(
            context = appContext,
            tcpManager = transferServer,
            repository = transferRepository,
            deviceName = Build.MODEL.ifBlank { "NexShare device" }
        )
    }

    /** Runs on the server's IO thread. Blocks until the user decides or the timeout expires. */
    private fun awaitApproval(senderKey: String, senderName: String, offer: JSONObject): Boolean {
        val request = PendingRequest(
            senderHost = senderKey,
            senderName = senderName,
            fileName = offer.optString("fileName"),
            fileSize = offer.optLong("fileSize"),
            mimeType = offer.optString("mimeType", "application/octet-stream"),
            sha256 = offer.optString("sha256"),
            pairingCode = pairing.newCode(),
            decision = CompletableDeferred()
        )
        _pending.value = request
        val accepted = runBlocking {
            withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { request.decision.await() } ?: false
        }
        _pending.value = null
        return accepted
    }

    /** Records metadata for a verified receive and posts a notification. Never stores bytes in Room. */
    private fun recordReceived(part: File, senderName: String, offer: JSONObject) {
        val id = offer.optString("transferId")
        val name = offer.optString("fileName")

        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val destFile = File(downloadsDir, name)
            part.copyTo(destFile, overwrite = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file to downloads", e)
        }

        ioScope.launch {
            transferRepository.recordStart(
                transferId = id,
                fileName = name,
                fileSize = offer.optLong("fileSize"),
                sha256 = offer.optString("sha256"),
                sender = senderName,
                receiver = Build.MODEL,
                method = "HOTSPOT",
                direction = "RECEIVED"
            )
            transferRepository.recordFinished(id, TransferRepository.STATUS_VERIFIED)
        }
        NotificationHelper.postResult(
            context = appContext,
            id = id.hashCode(),
            title = "Received $name",
            text = "Saved to Downloads folder."
        )
    }

    fun answerPending(accept: Boolean) {
        _pending.value?.decision?.complete(accept)
    }

    fun checkpointCleanup() {
        com.example.nexshare.file.CheckpointStore(tempFiles.incomingDir).cleanOrphans()
    }

    companion object {
        const val APPROVAL_TIMEOUT_MS = 30_000L
        private const val TAG = "NexShare"
    }
}