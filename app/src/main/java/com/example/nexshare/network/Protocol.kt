package com.example.nexshare.network

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Binary framing for every NexShare message.
 *
 * Header (fixed 12 bytes, big-endian):
 *   MAGIC          4 bytes  "NXSH"
 *   VERSION        1 byte
 *   MESSAGE_TYPE   1 byte
 *   PAYLOAD_LENGTH 4 bytes  (unsigned, capped at MAX_PAYLOAD)
 *   reserved       2 bytes  (zero)
 * Payload: PAYLOAD_LENGTH bytes.
 */
object Protocol {

    const val VERSION: Byte = 1
    const val HEADER_SIZE = 12
    const val CHUNK_SIZE = 64 * 1024
    /** Upper bound for one frame. A chunk plus its small header fits well below this. */
    const val MAX_PAYLOAD = CHUNK_SIZE + 4096
    private val MAGIC = byteArrayOf('N'.code.toByte(), 'X'.code.toByte(), 'S'.code.toByte(), 'H'.code.toByte())
    const val SERVICE_TYPE = "_nexshare._tcp."
    const val PROTOCOL_VERSION_INT = 1
}

enum class MessageType(val code: Byte) {
    HELLO(1), PAIR_REQUEST(2), PAIR_RESPONSE(3), FILE_OFFER(4), FILE_ACCEPT(5),
    FILE_REJECT(6), FILE_CHUNK(7), CHUNK_ACK(8), PAUSE(9), RESUME(10), CANCEL(11),
    COMPLETE(12), HASH_RESULT(13), ERROR(14);

    companion object {
        fun fromCode(code: Byte): MessageType? = entries.firstOrNull { it.code == code }
    }
}

/** One decoded frame. Payload is kept as raw bytes; higher layers parse it. */
class Frame(val type: MessageType, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is Frame && other.type == type && other.payload.contentEquals(payload)

    override fun hashCode(): Int = type.hashCode() * 31 + payload.contentHashCode()
}

class ProtocolException(message: String) : IOException(message)

/** Reads and writes frames. Thread-safety is the caller's responsibility: one writer and one reader. */
class FrameCodec(
    private val input: DataInputStream,
    private val output: DataOutputStream
) {

    fun write(frame: Frame) = write(frame.type, frame.payload)

    fun write(type: MessageType, payload: ByteArray = ByteArray(0)) {
        require(payload.size <= Protocol.MAX_PAYLOAD) { "Payload too large: ${payload.size}" }
        output.write(MAGIC_BYTES)
        output.writeByte(Protocol.VERSION.toInt())
        output.writeByte(type.code.toInt())
        output.writeInt(payload.size)
        output.writeShort(0)
        output.write(payload)
        output.flush()
    }

    fun read(): Frame {
        val magic = ByteArray(4)
        input.readFully(magic)
        if (!magic.contentEquals(MAGIC_BYTES)) throw ProtocolException("Bad magic")

        val version = input.readByte()
        if (version != Protocol.VERSION) throw ProtocolException("Unsupported version $version")

        val typeCode = input.readByte()
        val type = MessageType.fromCode(typeCode)
            ?: throw ProtocolException("Unknown message type $typeCode")

        val length = input.readInt()
        if (length < 0 || length > Protocol.MAX_PAYLOAD) {
            throw ProtocolException("Invalid payload length $length")
        }
        input.readShort() // reserved

        val payload = ByteArray(length)
        input.readFully(payload)
        return Frame(type, payload)
    }

    companion object {
        private val MAGIC_BYTES = byteArrayOf(0x4E, 0x58, 0x53, 0x48) // "NXSH"
    }
}