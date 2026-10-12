package com.example.nexshare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexshare.transfer.TransferRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.nexshare.data.TransferEntity

class HistoryViewModel(private val repository: TransferRepository) : ViewModel() {

    val history = repository.observeTransfers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<TransferEntity>())

    fun clear() {
        viewModelScope.launch { repository.clearHistory() }
    }
}