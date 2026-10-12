package com.example.nexshare.security

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Streaming SHA-256. Reads in 64 KB blocks, so memory use stays constant
 * regardless of file size. Runs on Dispatchers.IO.
 */
object HashManager {

    const val BUFFER_SIZE = 64 * 1024

    /** Hashes an arbitrary input stream. The caller closes the stream. */
    fun sha256(input: InputStream, onBytes: (Long) -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
            total += read
            onBytes(total)
        }
        return digest.digest().toHex()
    }

    suspend fun sha256OfFile(file: File): String = withContext(Dispatchers.IO) {
        file.inputStream().buffered().use { sha256(it) }
    }

    suspend fun sha256OfUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Cannot open $uri")
        stream.buffered().use { sha256(it) }
    }

    private fun ByteArray.toHex(): String {
        val hex = CharArray(size * 2)
        val digits = "0123456789abcdef"
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            hex[i * 2] = digits[v ushr 4]
            hex[i * 2 + 1] = digits[v and 0x0F]
        }
        return String(hex)
    }
}