package com.example.nexshare.transfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One live transfer as shown on the Transfers screen. */
data class ActiveTransfer(
    val transferId: String,
    val fileName: String,
    val peerName: String,
    val direction: String,
    val bytesDone: Long,
    val bytesTotal: Long,
    val bytesPerSecond: Long,
    val state: String,
    val control: TransferControl,
    val request: (() -> Unit)? = null
) {
    val fraction: Float
        get() = if (bytesTotal <= 0L) 0f else (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
}

/** Shared list of live transfers. The Transfers screen observes it. */
object ActiveTransfers {
    private val _items = MutableStateFlow<List<ActiveTransfer>>(emptyList())
    val items: StateFlow<List<ActiveTransfer>> = _items.asStateFlow()

    fun upsert(item: ActiveTransfer) {
        _items.update { list ->
            if (list.any { it.transferId == item.transferId }) {
                list.map { if (it.transferId == item.transferId) item else it }
            } else list + item
        }
    }

    fun updateProgress(transferId: String, done: Long, speed: Long) {
        _items.update { list ->
            list.map {
                if (it.transferId == transferId) it.copy(bytesDone = done, bytesPerSecond = speed, state = "IN_PROGRESS")
                else it
            }
        }
    }

    fun setState(transferId: String, state: String) {
        _items.update { list ->
            list.map { if (it.transferId == transferId) it.copy(state = state, bytesPerSecond = 0L) else it }
        }
    }

    fun remove(transferId: String) {
        _items.update { list -> list.filterNot { it.transferId == transferId } }
    }
}