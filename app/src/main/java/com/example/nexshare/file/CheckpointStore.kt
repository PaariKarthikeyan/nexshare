package com.example.nexshare.file

import com.example.nexshare.file.TemporaryFileManager.Companion.sanitizeFileName
import org.json.JSONObject
import java.io.File

/**
 * Stores resume state next to each .part file as <part>.checkpoint.json.
 * The checkpoint holds only the transfer ID, the next expected chunk index,
 * and the byte count. It never holds file contents.
 */
class CheckpointStore(private val incomingDir: File) {

    data class Checkpoint(val transferId: String, val nextIndex: Int, val bytesReceived: Long)

    fun fileFor(transferId: String): File {
        val safeId = transferId.filter { it.isLetterOrDigit() || it == '-' }
        return File(incomingDir, ".transfer_${safeId}.checkpoint.json")
    }

    fun save(cp: Checkpoint) {
        fileFor(cp.transferId).writeText(
            JSONObject()
                .put("transferId", cp.transferId)
                .put("nextIndex", cp.nextIndex)
                .put("bytesReceived", cp.bytesReceived)
                .toString()
        )
    }

    fun load(transferId: String): Checkpoint? {
        val f = fileFor(transferId)
        if (!f.exists()) return null
        return runCatching {
            val json = JSONObject(f.readText())
            Checkpoint(
                transferId = json.getString("transferId"),
                nextIndex = json.getInt("nextIndex"),
                bytesReceived = json.getLong("bytesReceived")
            )
        }.getOrNull()
    }

    fun delete(transferId: String) {
        fileFor(transferId).delete()
    }

    /** Removes checkpoints whose .part file no longer exists. */
    fun cleanOrphans(): Int {
        val files = incomingDir.listFiles { f -> f.name.endsWith(".checkpoint.json") } ?: return 0
        var removed = 0
        files.forEach { cp ->
            val id = cp.name.removePrefix(".transfer_").removeSuffix(".checkpoint.json")
            val hasPart = incomingDir.listFiles { f -> f.name.startsWith(".transfer_$id") && f.name.endsWith(".part") }
                ?.isNotEmpty() == true
            if (!hasPart && cp.delete()) removed++
        }
        return removed
    }
}