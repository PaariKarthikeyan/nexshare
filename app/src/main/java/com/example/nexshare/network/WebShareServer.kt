package com.example.nexshare.network

import android.content.Context
import android.util.Log
import com.example.nexshare.file.FileManager
import com.example.nexshare.transfer.SelectedFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WebShareServer(
    private val context: Context,
    private val fileManager: FileManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    var sharedFiles: List<SelectedFile> = emptyList()

    val port: Int = 8080

    fun start(): String? {
        stop()
        val ip = getLocalIpv4() ?: return null
        return try {
            val server = ServerSocket(port)
            serverSocket = server
            scope.launch {
                while (!server.isClosed) {
                    val client = try {
                        server.accept()
                    } catch (e: Exception) {
                        break
                    }
                    scope.launch { handleClient(client) }
                }
            }
            "http://$ip:$port"
        } catch (e: Exception) {
            Log.e("WebShareServer", "Failed to start web server", e)
            null
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30000 // 30s timeout for reading request headers
            val input = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val output = socket.getOutputStream()

            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]

            if (method == "GET" && (path == "/" || path.startsWith("/?"))) {
                serveIndex(output)
            } else if (method == "GET" && path.startsWith("/download")) {
                val indexStr = path.substringAfter("id=", "0").substringBefore("&")
                val index = indexStr.toIntOrNull() ?: 0
                if (index in sharedFiles.indices) {
                    serveFile(socket, output, sharedFiles[index])
                } else {
                    serve404(output)
                }
            } else if (method == "POST" && path.startsWith("/upload")) {
                serveUpload(input, socket.getInputStream(), output)
            } else {
                serve404(output)
            }
        } catch (e: Exception) {
            Log.e("WebShareServer", "Client handler error: ${e.message}")
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun serveIndex(output: OutputStream) {
        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'>")
        html.append("<title>NexShare Web Transfer</title>")
        html.append("<style>")
        html.append("body{font-family:sans-serif;background:#0F1117;color:#F8FAFC;padding:20px;max-width:600px;margin:0 auto;}")
        html.append("h1{color:#6366F1;} .card{background:#20232B;border-radius:12px;padding:16px;margin-bottom:16px;border:1px solid #323745;}")
        html.append("a.btn, button{display:inline-block;background:#6366F1;color:#fff;padding:10px 16px;text-decoration:none;border-radius:8px;border:none;cursor:pointer;font-weight:bold;}")
        html.append("input[type=file]{margin-bottom:12px;color:#94A3B8;}")
        html.append("</style></head><body>")
        html.append("<h1>NexShare Cross-Platform Transfer</h1>")
        html.append("<p style='color:#94A3B8;'>Direct transfer between Android and PC / Mac / Browser.</p>")

        html.append("<div class='card'><h2>Download Shared Files</h2>")
        if (sharedFiles.isEmpty()) {
            html.append("<p style='color:#94A3B8;'>No files currently shared from Android.</p>")
        } else {
            sharedFiles.forEachIndexed { idx, file ->
                html.append("<div style='margin-bottom:12px;'>")
                html.append("<strong>${file.displayName}</strong> (${formatBytes(file.sizeBytes)})<br><br>")
                html.append("<a class='btn' href='/download?id=$idx'>Download File</a>")
                html.append("</div><hr style='border-color:#323745;'>")
            }
        }
        html.append("</div>")

        html.append("<div class='card'><h2>Upload File to Android</h2>")
        html.append("<form action='/upload' method='POST' enctype='multipart/form-data'>")
        html.append("<input type='file' name='file' required><br>")
        html.append("<button type='submit'>Upload to Android Downloads</button>")
        html.append("</form></div>")

        html.append("</body></html>")

        val bytes = html.toString().toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun serveFile(socket: Socket, output: OutputStream, file: SelectedFile) {
        // Clear socket timeout so large file downloads are never interrupted
        runCatching { socket.soTimeout = 0 }

        val stream = try {
            fileManager.openInput(file.uri)
        } catch (e: Exception) {
            null
        }
        if (stream == null) {
            serve404(output)
            return
        }

        try {
            val safeName = file.displayName.replace("\"", "\\\"").replace("\r", "").replace("\n", "")
            val encodedName = URLEncoder.encode(file.displayName, "UTF-8").replace("+", "%20")

            val headers = StringBuilder()
                .append("HTTP/1.1 200 OK\r\n")
                .append("Content-Type: ").append(file.mimeType.ifBlank { "application/octet-stream" }).append("\r\n")
            if (file.sizeBytes > 0) {
                headers.append("Content-Length: ").append(file.sizeBytes).append("\r\n")
            }
            headers.append("Content-Disposition: attachment; filename=\"").append(safeName).append("\"; filename*=UTF-8''").append(encodedName).append("\r\n")
                .append("Accept-Ranges: bytes\r\n")
                .append("Connection: close\r\n\r\n")

            output.write(headers.toString().toByteArray(Charsets.UTF_8))
            output.flush()

            stream.use { input ->
                val buffer = ByteArray(128 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        } catch (e: Exception) {
            Log.e("WebShareServer", "Error streaming file ${file.displayName}: ${e.message}")
        }
    }

    private fun serveUpload(reader: BufferedReader, rawInput: java.io.InputStream, output: OutputStream) {
        val html = "<!DOCTYPE html><html><head><title>Uploaded</title></head><body style='background:#0F1117;color:#F8FAFC;padding:20px;font-family:sans-serif;'><h2>Upload Request Received!</h2><p><a href='/' style='color:#6366F1;'>Back to NexShare Web Transfer</a></p></body></html>"
        val bytes = html.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun serve404(output: OutputStream) {
        val body = "404 Not Found"
        val headers = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n"
        output.write((headers + body).toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun getLocalIpv4(): String? =
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "Unknown"
        val units = listOf("B", "KB", "MB", "GB")
        var v = bytes.toDouble()
        var u = 0
        while (v >= 1024 && u < units.lastIndex) {
            v /= 1024
            u++
        }
        return String.format("%.1f %s", v, units[u])
    }
}
