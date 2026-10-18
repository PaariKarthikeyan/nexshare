package com.example.nexshare.transfer

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.example.nexshare.network.BluetoothTransferManager
import com.example.nexshare.network.TcpTransferManager
import com.example.nexshare.notification.NotificationHelper
import com.example.nexshare.transfer.NexShareTransferService.Companion.ControlRegistry
import com.example.nexshare.transfer.TransferRepository.Companion.STATUS_CANCELLED
import com.example.nexshare.transfer.TransferRepository.Companion.STATUS_FAILED
import com.example.nexshare.transfer.TransferRepository.Companion.STATUS_HASH_MISMATCH
import com.example.nexshare.transfer.TransferRepository.Companion.STATUS_PAUSED
import com.example.nexshare.transfer.TransferRepository.Companion.STATUS_VERIFIED
import java.util.UUID

class TransferService(
    private val context: Context,
    private val tcpManager: TcpTransferManager,
    private val bluetoothManager: BluetoothTransferManager,
    private val repository: TransferRepository,
    private val deviceName: String
) {

    suspend fun sendFile(
        peer: Peer,
        file: SelectedFile,
        sha256: String,
        control: TransferControl,
        transferId: String = UUID.randomUUID().toString()
    ): TransferOutcome {
        repository.upsertPeer(peer.peerId, peer.deviceName, peer.host, peer.port, peer.method.name)
        repository.recordStart(
            transferId = transferId,
            fileName = file.displayName,
            fileSize = file.sizeBytes,
            sha256 = sha256,
            sender = deviceName,
            receiver = peer.deviceName,
            method = peer.method.name,
            direction = DIRECTION_SENT
        )
        ActiveTransfers.upsert(
            ActiveTransfer(
                transferId = transferId,
                fileName = file.displayName,
                peerName = peer.deviceName,
                direction = DIRECTION_SENT,
                bytesDone = 0L,
                bytesTotal = file.sizeBytes,
                bytesPerSecond = 0L,
                state = "STARTING",
                control = control
            )
        )
        NexShareTransferService.start(context, transferId, file.displayName)
        ControlRegistry.put(transferId, control)

        val outcome = try {
            if (peer.method == ConnectionMethod.BLUETOOTH) {
                bluetoothManager.send(
                    address = peer.host,
                    deviceName = deviceName,
                    file = file,
                    sha256 = sha256,
                    transferId = transferId,
                    control = control,
                    onProgress = { p ->
                        ActiveTransfers.updateProgress(transferId, p.bytesDone, p.bytesPerSecond)
                        val notif = NotificationHelper.buildProgress(
                            context = context,
                            fileName = file.displayName,
                            percent = (p.fraction * 100).toInt(),
                            speedText = formatSpeed(p.bytesPerSecond),
                            paused = false
                        )
                        runCatching {
                            NotificationManagerCompat.from(context)
                                .notify(NotificationHelper.TRANSFER_NOTIFICATION_ID, notif)
                        }
                    }
                )
            } else {
                tcpManager.send(
                    host = peer.host,
                    port = peer.port,
                    deviceName = deviceName,
                    file = file,
                    sha256 = sha256,
                    transferId = transferId,
                    control = control,
                    onProgress = { p ->
                        ActiveTransfers.updateProgress(transferId, p.bytesDone, p.bytesPerSecond)
                        val notif = NotificationHelper.buildProgress(
                            context = context,
                            fileName = file.displayName,
                            percent = (p.fraction * 100).toInt(),
                            speedText = formatSpeed(p.bytesPerSecond),
                            paused = false
                        )
                        runCatching {
                            NotificationManagerCompat.from(context)
                                .notify(NotificationHelper.TRANSFER_NOTIFICATION_ID, notif)
                        }
                    }
                )
            }
        } catch (e: Exception) {
            TransferOutcome.FAILED
        } finally {
            ControlRegistry.remove(transferId)
        }

        val status = when (outcome) {
            TransferOutcome.VERIFIED -> STATUS_VERIFIED
            TransferOutcome.PAUSED -> STATUS_PAUSED
            TransferOutcome.CANCELLED -> STATUS_CANCELLED
            TransferOutcome.HASH_MISMATCH -> STATUS_HASH_MISMATCH
            TransferOutcome.REJECTED, TransferOutcome.FAILED -> STATUS_FAILED
        }
        ActiveTransfers.setState(transferId, status)
        if (status == STATUS_PAUSED) {
            repository.recordPaused(transferId)
        } else {
            repository.recordFinished(transferId, status)
            ActiveTransfers.remove(transferId)
            val resultMessage = if (outcome == TransferOutcome.FAILED && peer.method == ConnectionMethod.BLUETOOTH) {
                "${file.displayName} (Device is not currently running NexShare)"
            } else {
                file.displayName
            }
            NotificationHelper.postResult(
                context = context,
                id = transferId.hashCode(),
                title = resultTitle(status),
                text = resultMessage
            )
            NexShareTransferService.stop(context)
        }
        return outcome
    }

    private fun resultTitle(status: String): String = when (status) {
        STATUS_VERIFIED -> "Sent and verified"
        STATUS_CANCELLED -> "Transfer cancelled"
        STATUS_HASH_MISMATCH -> "Verification failed"
        else -> "Transfer failed"
    }

    private fun formatSpeed(bytesPerSecond: Long): String = when {
        bytesPerSecond >= 1_000_000L -> String.format("%.1f MB/s", bytesPerSecond / 1_000_000.0)
        bytesPerSecond >= 1_000L -> String.format("%.0f KB/s", bytesPerSecond / 1_000.0)
        else -> "$bytesPerSecond B/s"
    }

    companion object {
        const val DIRECTION_SENT = "SENT"
        const val DIRECTION_RECEIVED = "RECEIVED"
    }
}
