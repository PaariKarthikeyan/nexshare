package com.example.nexshare.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexshare.file.FileManager
import com.example.nexshare.security.HashManager
import com.example.nexshare.transfer.HashedFile
import com.example.nexshare.transfer.SelectedFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the files queued for sending. Hashing is started as soon as a file is added,
 * so the SHA-256 is ready by the time a peer is chosen.
 */
class SendViewModel(application: Application) : AndroidViewModel(application) {

    private val fileManager = FileManager(application)

    private val _files = MutableStateFlow<List<HashedFile>>(emptyList())
    val files: StateFlow<List<HashedFile>> = _files.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun addFiles(uris: List<Uri>) {
        uris.forEach { uri -> addOne(fileManager.describe(uri)) }
    }

    fun addFolder(treeUri: Uri) {
        val listed = fileManager.listFolder(treeUri)
        if (listed.isEmpty()) {
            _message.value = "No files found in that folder."
            return
        }
        listed.forEach { addOne(it) }
        _message.value = "Added ${listed.size} file(s) from the folder."
    }

    fun remove(uri: Uri) {
        _files.update { list -> list.filterNot { it.file.uri == uri } }
    }

    fun clear() {
        _files.value = emptyList()
        _message.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun addOne(file: SelectedFile) {
        if (_files.value.any { it.file.uri == file.uri }) return
        _files.update { it + HashedFile(file = file, hashing = true) }
        viewModelScope.launch {
            val result = runCatching {
                HashManager.sha256OfUri(getApplication(), file.uri)
            }
            _files.update { list ->
                list.map {
                    if (it.file.uri != file.uri) it
                    else result.fold(
                        onSuccess = { hash -> it.copy(sha256 = hash, hashing = false) },
                        onFailure = { e -> it.copy(hashing = false, error = e.message ?: "Hashing failed") }
                    )
                }
            }
        }
    }
}