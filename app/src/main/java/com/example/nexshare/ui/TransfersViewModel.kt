package com.example.nexshare.ui

import androidx.lifecycle.ViewModel
import com.example.nexshare.transfer.ActiveTransfer
import com.example.nexshare.transfer.ActiveTransfers
import kotlinx.coroutines.flow.StateFlow

class TransfersViewModel : ViewModel() {

    val transfers: StateFlow<List<ActiveTransfer>> = ActiveTransfers.items

    fun pause(item: ActiveTransfer) = item.control.pause()

    fun resume(item: ActiveTransfer) {
        item.control.resume()
        ActiveTransfers.setState(item.transferId, "IN_PROGRESS")
    }

    fun cancel(item: ActiveTransfer) = item.control.cancel()

    /** Retry reuses the transfer ID so the receiver's checkpoint lets it continue. */
    fun retry(item: ActiveTransfer, onRetry: (ActiveTransfer) -> Unit) = onRetry(item)
}