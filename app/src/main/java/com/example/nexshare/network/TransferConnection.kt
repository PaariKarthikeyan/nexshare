package com.example.nexshare.network

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Transport-neutral connection. TCP and Bluetooth RFCOMM both produce one of these,
 * so framing and transfer logic are written once.
 *
 * Implements Closeable so callers can use `.use { }`.
 */
class TransferConnection(
    val input: InputStream,
    val output: OutputStream,
    private val onClose: () -> Unit
) : Closeable {

    /** Creates a frame codec over this connection. Create one per connection. */
    fun codec(): FrameCodec = FrameCodec(
        DataInputStream(input.buffered()),
        DataOutputStream(output.buffered())
    )

    override fun close() {
        onClose()
    }
}