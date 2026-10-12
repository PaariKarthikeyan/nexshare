package com.example.nexshare.transfer
import android.net.Uri

enum class ConnectionMethod { HOTSPOT, BLUETOOTH }

enum class PeerStatus { ONLINE, OFFLINE }

data class Peer(
    val peerId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val method: ConnectionMethod,
    val status: PeerStatus,
    val trusted: Boolean,
    val lastSeenMillis: Long
)

enum class HotspotStatus { STOPPED, STARTING, RUNNING, ERROR }

data class HotspotUiState(
    val status: HotspotStatus = HotspotStatus.STOPPED,
    val ssid: String? = null,
    val password: String? = null,
    val localIp: String? = null,
    val port: Int = 0,
    val errorMessage: String? = null
)

enum class BluetoothAvailability { UNAVAILABLE, DISABLED, ENABLED }

data class BluetoothDeviceUi(
    val address: String,
    val name: String
)

data class BluetoothUiState(
    val availability: BluetoothAvailability = BluetoothAvailability.UNAVAILABLE,
    val permissionGranted: Boolean = false,
    val pairedDevices: List<BluetoothDeviceUi> = emptyList(),
    val message: String? = null
)

/** A file the user picked, with metadata read from the content resolver. */
data class SelectedFile(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String
) {
    val typeLabel: String
        get() = displayName.substringAfterLast('.', "").uppercase().ifEmpty { "FILE" }
}

/** Hash state for one selected file. */
data class HashedFile(
    val file: SelectedFile,
    val sha256: String? = null,
    val hashing: Boolean = false,
    val error: String? = null
)

data class PendingRequest(
    val senderHost: String,
    val senderName: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val sha256: String,
    val pairingCode: String,
    val decision: kotlinx.coroutines.CompletableDeferred<Boolean>
)