package com.example.nexshare.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.nexshare.AppContainer
import com.example.nexshare.ui.theme.*
import com.example.nexshare.util.QRCodeDecoder

@Composable
fun ReceiveScreen(container: AppContainer, peersViewModel: PeersViewModel? = null) {
    val pending by container.pendingRequest.collectAsState()
    val deviceName = Build.MODEL.ifBlank { "NexShare device" }
    val context = LocalContext.current

    var scanStatusMessage by remember { mutableStateOf<String?>(null) }
    var manualIpInput by remember { mutableStateOf("") }

    // Live Camera QR Scanner Launcher
    val cameraScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val rawText = QRCodeDecoder.decodeBitmap(bitmap)
            if (rawText != null) {
                val qrData = QRCodeDecoder.parseData(rawText)
                if (qrData != null) {
                    peersViewModel?.addDiscoveredPeerFromQR(qrData.deviceName, qrData.host, qrData.port)
                    peersViewModel?.sendHandshakeToSender(qrData.host, deviceName)
                    scanStatusMessage = "Paired with ${qrData.deviceName} (${qrData.host}:${qrData.port})! Both devices connected."
                } else {
                    scanStatusMessage = "Scanned QR Code: $rawText"
                }
            } else {
                scanStatusMessage = "No QR code detected in captured photo. Please align and try again."
            }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            runCatching { cameraScannerLauncher.launch(null) }
                .onFailure { scanStatusMessage = "Camera error: ${it.message}" }
        } else {
            scanStatusMessage = "Camera permission is required to scan QR codes."
        }
    }

    fun safeLaunchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            runCatching { cameraScannerLauncher.launch(null) }
                .onFailure { scanStatusMessage = "Could not open camera: ${it.message}" }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Gallery / File Image QR Launcher
    val qrPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val rawText = QRCodeDecoder.decodeBitmap(bitmap)
                        if (rawText != null) {
                            val qrData = QRCodeDecoder.parseData(rawText)
                            if (qrData != null) {
                                peersViewModel?.addDiscoveredPeerFromQR(qrData.deviceName, qrData.host, qrData.port)
                                peersViewModel?.sendHandshakeToSender(qrData.host, deviceName)
                                scanStatusMessage = "Paired with ${qrData.deviceName} (${qrData.host}:${qrData.port})! Both devices connected."
                            } else {
                                scanStatusMessage = "Scanned QR Code: $rawText"
                            }
                        } else {
                            scanStatusMessage = "No QR code detected in the selected image file."
                        }
                    }
                }
            } catch (e: Exception) {
                scanStatusMessage = "Failed to process QR image: ${e.message}"
            }
        }
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
                text = "Receive files",
                style = MaterialTheme.typography.headlineMedium,
                color = PrimaryText
            )
            Text(
                text = "Your device is ready and listening for incoming transfers.",
                color = SecondaryText,
                fontSize = 12.sp
            )
        }

        item {
            PanelCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Avatar(name = deviceName, diameter = 40)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = deviceName,
                            color = PrimaryText,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Listening on local network & Bluetooth",
                            color = SecondaryText,
                            fontSize = 12.sp
                        )
                    }
                    StatusBadge(text = "READY", color = Success)
                }
            }
        }

        item {
            PanelCard(modifier = Modifier.fillMaxWidth(), color = DarkPanel) {
                Text(
                    text = "Scan QR Code to Receive",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Scan the QR code displayed on the sender's device using your Camera or select a QR image file.",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { safeLaunchCamera() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan with Camera")
                    }
                    OutlinedButton(
                        onClick = { qrPickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select QR Image File")
                    }
                }
                scanStatusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = msg, color = PrimaryAccent, fontSize = 12.sp)
                }
            }
        }

        item {
            PanelCard(modifier = Modifier.fillMaxWidth(), color = DarkPanel) {
                Text(
                    text = "Enter IP Address to Connect",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Enter the IP address displayed on your computer or sender device to join the network and add it to Nearby Devices.",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualIpInput,
                    onValueChange = { manualIpInput = it },
                    placeholder = { Text("e.g. 192.168.43.1:8080 or 192.168.1.15", color = SecondaryText) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val clean = manualIpInput.trim().removePrefix("http://").removePrefix("https://")
                        val host = clean.substringBefore(":")
                        val port = clean.substringAfter(":", "8080").toIntOrNull() ?: 8080
                        if (host.isNotBlank()) {
                            peersViewModel?.addDiscoveredPeerFromQR("Computer/Host ($host)", host, port)
                            peersViewModel?.sendHandshakeToSender(host, deviceName)
                            scanStatusMessage = "Added Host $host:$port to Nearby Devices! Tap 'Devices' to transfer."
                            manualIpInput = ""
                        }
                    },
                    enabled = manualIpInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Join Network & Connect")
                }
            }
        }

        if (pending != null) {
            item {
                Text(
                    text = "Active Request",
                    style = MaterialTheme.typography.titleLarge,
                    color = PrimaryText
                )
            }
            item {
                pending?.let { request ->
                    PanelCard(modifier = Modifier.fillMaxWidth(), color = DarkPanel) {
                        Text(
                            text = "Incoming file from ${request.senderName}",
                            color = PrimaryText,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        InfoRow("File name", request.fileName)
                        InfoRow("Size", formatBytes(request.fileSize))
                        InfoRow("Pairing code", request.pairingCode)

                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { container.answerPending(true) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Accept Transfer")
                            }
                            OutlinedButton(
                                onClick = { container.answerPending(false) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Reject Transfer")
                            }
                            OutlinedButton(
                                onClick = {
                                    container.security.block(request.senderHost)
                                    container.answerPending(false)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Block Sender")
                            }
                        }
                    }
                }
            }
        } else {
            item {
                PanelCard(modifier = Modifier.fillMaxWidth(), color = DarkPanel) {
                    Text(
                        text = "Waiting for incoming files... Nearby devices can discover this device, or scan the QR code to initiate a transfer.",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
