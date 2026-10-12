# NexShare v1.0

**NexShare** is a fast, secure, and modern Android application for direct device-to-device file transfer. Built with Kotlin, Jetpack Compose, Coroutines, and binary framing sockets, NexShare requires no central cloud servers and guarantees direct, high-speed, local data transfers.

---

## Key Features (v1.0)

### 1. Instant Multi-Protocol Device Discovery
* **Ultra-Fast UDP Subnet Broadcast**: Broadcasts high-speed ping packets (`NXSH_PING` on port 5002) across the local Wi-Fi / Hotspot network for sub-100ms peer discovery.
* **Network Service Discovery (mDNS / DNS-SD)**: Registers and resolves local network services (`_nexshare._tcp.`) via Android's `NsdManager`.
* **Active & Bonded Bluetooth Scanning**: Actively scans for all connectable nearby Bluetooth devices (`BluetoothAdapter.startDiscovery`) and instantly populates bonded devices (< 50ms).

### 2. QR Code Transfer & Camera Scanner
* **Sender QR Code Generation**: Generates dynamic QR codes containing network credentials (SSID, Password, IP, Port) for instant peer pairing.
* **Receiver Camera Scanner**: Live camera preview scanning (using CameraX and ZXing) allowing receivers to point their camera at the sender's screen to connect and accept transfers automatically.
* **Image Gallery Picker**: Fallback option to pick and scan QR code image files from the device gallery.

### 3. Smart Automatic Transfer Protocol
* **Automated Connection Selection**: Automatically selects **Bluetooth RFCOMM** for small files (< 5 MB) and high-speed **TCP/Hotspot streaming** for large files (≥ 5 MB).
* **Direct Download Storage**: Successfully received files are automatically saved to the device's public **Downloads** directory (`Environment.DIRECTORY_DOWNLOADS`).
* **Binary Framing & Chunking**: Custom binary protocol header (`NXSH`) with 64 KB chunking, sequence checking, pause/resume checkpointing, and cancellation support.
* **SHA-256 Verification**: Calculates and verifies SHA-256 hashes end-to-end to ensure 100% data integrity.

### 4. Security & Safety Rules
* **Security Policy**: Rate-limiting (max 6 requests/min per peer), blocked dangerous file extensions (`.apk`, `.exe`, `.bat`, `.sh`, etc.), and size checks before any bytes are written.
* **Pairing Codes**: Random 6-digit verification pairing codes for inbound transfers.
* **Peer Trust & Blocking**: Ability to trust, revoke, or permanently block specific peer devices.

### 5. Sleek, Modern Jetpack Compose UI
* **Cohesive Dark Palette**: Polished dark slate theme (`#0F1117` background, `#181A20` panels, `#20232B` cards, `#6366F1` indigo primary accent).
* **Vertical Stacked Controls**: Clean, full-width action buttons stacked vertically below content cards.
* **Dedicated Receive Screen**: Dedicated screen to manage active incoming requests and view ready status.

---

## Project Structure

```
NexShare/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/nexshare/
│   │   │   │   ├── AppContainer.kt               # DI & global application container
│   │   │   │   ├── MainActivity.kt               # Main entry Activity
│   │   │   │   ├── PermissionUtils.kt            # Runtime permission helpers
│   │   │   │   ├── data/                         # Room Database (AppDatabase, TransferDao, PeerDao)
│   │   │   │   ├── file/                         # FileManager, TemporaryFileManager, CheckpointStore
│   │   │   │   ├── network/                      # TcpTransferManager, BluetoothTransferManager,
│   │   │   │   │                                 # NsdDiscoveryManager, UdpDiscoveryManager, FrameCodec
│   │   │   │   ├── security/                     # SecurityPolicy, HashManager, PairingManager
│   │   │   │   ├── transfer/                     # TransferService, ActiveTransfers, TransferModels
│   │   │   │   ├── ui/                           # Jetpack Compose Screens (Dashboard, Receive, Send,
│   │   │   │   │                                 # Peers, Hotspot, Bluetooth, Transfers, History, Settings)
│   │   │   │   │   └── theme/                    # Color, Type, Theme typography & palette
│   │   │   │   └── util/                         # QRCodeEncoder, QRCodeDecoder
│   │   │   └── AndroidManifest.xml
│   │   └── androidTest/                          # LoopbackTransferTest, TransferRepositoryTest
│   └── build.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── .gitignore
```

---

## How to Build & Run

### Prerequisites
* **Android Studio**: Ladybug / 2024.2+ or Android Studio Jellyfish+
* **JDK**: Version 17
* **Android SDK**: `compileSdk = 35`, `minSdk = 26`

### Building the Debug APK via Command Line
Run the Gradle assemble task from the root directory:
```bash
# Windows
.\gradlew assembleDebug

# macOS / Linux
./gradlew assembleDebug
```
The output APK file will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Running Unit Tests
```bash
# Windows
.\gradlew app:testDebugUnitTest

# macOS / Linux
./gradlew app:testDebugUnitTest
```

---

## How to Use NexShare

### 1. Sending Files
1. Open **NexShare** and tap **Send Files** on the Dashboard or Nav Rail.
2. Select one or more files using the file picker.
3. Tap **Choose a device** to view nearby discovered devices, or tap **Show Transfer QR Code** to display a connection QR code.
4. Select the target peer device to initiate high-speed transfer.

### 2. Receiving Files
1. Open the **Receive** tab on the target device.
2. Ensure Wi-Fi / Hotspot or Bluetooth is enabled.
3. Tap **Scan with Camera** to scan the sender's QR code directly, or accept the incoming transfer prompt.
4. Once verified, the received file will automatically appear in your device's **Downloads** folder.

---

## License & Attribution
Designed and built for Android using Jetpack Compose, Kotlin Coroutines, Room, DataStore, CameraX, and ZXing.
