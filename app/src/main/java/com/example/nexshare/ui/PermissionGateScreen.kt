package com.example.nexshare.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexshare.PermissionUtils
import com.example.nexshare.ui.theme.*

@Composable
fun PermissionGateScreen(
    onProceed: () -> Unit
) {
    val context = LocalContext.current
    var missingPermissions by remember {
        mutableStateOf(PermissionUtils.missing(context, PermissionUtils.allRequiredPermissions()))
    }
    val btAdapter = remember {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    }
    var isBtEnabled by remember {
        mutableStateOf(btAdapter?.isEnabled == true)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        missingPermissions = PermissionUtils.missing(context, PermissionUtils.allRequiredPermissions())
        isBtEnabled = btAdapter?.isEnabled == true
        if (missingPermissions.isEmpty() && isBtEnabled) {
            onProceed()
        }
    }

    val btEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isBtEnabled = btAdapter?.isEnabled == true
        missingPermissions = PermissionUtils.missing(context, PermissionUtils.allRequiredPermissions())
        if (missingPermissions.isEmpty() && isBtEnabled) {
            onProceed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MainPanel)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Setup NexShare",
            style = MaterialTheme.typography.headlineMedium,
            color = PrimaryText
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "To swiftly discover nearby devices and transfer files, NexShare requires Bluetooth, Wi-Fi, and Camera permissions.",
            color = SecondaryText,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        PanelCard(modifier = Modifier.fillMaxWidth(), color = DarkPanel) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow(
                    label = "Required Permissions",
                    value = if (missingPermissions.isEmpty()) "Granted" else "${missingPermissions.size} Pending"
                )
                InfoRow(
                    label = "Bluetooth Status",
                    value = if (isBtEnabled) "Enabled" else "Disabled"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (missingPermissions.isNotEmpty()) {
                Button(
                    onClick = { permissionLauncher.launch(PermissionUtils.allRequiredPermissions()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Required Permissions")
                }
            }

            if (!isBtEnabled) {
                OutlinedButton(
                    onClick = {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        btEnableLauncher.launch(enableBtIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Turn On Bluetooth")
                }
            }

            Button(
                onClick = {
                    missingPermissions = PermissionUtils.missing(context, PermissionUtils.allRequiredPermissions())
                    isBtEnabled = btAdapter?.isEnabled == true
                    if (missingPermissions.isNotEmpty()) {
                        permissionLauncher.launch(PermissionUtils.allRequiredPermissions())
                    } else if (!isBtEnabled) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        btEnableLauncher.launch(enableBtIntent)
                    } else {
                        onProceed()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (missingPermissions.isEmpty() && isBtEnabled) "Proceed to NexShare" else "Check & Enable All")
            }
        }
    }
}
