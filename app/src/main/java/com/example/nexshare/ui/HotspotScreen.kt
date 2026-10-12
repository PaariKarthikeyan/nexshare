package com.example.nexshare.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexshare.PermissionUtils
import com.example.nexshare.transfer.HotspotStatus
import com.example.nexshare.ui.theme.*

@Composable
fun HotspotScreen(viewModel: HotspotViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // Hotspot permissions are requested here, when the user opens the hotspot flow.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) viewModel.start()
    }

    val running = state.status == HotspotStatus.RUNNING
    val starting = state.status == HotspotStatus.STARTING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MainPanel)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Hotspot",
            style = MaterialTheme.typography.headlineMedium,
            color = PrimaryText
        )
        Text(
            text = "A local-only Wi-Fi network for fast transfers. No internet access is used.",
            color = SecondaryText
        )

        PanelCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Status", color = SecondaryText, fontSize = 14.sp)
                StatusBadge(text = state.status.name, color = hotspotColor(state.status))
            }
            InfoRow("SSID", state.ssid ?: "Not active")
            InfoRow("Password", state.password ?: "Not active")
            InfoRow("Local IP", state.localIp ?: "Not active")
            InfoRow("Port", if (state.port > 0) state.port.toString() else "Not active")
            InfoRow("Connected devices", "0")
            InfoRow("Discovery", "Not started")
        }

        state.errorMessage?.let {
            Text(text = it, color = Warning, fontSize = 14.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val missing = PermissionUtils.missing(
                        context,
                        PermissionUtils.hotspotPermissions()
                    )
                    if (missing.isEmpty()) {
                        viewModel.start()
                    } else {
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                },
                enabled = !running && !starting
            ) {
                Text("Start hotspot")
            }
            OutlinedButton(
                onClick = { viewModel.stop() },
                enabled = state.status != HotspotStatus.STOPPED
            ) {
                Text("Stop hotspot")
            }
        }

        PanelCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "QR sharing",
                color = PrimaryText,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "QR codes for hotspot credentials are planned for a later release.",
                color = SecondaryText,
                fontSize = 13.sp
            )
        }
    }
}

private fun hotspotColor(status: HotspotStatus): Color = when (status) {
    HotspotStatus.RUNNING -> Success
    HotspotStatus.STARTING -> Warning
    HotspotStatus.ERROR -> Danger
    HotspotStatus.STOPPED -> SecondaryText
}