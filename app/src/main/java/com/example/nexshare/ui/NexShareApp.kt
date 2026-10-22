package com.example.nexshare.ui

import android.bluetooth.BluetoothManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexshare.NexShareApplication
import com.example.nexshare.PermissionUtils
import com.example.nexshare.ui.theme.OuterBackground
import androidx.compose.runtime.LaunchedEffect

@Composable
fun NexShareApp() {
    val context = LocalContext.current
    val container = (context.applicationContext as NexShareApplication).container
    val deviceName = Build.MODEL.ifBlank { "NexShare device" }

    val peersViewModel: PeersViewModel = viewModel()
    val hotspotViewModel: HotspotViewModel = viewModel()
    val bluetoothViewModel: BluetoothViewModel = viewModel()
    val sendViewModel: SendViewModel = viewModel()

    val btAdapter = remember {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    }

    var isReady by remember {
        val missing = PermissionUtils.missing(context, PermissionUtils.allRequiredPermissions())
        val btEnabled = btAdapter?.isEnabled == true
        mutableStateOf(missing.isEmpty() && btEnabled)
    }

    LaunchedEffect(isReady) {
        if (isReady) {
            val serverPort = container.transferServer.startServer(deviceName)
            container.bluetoothManager.startServer(deviceName)
            peersViewModel.startDiscovery(deviceName, serverPort)
            hotspotViewModel.bind(container.transferServer, deviceName) { port ->
                if (port > 0) peersViewModel.startDiscovery(deviceName, port)
            }
        }
    }

    if (!isReady) {
        PermissionGateScreen(onProceed = { isReady = true })
    } else {
        val pending by container.pendingRequest.collectAsState()
        pending?.let { request ->
            IncomingRequestDialog(
                request = request,
                onAccept = { container.answerPending(true) },
                onReject = { container.answerPending(false) },
                onBlock = {
                    container.security.block(request.senderHost)
                    container.answerPending(false)
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OuterBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            AppNavigation(
                container = container,
                peersViewModel = peersViewModel,
                hotspotViewModel = hotspotViewModel,
                bluetoothViewModel = bluetoothViewModel,
                sendViewModel = sendViewModel
            )
        }
    }
}
