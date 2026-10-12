package com.example.nexshare.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexshare.transfer.HashedFile
import com.example.nexshare.ui.theme.*
import com.example.nexshare.util.QRCodeEncoder

@Composable
fun SendScreen(viewModel: SendViewModel, onDone: () -> Unit) {
    val files by viewModel.files.collectAsState()
    val message by viewModel.message.collectAsState()
    var showQrDialog by remember { mutableStateOf(false) }
    val deviceName = Build.MODEL.ifBlank { "NexShare Device" }

    if (showQrDialog) {
        val totalBytes = files.sumOf { it.file.sizeBytes }
        val qrJson = "{\"deviceName\":\"$deviceName\",\"fileCount\":${files.size},\"totalBytes\":$totalBytes}"
        TransferQRCodeDialog(
            qrContent = qrJson,
            deviceName = deviceName,
            onDismiss = { showQrDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MainPanel)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Send files",
                style = MaterialTheme.typography.headlineMedium,
                color = PrimaryText
            )
            Text(
                text = "Files are hashed on your device. Originals are never moved or deleted.",
                color = SecondaryText,
                fontSize = 12.sp
            )
        }

        item {
            FilePickerButtons(
                onFilesPicked = { viewModel.addFiles(it) },
                onFolderPicked = { viewModel.addFolder(it) }
            )
        }

        message?.let { text ->
            item {
                Text(text = text, color = Warning, fontSize = 12.sp)
            }
        }

        if (files.isEmpty()) {
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text("No files selected.", color = SecondaryText)
                }
            }
        } else {
            items(files, key = { it.file.uri.toString() }) { item ->
                FileCard(item = item, onRemove = { viewModel.remove(item.file.uri) })
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDone,
                        enabled = files.all { it.sha256 != null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose a device")
                    }
                    OutlinedButton(
                        onClick = { showQrDialog = true },
                        enabled = files.all { it.sha256 != null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show Transfer QR Code")
                    }
                    OutlinedButton(
                        onClick = { viewModel.clear() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear all files")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun TransferQRCodeDialog(
    qrContent: String,
    deviceName: String,
    onDismiss: () -> Unit
) {
    val bitmap = remember(qrContent) {
        QRCodeEncoder.encodeAsBitmap(qrContent, 400, 400)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer QR Code") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Receiver can scan this QR code on their device to connect and receive files from $deviceName.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText
                )
                bitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Transfer QR Code",
                        modifier = Modifier
                            .size(200.dp)
                            .padding(4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun FileCard(item: HashedFile, onRemove: () -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Avatar(name = item.file.typeLabel.take(1), diameter = 40)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.file.displayName,
                    color = PrimaryText,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${item.file.typeLabel} • ${formatBytes(item.file.sizeBytes)}",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
            }
            OutlinedButton(onClick = onRemove) { Text("Remove") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            item.hashing -> StatusBadge("Hashing", Warning)
            item.error != null -> StatusBadge("Hash failed", Danger)
            item.sha256 != null -> StatusBadge("Hash ready", Success)
        }
        item.sha256?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SHA-256: ${it.take(16)}…",
                color = SecondaryText,
                fontSize = 12.sp
            )
        }
        item.error?.let {
            Text(text = it, color = Danger, fontSize = 12.sp)
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "Unknown size"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format("%.1f %s", value, units[unit])
}
