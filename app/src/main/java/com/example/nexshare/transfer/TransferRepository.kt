package com.example.nexshare.transfer

import com.example.nexshare.data.AppDatabase
import com.example.nexshare.data.PeerEntity
import com.example.nexshare.data.TransferEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single entry point for metadata. Callers pass metadata only; this class never
 * accepts or returns file bytes.
 */
class TransferRepository(private val db: AppDatabase) {

    private val transfers = db.transferDao()
    private val peers = db.peerDao()

    fun observeTransfers(): Flow<List<TransferEntity>> = transfers.observeAll()

    fun observeActive(): Flow<List<TransferEntity>> =
        transfers.observeByStatus(listOf(STATUS_IN_PROGRESS, STATUS_PAUSED))

    fun observeCompletedCount(): Flow<Int> =
        transfers.observeAll().map { list -> list.count { it.status == STATUS_VERIFIED } }

    suspend fun recordStart(
        transferId: String,
        fileName: String,
        fileSize: Long,
        sha256: String,
        sender: String,
        receiver: String,
        method: String,
        direction: String
    ) {
        val now = System.currentTimeMillis()
        transfers.upsert(
            TransferEntity(
                transferId = transferId,
                fileName = fileName,
                fileSize = fileSize,
                sha256 = sha256,
                sender = sender,
                receiver = receiver,
                method = method,
                direction = direction,
                status = STATUS_IN_PROGRESS,
                speedBytesPerSecond = 0L,
                createdAt = now,
                updatedAt = now,
                completedAt = null
            )
        )
    }

    suspend fun recordProgress(transferId: String, speed: Long) {
        transfers.updateStatus(transferId, STATUS_IN_PROGRESS, speed, System.currentTimeMillis())
    }

    suspend fun recordPaused(transferId: String) {
        transfers.updateStatus(transferId, STATUS_PAUSED, 0L, System.currentTimeMillis())
    }

    suspend fun recordFinished(transferId: String, status: String) {
        transfers.markFinished(transferId, status, System.currentTimeMillis())
    }

    suspend fun clearHistory() = transfers.clearAll()

    suspend fun upsertPeer(
        peerId: String,
        deviceName: String,
        host: String,
        port: Int,
        method: String
    ) {
        val existing = peers.get(peerId)
        peers.upsert(
            PeerEntity(
                peerId = peerId,
                deviceName = deviceName,
                host = host,
                port = port,
                method = method,
                trusted = existing?.trusted ?: false,
                lastSeen = System.currentTimeMillis()
            )
        )
    }

    fun observePeers(): Flow<List<PeerEntity>> = peers.observeAll()

    suspend fun setTrusted(peerId: String, trusted: Boolean) = peers.setTrusted(peerId, trusted)

    suspend fun removePeer(peerId: String) = peers.delete(peerId)

    suspend fun clearUntrustedPeers() = peers.clearUntrusted()

    companion object {
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_VERIFIED = "VERIFIED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_HASH_MISMATCH = "HASH_MISMATCH"
    }
}