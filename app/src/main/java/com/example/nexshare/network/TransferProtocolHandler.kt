package com.example.nexshare.network

import com.example.nexshare.file.CheckpointStore
import com.example.nexshare.file.FileManager
import com.example.nexshare.file.TemporaryFileManager
import com.example.nexshare.security.HashManager
import com.example.nexshare.security.SecurityPolicy
import com.example.nexshare.transfer.SelectedFile
import com.example.nexshare.transfer.TransferControl
import com.example.nexshare.transfer.TransferOutcome
import com.example.nexshare.transfer.TransferProgress
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import org.json.JSONObject

class TransferProtocolHandler(
    private val fileManager: FileManager,
    private val tempFiles: TemporaryFileManager,
    private val security: SecurityPolicy,
    private val onLog: (String) -> Unit = {},
    private val approve: (senderKey: String, senderName: String, offer: JSONObject) -> Boolean = { _, _, _ -> false },
    private val onReceived: (partFile: File, senderName: String, offer: JSONObject) -> Unit = { _, _, _ -> }
) {

    private val checkpoints = CheckpointStore(tempFiles.incomingDir)

    fun send(
        conn: TransferConnection,
        deviceName: String,
        file: SelectedFile,
        sha256: String,
        transferId: String = UUID.randomUUID().toString(),
        control: TransferControl = TransferControl(),
        onProgress: (TransferProgress) -> Unit = {}
    ): TransferOutcome {
        return try {
            val codec = conn.codec()
            codec.write(MessageType.HELLO, helloPayload(deviceName))
            val hello = codec.read()
            require(hello.type == MessageType.HELLO) { "Expected HELLO" }

            val offer = JSONObject()
                .put("transferId", transferId)
                .put("fileName", file.displayName)
                .put("fileSize", file.sizeBytes)
                .put("sha256", sha256)
                .put("mimeType", file.mimeType)
                .put("senderName", deviceName)
                .put("chunkSize", Protocol.CHUNK_SIZE)
            codec.write(MessageType.FILE_OFFER, offer.toString().toByteArray(Charsets.UTF_8))

            val reply = codec.read()
            if (reply.type != MessageType.FILE_ACCEPT) {
                onLog("Receiver declined: ${reply.type}")
                return TransferOutcome.REJECTED
            }
            val payloadBytes = if (reply.payload.isEmpty()) "{}".toByteArray() else reply.payload
            val resume = JSONObject(String(payloadBytes, Charsets.UTF_8))
            val startIndex = resume.optInt("nextIndex", 0)
            val startBytes = resume.optLong("bytesReceived", 0L)

            val input = fileManager.openInput(file.uri) ?: error("Cannot open source file")
            input.use { stream ->
                skipExactly(stream, startBytes)
                sendChunks(codec, stream, transferId, file, startIndex, startBytes, control, onProgress)?.let {
                    return it
                }
            }

            codec.write(MessageType.COMPLETE, idPayload(transferId))
            val result = codec.read()
            if (result.type != MessageType.HASH_RESULT) return TransferOutcome.FAILED
            val json = JSONObject(String(result.payload, Charsets.UTF_8))
            when {
                json.optBoolean("ok", false) -> TransferOutcome.VERIFIED
                json.optString("reason") == "hash mismatch" -> TransferOutcome.HASH_MISMATCH
                else -> TransferOutcome.FAILED
            }
        } catch (e: Exception) {
            onLog("Send failed: ${e.message}")
            TransferOutcome.FAILED
        }
    }

    private fun sendChunks(
        codec: FrameCodec,
        stream: InputStream,
        transferId: String,
        file: SelectedFile,
        startIndex: Int,
        startBytes: Long,
        control: TransferControl,
        onProgress: (TransferProgress) -> Unit
    ): TransferOutcome? {
        val buffer = ByteArray(Protocol.CHUNK_SIZE)
        var sent = startBytes
        var index = startIndex
        var windowStart = System.currentTimeMillis()
        var windowBytes = 0L
        while (true) {
            if (control.isCancelled) {
                codec.write(MessageType.CANCEL, idPayload(transferId))
                return TransferOutcome.CANCELLED
            }
            if (control.isPaused) {
                codec.write(MessageType.PAUSE, idPayload(transferId))
                return TransferOutcome.PAUSED
            }
            val read = stream.read(buffer)
            if (read == -1) return null

            val header = JSONObject()
                .put("transferId", transferId)
                .put("index", index)
                .toString()
                .toByteArray(Charsets.UTF_8)
            codec.write(MessageType.FILE_CHUNK, chunkPayload(header, buffer, read))

            val ack = codec.read()
            if (ack.type != MessageType.CHUNK_ACK) error("Expected CHUNK_ACK, got ${ack.type}")

            sent += read
            windowBytes += read
            index++
            val now = System.currentTimeMillis()
            val elapsed = now - windowStart
            val speed = if (elapsed > 0) windowBytes * 1000L / elapsed else 0L
            if (elapsed >= 1000L) {
                windowStart = now
                windowBytes = 0L
            }
            onProgress(TransferProgress(transferId, file.displayName, sent, file.sizeBytes, speed))
        }
    }

    fun receive(conn: TransferConnection, senderKey: String, deviceName: String) {
        val codec = conn.codec()
        try {
            receiveSession(codec, senderKey, deviceName)
        } catch (e: Exception) {
            onLog("Inbound transfer failed: ${e.message}")
            runCatching { codec.write(MessageType.ERROR, (e.message ?: "error").toByteArray()) }
        }
    }

    private fun receiveSession(codec: FrameCodec, senderKey: String, deviceName: String) {
        val hello = codec.read()
        if (hello.type != MessageType.HELLO) {
            codec.write(MessageType.ERROR, "Expected HELLO".toByteArray())
            return
        }
        val senderName = runCatching {
            JSONObject(String(hello.payload, Charsets.UTF_8)).optString("deviceName", senderKey)
        }.getOrDefault(senderKey)
        codec.write(MessageType.HELLO, helloPayload(deviceName))

        val offerFrame = codec.read()
        if (offerFrame.type != MessageType.FILE_OFFER) {
            codec.write(MessageType.ERROR, "Expected FILE_OFFER".toByteArray())
            return
        }
        val offer = JSONObject(String(offerFrame.payload, Charsets.UTF_8))
        val fileName = security.sanitizeName(offer.optString("fileName"))
        offer.put("fileName", fileName)
        val fileSize = offer.optLong("fileSize", -1L)
        val transferId = offer.optString("transferId")
        val expectedHash = offer.optString("sha256")
        onLog("Offer from $senderName ($senderKey): $fileName ($fileSize bytes)")

        // Policy runs first: blocked senders and rate-limited senders never reach the user.
        security.checkOffer(senderKey, offer)?.let { reason ->
            codec.write(MessageType.FILE_REJECT, reason.toByteArray(Charsets.UTF_8))
            return
        }
        if (!approve(senderKey, senderName, offer)) {
            codec.write(MessageType.FILE_REJECT, "Declined by receiver".toByteArray())
            return
        }

        val saved = checkpoints.load(transferId)
        val existing = tempFiles.existingPartFor(transferId)
        val resuming = saved != null && existing != null && existing.length() == saved.bytesReceived

        if (!resuming && tempFiles.usableSpaceBytes() < fileSize + STORAGE_MARGIN_BYTES) {
            codec.write(MessageType.FILE_REJECT, "Insufficient storage".toByteArray())
            return
        }

        val part = if (resuming) existing!! else tempFiles.partFileFor(transferId, fileName)
        var received = if (resuming) saved!!.bytesReceived else 0L
        var expectedIndex = if (resuming) saved!!.nextIndex else 0

        codec.write(
            MessageType.FILE_ACCEPT,
            JSONObject()
                .put("nextIndex", expectedIndex)
                .put("bytesReceived", received)
                .toString()
                .toByteArray(Charsets.UTF_8)
        )

        var completed = false
        var paused = false
        try {
            FileOutputStream(part, resuming).use { out ->
                while (!completed && !paused) {
                    val frame = codec.read()
                    when (frame.type) {
                        MessageType.FILE_CHUNK -> {
                            val chunk = parseChunk(frame.payload, transferId)
                            if (chunk.index != expectedIndex) {
                                throw ProtocolException("Chunk ${chunk.index} out of order, expected $expectedIndex")
                            }
                            if (received + chunk.dataLength > fileSize) {
                                throw ProtocolException("Chunk exceeds offered file size")
                            }
                            out.write(chunk.data, chunk.dataOffset, chunk.dataLength)
                            out.flush()
                            received += chunk.dataLength
                            expectedIndex++
                            checkpoints.save(CheckpointStore.Checkpoint(transferId, expectedIndex, received))
                            codec.write(
                                MessageType.CHUNK_ACK,
                                JSONObject()
                                    .put("transferId", transferId)
                                    .put("index", chunk.index)
                                    .toString()
                                    .toByteArray(Charsets.UTF_8)
                            )
                        }
                        MessageType.COMPLETE -> completed = true
                        MessageType.PAUSE -> {
                            onLog("Sender paused $fileName at $received bytes")
                            paused = true
                        }
                        MessageType.CANCEL -> {
                            onLog("Sender cancelled $fileName")
                            cleanup(part, transferId)
                            return
                        }
                        else -> throw ProtocolException("Unexpected ${frame.type} during transfer")
                    }
                }
            }
        } catch (e: Exception) {
            onLog("Transfer interrupted for $fileName: ${e.message}")
            return
        }

        if (paused) return

        if (received != fileSize) {
            cleanup(part, transferId)
            codec.write(hashResult(false, reason = "size mismatch"))
            return
        }

        val actualHash = part.inputStream().buffered().use { HashManager.sha256(it) }
        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
            cleanup(part, transferId)
            codec.write(hashResult(false, reason = "hash mismatch"))
            onLog("Hash mismatch for $fileName")
            return
        }

        checkpoints.delete(transferId)
        onReceived(part, senderName, offer)
        codec.write(hashResult(true, sha256 = actualHash))
        onLog("Verified $fileName")
    }

    private fun cleanup(part: File, transferId: String) {
        part.delete()
        checkpoints.delete(transferId)
    }

    private fun skipExactly(stream: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) error("Source file is shorter than the resume offset")
            remaining -= skipped
        }
    }

    private fun parseChunk(payload: ByteArray, expectedTransferId: String): ChunkFrame {
        if (payload.size < 4) throw ProtocolException("Chunk too short")
        val headerLength =
            ((payload[0].toInt() and 0xFF) shl 24) or
                    ((payload[1].toInt() and 0xFF) shl 16) or
                    ((payload[2].toInt() and 0xFF) shl 8) or
                    (payload[3].toInt() and 0xFF)
        if (headerLength < 0 || headerLength > payload.size - 4) {
            throw ProtocolException("Invalid chunk header length")
        }
        val header = JSONObject(String(payload, 4, headerLength, Charsets.UTF_8))
        if (header.optString("transferId") != expectedTransferId) {
            throw ProtocolException("Chunk belongs to another transfer")
        }
        val dataOffset = 4 + headerLength
        val dataLength = payload.size - dataOffset
        if (dataLength <= 0) throw ProtocolException("Empty chunk data")
        return ChunkFrame(header.optInt("index", -1), payload, dataOffset, dataLength)
    }

    private class ChunkFrame(
        val index: Int,
        val data: ByteArray,
        val dataOffset: Int,
        val dataLength: Int
    )

    private fun hashResult(ok: Boolean, sha256: String? = null, reason: String? = null): Frame {
        val json = JSONObject().put("ok", ok)
        sha256?.let { json.put("sha256", it) }
        reason?.let { json.put("reason", it) }
        return Frame(MessageType.HASH_RESULT, json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun helloPayload(deviceName: String): ByteArray =
        JSONObject()
            .put("deviceName", deviceName)
            .put("protocolVersion", Protocol.PROTOCOL_VERSION_INT)
            .toString()
            .toByteArray(Charsets.UTF_8)

    private fun idPayload(transferId: String): ByteArray =
        JSONObject().put("transferId", transferId).toString().toByteArray(Charsets.UTF_8)

    private fun chunkPayload(header: ByteArray, data: ByteArray, length: Int): ByteArray {
        val out = ByteArray(4 + header.size + length)
        val h = header.size
        out[0] = (h ushr 24).toByte()
        out[1] = (h ushr 16).toByte()
        out[2] = (h ushr 8).toByte()
        out[3] = h.toByte()
        System.arraycopy(header, 0, out, 4, h)
        System.arraycopy(data, 0, out, 4 + h, length)
        return out
    }

    companion object {
        private const val STORAGE_MARGIN_BYTES = 64L * 1024 * 1024
    }
}