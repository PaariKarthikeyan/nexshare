package com.example.nexshare

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nexshare.file.FileManager
import com.example.nexshare.file.TemporaryFileManager
import com.example.nexshare.network.TcpTransferManager
import com.example.nexshare.security.HashManager
import com.example.nexshare.transfer.SelectedFile
import com.example.nexshare.transfer.TransferControl
import com.example.nexshare.transfer.TransferOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class LoopbackTransferTest {

    private fun fixture(ctx: android.content.Context): Pair<File, String> {
        val source = File(ctx.cacheDir, "loopback_source.bin")
            .apply { writeBytes(Random.nextBytes(300_000)) }
        val hash = source.inputStream().buffered().use { HashManager.sha256(it) }
        return source to hash
    }

    private fun selected(source: File) = SelectedFile(
        uri = Uri.fromFile(source),
        displayName = source.name,
        sizeBytes = source.length(),
        mimeType = "application/octet-stream"
    )

    @Test
    fun sendsFileOverLoopbackAndVerifiesHash() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val (source, hash) = fixture(ctx)
        val temp = TemporaryFileManager(ctx)
        var received: File? = null
        val receiver = TcpTransferManager(
            fileManager = FileManager(ctx),
            tempFiles = temp,
            approve = { _, _, _ -> true },
            onReceived = { part, _, _ -> received = part }
        )
        val port = receiver.startServer("Receiver")
        val sender = TcpTransferManager(FileManager(ctx), temp)

        val outcome = sender.send("127.0.0.1", port, "Sender", selected(source), hash)
        receiver.stopServer()

        assertEquals(TransferOutcome.VERIFIED, outcome)
        val part = received ?: error("Receiver did not produce a file")
        assertEquals(source.length(), part.length())
        assertEquals(hash, part.inputStream().buffered().use { HashManager.sha256(it) })
        part.delete()
        source.delete()
    }

    @Test
    fun cancelRemovesPartialFile() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val (source, hash) = fixture(ctx)
        val temp = TemporaryFileManager(ctx)
        val receiver = TcpTransferManager(
            fileManager = FileManager(ctx),
            tempFiles = temp,
            approve = { _, _, _ -> true }
        )
        val port = receiver.startServer("Receiver")
        val sender = TcpTransferManager(FileManager(ctx), temp)
        val control = TransferControl()

        // Cancel as soon as the first chunk is acknowledged.
        val outcome = sender.send(
            "127.0.0.1", port, "Sender", selected(source), hash, control = control
        ) { progress -> if (progress.bytesDone > 0) control.cancel() }

        Thread.sleep(300)
        receiver.stopServer()

        assertEquals(TransferOutcome.CANCELLED, outcome)
        assertFalse(temp.incomingDir.listFiles().orEmpty().any { it.name.endsWith(".part") })
        source.delete()
    }

    @Test
    fun pauseThenResumeCompletesWithCorrectHash() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val (source, hash) = fixture(ctx)
        val temp = TemporaryFileManager(ctx)
        var received: File? = null
        val receiver = TcpTransferManager(
            fileManager = FileManager(ctx),
            tempFiles = temp,
            approve = { _, _, _ -> true },
            onReceived = { part, _, _ -> received = part }
        )
        val port = receiver.startServer("Receiver")
        val sender = TcpTransferManager(FileManager(ctx), temp)
        val id = java.util.UUID.randomUUID().toString()
        val control = TransferControl()

        val first = sender.send("127.0.0.1", port, "Sender", selected(source), hash, id, control) { p ->
            if (p.bytesDone > 0) control.pause()
        }
        assertEquals(TransferOutcome.PAUSED, first)
        Thread.sleep(200)

        control.resume()
        val second = sender.send("127.0.0.1", port, "Sender", selected(source), hash, id, control)
        receiver.stopServer()

        assertEquals(TransferOutcome.VERIFIED, second)
        val part = received ?: error("No file received after resume")
        assertEquals(hash, part.inputStream().buffered().use { HashManager.sha256(it) })
        part.delete()
        source.delete()
    }
}
