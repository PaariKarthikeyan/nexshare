package com.example.nexshare.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexshare.security.SecurityPolicy
import com.example.nexshare.ui.theme.*

@Composable
fun SettingsScreen(
    security: SecurityPolicy,
    onClearHistory: () -> Unit
) {
    // Blocked list is shown from a local snapshot; the policy is the source of truth.
    val blocked = remember { mutableStateListOf<String>().apply { addAll(security.blockedSnapshot()) } }
    var confirmExtension by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MainPanel)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = PrimaryText)
        }

        item { SectionTitle("Blocked devices") }
        if (blocked.isEmpty()) {
            item { PanelCard { Text("No blocked devices.", color = SecondaryText) } }
        } else {
            items(blocked, key = { it }) { key ->
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(key, color = PrimaryText)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        security.unblock(key)
                        blocked.remove(key)
                    }) { Text("Unblock") }
                }
            }
        }

        item { SectionTitle("Blocked file types") }
        item {
            PanelCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Executables and scripts are blocked by default. Allowing one is a security risk: " +
                            "received files are never opened or run automatically, but you should only allow a type you trust.",
                    color = SecondaryText,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                SecurityPolicy.DEFAULT_BLOCKED.sorted().forEach { ext ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(ext, color = PrimaryText)
                        OutlinedButton(onClick = { confirmExtension = ext }) { Text("Allow") }
                    }
                }
            }
        }

        item { SectionTitle("Data") }
        item {
            OutlinedButton(onClick = { confirmClear = true }) { Text("Delete transfer history") }
        }
    }

    confirmExtension?.let { ext ->
        AlertDialog(
            onDismissRequest = { confirmExtension = null },
            containerColor = MainPanel,
            title = { Text("Allow $ext files?", color = PrimaryText) },
            text = {
                Text(
                    "Files with this extension can be received. They are never opened or run automatically. Only continue if you trust the sender.",
                    color = SecondaryText
                )
            },
            confirmButton = {
                Button(onClick = {
                    security.allowExtension(ext)
                    confirmExtension = null
                }) { Text("Allow") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmExtension = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = MainPanel,
            title = { Text("Delete history?", color = PrimaryText) },
            text = { Text("This removes transfer records. Received files are not affected.", color = SecondaryText) },
            confirmButton = {
                Button(onClick = {
                    onClearHistory()
                    confirmClear = false
                }) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}