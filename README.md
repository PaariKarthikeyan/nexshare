# NexShare v1.1

**NexShare** is a fast, secure, cross-platform, and modern application for direct device-to-device file transfer. Built with Kotlin, Jetpack Compose, Coroutines, CameraX, ZXing, and high-speed binary framing sockets, NexShare requires no cloud servers and guarantees direct local data transfers between Android devices, PCs, Macs, Linux computers, and iOS web browsers.

---

## What's New in v1.1

* **Permission & Service Setup Gate**: On startup, NexShare checks for required permissions (Location, Nearby Wi-Fi, Bluetooth, Camera, Notifications) and prompts the user to enable Bluetooth and grant permissions before unlocking the app.
* **Smart Bluetooth Device Filtering**: Excludes non-transfer Bluetooth peripherals (audio headsets, speakers, selfie sticks, game controllers, smartwatches) to list only smartphones and computers capable of file transfer.
* **Instant Unblocked UDP Subnet Discovery**: Uses `CHANGE_WIFI_MULTICAST_STATE` and `WifiManager.MulticastLock` to unblock incoming UDP discovery pings (`NXSH_PING` / `NXSH_PONG` on port 5002) across Wi-Fi and Hotspot subnets.
* **Bi-Directional QR Code Pairing**: When a receiver scans a sender's QR code, a handshake ping is sent back, automatically adding both devices to each other's **Nearby Devices** list.
* **Cross-Platform Embedded Web Share Server**: Embedded HTTP server (`http://<ip>:8080`) allows PC, Mac, Linux, and iPhone browsers on the same network to download files from Android or upload files directly back to Android.
* **Direct Downloads Directory Storage**: Received files are automatically verified via SHA-256 and saved directly into the device's public **Downloads** folder (`Environment.DIRECTORY_DOWNLOADS`).
* **Mandatory Transfer Acceptance Prompt**: Every incoming file transfer prompts the receiver with an **Accept / Reject / Block** dialog (`IncomingRequestDialog`) before any data is transferred.
* **Desktop Application Exporting**: Added Compose Multiplatform desktop packaging configuration (`.jar`, `.msi`, `.exe`, `.dmg`).

---

## Key Features

### 1. Instant Multi-Protocol Discovery
* **UDP Subnet Broadcast**: High-speed broadcast pings (`NXSH_PING` on port 5002) for sub-100ms discovery across local networks and Hotspots.
* **Network Service Discovery (mDNS / DNS-SD)**: Resolves local network services (`_nexshare._tcp.`) via Android's `NsdManager`.
* **Filtered Bluetooth Discovery**: Scans and lists nearby connectable smartphones and computers while filtering out peripherals.

### 2. QR Code Pairing & Live Camera Scanner
* **Sender QR Code Generation**: Generates dynamic QR codes containing host IP, port, and transfer metadata (`TransferQRCodeDialog`).
* **Receiver Camera Scanner**: Live camera preview scanning (using CameraX and ZXing) to scan QR codes on the sender's screen.
* **Manual IP Entry & Image Gallery Fallback**: Enter an IP address manually or pick a QR code image file from the gallery.

### 3. Cross-Platform Web Share Mode
* **No Software Installation on PC/Mac Required**: Start Web Share on Android and open `http://<ip>:8080` in Chrome, Edge, or Safari on any computer or iPhone on the same network.
* **Download & Upload**: Computer users can download shared files or upload files directly into the Android phone's Downloads folder.

### 4. Binary Framing Protocol & Reliability
* **Binary Socket Protocol**: Custom header (`NXSH`) with 64 KB chunking, sequence checking, pause/resume checkpointing, and cancellation support over TCP sockets (port 5000).
* **SHA-256 Checksum**: Cryptographic checksum calculated end-to-end to ensure 100% file integrity.

### 5. Sleek Jetpack Compose UI
* **Cohesive Dark Palette**: Polished dark slate theme (`#0F1117` background, `#181A20` panels, `#20232B` cards, `#6366F1` indigo primary accent).
* **Vertical Stacked Action Controls**: Clean, full-width action buttons arranged in vertical order.

---

## Project Structure

```
NexShare/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/nexshare/
│   │   │   │   ├── AppContainer.kt               # DI & global application container
│   │   │   │   ├── DesktopMain.kt                # Desktop entry point
│   │   │   │   ├── MainActivity.kt               # Main Android Activity
│   │   │   │   ├── PermissionUtils.kt            # Runtime permission helpers
│   │   │   │   ├── data/                         # Room Database (AppDatabase, TransferDao, PeerDao)
│   │   │   │   ├── file/                         # FileManager, TemporaryFileManager, CheckpointStore
│   │   │   │   ├── network/                      # TcpTransferManager, BluetoothTransferManager,
│   │   │   │   │                                 # WebShareServer, NsdDiscoveryManager, UdpDiscoveryManager, WifiAutoConnector
│   │   │   │   ├── security/                     # SecurityPolicy, HashManager, PairingManager
│   │   │   │   ├── transfer/                     # TransferService, ActiveTransfers, TransferModels
│   │   │   │   ├── ui/                           # Jetpack Compose Screens (Dashboard, Receive, Send,
│   │   │   │   │                                 # Peers, PermissionGate, Hotspot, Bluetooth, Transfers, History, Settings)
│   │   │   │   │   └── theme/                    # Color, Type, Theme typography & palette
│   │   │   │   └── util/                         # QRCodeEncoder, QRCodeDecoder
│   │   │   └── AndroidManifest.xml
│   │   └── androidTest/                          # LoopbackTransferTest, TransferRepositoryTest
│   └── build.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── README.md
```

---

## How to Build & Export

### Prerequisites
* **Android Studio**: Ladybug / 2024.2+
* **JDK**: Version 17
* **Android SDK**: `compileSdk = 35`, `minSdk = 26`

### 1. Export Mobile App (Android `.apk`)
```bash
# Windows
.\gradlew assembleDebug

# macOS / Linux
./gradlew assembleDebug
```
* **Output APK Path:** `app/build/outputs/apk/debug/app-debug.apk`

### 2. Export Standalone Desktop Application (`.jar`)
```bash
# Windows
.\gradlew app:packageUberJarForCurrentOS

# macOS / Linux
./gradlew app:packageUberJarForCurrentOS
```
* **Output Desktop App Path:** `app/build/compose/jars/NexShare-windows-x64-1.1.0.jar`

### 3. Run Unit Tests
```bash
.\gradlew app:testDebugUnitTest
```

---

## How to Use NexShare

### Mobile-to-Mobile Transfer (Android to Android)
1. Open **NexShare** on both Android phones. Grant permissions on the setup screen if prompted.
2. **Sender**: Open **Send Files**, select your files, and tap **Choose a device** (or tap *Show Transfer QR Code*).
3. **Receiver**: Open **Receive Files** (or scan the sender's QR code with **Scan with Camera**).
4. Select the target peer device. The receiver will be prompted with an **Incoming Transfer Request** dialog.
5. Tap **Accept Transfer**. The file will stream over TCP socket and save directly to the receiver's **Downloads** folder.

### Mobile-to-Computer Transfer (Android to PC / Mac / Linux / iPhone)
1. Select files in **Send Files** on Android and tap **Web Share (Transfer with PC / Mac / iPhone)**.
2. On the computer or iPhone browser, open the displayed web address (e.g. `http://192.168.43.1:8080`).
3. Download shared files directly to your computer or upload files from your computer to Android's **Downloads** folder.

---

## License & Attribution
Designed and built using Jetpack Compose, Kotlin Coroutines, Room, DataStore, CameraX, ZXing, and Compose Multiplatform.
