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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexshare.PermissionUtils
import com.example.nexshare.transfer.BluetoothAvailability
import com.example.nexshare.transfer.BluetoothDeviceUi
import com.example.nexshare.ui.theme.*

@Composable
fun BluetoothScreen(viewModel: BluetoothViewModel) {
    val state by viewModel.state.collectAsState()

    // Bluetooth permissions are requested when the user opens this screen and taps Grant.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    val availabilityColor = when (state.availability) {
        BluetoothAvailability.ENABLED -> Success
        BluetoothAvailability.DISABLED -> Warning
        BluetoothAvailability.UNAVAILABLE -> Danger
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MainPanel)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Bluetooth",
            style = MaterialTheme.typography.headlineMedium,
            color = PrimaryText
        )
        Text(
            text = "Fallback for small files. Use the hotspot for large transfers.",
            color = SecondaryText
        )

        PanelCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Adapter", color = SecondaryText, fontSize = 14.sp)
                StatusBadge(text = state.availability.name, color = availabilityColor)
            }
            InfoRow(
                label = "Permission",
                value = if (state.permissionGranted) "Granted" else "Not granted"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!state.permissionGranted) {
                Button(onClick = {
                    permissionLauncher.launch(PermissionUtils.bluetoothPermissions())
                }) {
                    Text("Grant permission")
                }
            }
            OutlinedButton(onClick = { viewModel.refresh() }) {
                Text("Refresh")
            }
            OutlinedButton(
                onClick = { viewModel.scan() },
                enabled = state.permissionGranted &&
                        state.availability == BluetoothAvailability.ENABLED
            ) {
                Text("Scan")
            }
        }

        state.message?.let {
            Text(text = it, color = Warning, fontSize = 14.sp)
        }

        Text(
            text = "PAIRED DEVICES",
            color = SecondaryText,
            style = MaterialTheme.typography.labelSmall
        )

        if (state.pairedDevices.isEmpty()) {
            PanelCard(modifier = Modifier.fillMaxWidth()) {
                Text("No paired devices yet.", color = SecondaryText)
            }
        } else {
            state.pairedDevices.forEach { device ->
                PairedDeviceCard(device)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connecting, sending, and disconnecting over Bluetooth are enabled in a later phase.",
            color = SecondaryText,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun PairedDeviceCard(device: BluetoothDeviceUi) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = device.name,
            color = PrimaryText,
            style = MaterialTheme.typography.titleMedium
        )
        Text(text = device.address, color = SecondaryText, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}, enabled = false) { Text("Connect") }
            OutlinedButton(onClick = {}, enabled = false) { Text("Send via Bluetooth") }
        }
    }
}