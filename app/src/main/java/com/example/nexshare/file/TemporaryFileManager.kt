package com.example.nexshare.file

import android.content.Context
import java.io.File

class TemporaryFileManager(context: Context) {

    val incomingDir: File = File(context.cacheDir, INCOMING_DIR).apply { mkdirs() }

    fun partFileFor(transferId: String, fileName: String): File {
        val safeId = transferId.filter { it.isLetterOrDigit() || it == '-' }
        require(safeId.isNotEmpty()) { "Invalid transfer id" }
        val file = File(incomingDir, ".transfer_${safeId}_${sanitizeFileName(fileName)}.part")
        return ensureInside(file)
    }

    /** Finds the .part file for a transfer ID, if one exists. */
    fun existingPartFor(transferId: String): File? {
        val safeId = transferId.filter { it.isLetterOrDigit() || it == '-' }
        return incomingDir.listFiles { f ->
            f.name.startsWith(".transfer_${safeId}_") && f.name.endsWith(".part")
        }?.firstOrNull()
    }

    fun delete(file: File): Boolean = file.exists() && file.delete()

    fun cleanAbandoned(): Int {
        val leftovers = incomingDir.listFiles { f -> f.name.endsWith(PART_SUFFIX) } ?: return 0
        var removed = 0
        leftovers.forEach { part ->
            val id = part.name.removePrefix(".transfer_").substringBefore("_")
            val hasCheckpoint = java.io.File(incomingDir, ".transfer_${id}.checkpoint.json").exists()
            if (!hasCheckpoint && part.delete()) removed++
        }
        return removed
    }

    fun usableSpaceBytes(): Long = incomingDir.usableSpace

    companion object {
        const val INCOMING_DIR = "incoming"
        private const val PART_SUFFIX = ".part"
        private const val MAX_NAME_LENGTH = 120
        private val FORBIDDEN = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

        fun sanitizeFileName(raw: String): String {
            val base = raw.substringAfterLast('/').substringAfterLast('\\')
            var cleaned = base.replace(FORBIDDEN, "_").trim().trimStart('.')
            if (cleaned.isEmpty()) cleaned = "file"
            if (cleaned.length > MAX_NAME_LENGTH) {
                val ext = cleaned.substringAfterLast('.', "")
                val stem = cleaned.take(MAX_NAME_LENGTH - ext.length - 1)
                cleaned = if (ext.isNotEmpty()) "$stem.$ext" else stem
            }
            return cleaned
        }
    }

    private fun ensureInside(file: File): File {
        val root = incomingDir.canonicalPath + File.separator
        require(file.canonicalPath.startsWith(root)) { "Path escapes the incoming directory" }
        return file
    }
}