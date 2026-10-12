package com.example.nexshare.transfer

import java.util.concurrent.atomic.AtomicBoolean

/** Shared flags for one transfer. The UI sets them; the transfer loop reads them between chunks. */
class TransferControl {
    private val cancelled = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    val isCancelled: Boolean get() = cancelled.get()
    val isPaused: Boolean get() = paused.get()

    fun cancel() {
        cancelled.set(true)
    }

    fun pause() {
        paused.set(true)
    }

    /** Clears the pause flag. Call before starting a resumed send with the same transfer ID. */
    fun resume() {
        paused.set(false)
    }
}

data class TransferProgress(
    val transferId: String,
    val fileName: String,
    val bytesDone: Long,
    val bytesTotal: Long,
    val bytesPerSecond: Long
) {
    val fraction: Float
        get() = if (bytesTotal <= 0L) 0f else (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
}

enum class TransferOutcome { VERIFIED, REJECTED, HASH_MISMATCH, CANCELLED, PAUSED, FAILED }