package com.example.nexshare.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissions = PermissionUtils.hotspotPermissions() +
                PermissionUtils.bluetoothPermissions() +
                PermissionUtils.notificationPermissions()
        permissionLauncher.launch(permissions)

        peersViewModel.startDiscovery(deviceName, 0)
        hotspotViewModel.bind(container.transferServer, deviceName) { port ->
            if (port > 0) peersViewModel.startDiscovery(deviceName, port)
        }
    }

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
