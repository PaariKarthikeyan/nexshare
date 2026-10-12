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
import com.example.nexshare.transfer.Peer
import com.example.nexshare.transfer.PeerStatus
import com.example.nexshare.ui.theme.*

@Composable
fun PeersScreen(
    viewModel: PeersViewModel,
    hasQueuedFiles: Boolean,
    onSendTo: (Peer) -> Unit
) {
    val peers by viewModel.peers.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MainPanel)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Nearby devices",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PrimaryText
                    )
                    Text(
                        text = "Make sure both devices are on the same Wi-Fi network or Hotspot.",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }
                OutlinedButton(onClick = { viewModel.startDiscovery(android.os.Build.MODEL.ifBlank { "Device" }, 0) }) {
                    Text("Scan")
                }
            }
        }

        if (peers.isEmpty()) {
            item { PanelCard { Text("No devices nearby yet. Tap Scan or ensure Wi-Fi/Hotspot is connected.", color = SecondaryText) } }
        } else {
            items(peers, key = { it.peerId }) { peer ->
                PeerCard(
                    peer = peer,
                    canSend = hasQueuedFiles && peer.status == PeerStatus.ONLINE,
                    onSend = { onSendTo(peer) },
                    onPair = { viewModel.togglePair(peer.peerId) },
                    onBlock = { viewModel.block(peer.peerId) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun PeerCard(
    peer: Peer,
    canSend: Boolean,
    onSend: () -> Unit,
    onPair: () -> Unit,
    onBlock: () -> Unit
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Avatar(name = peer.deviceName, diameter = 40)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.deviceName,
                    color = PrimaryText,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${peer.host}:${peer.port}",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
                Text(
                    text = "Last seen ${timeAgo(peer.lastSeenMillis)}",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusBadge(
                text = peer.status.name,
                color = if (peer.status == PeerStatus.ONLINE) Success else SecondaryText
            )
            StatusBadge(
                text = if (peer.trusted) "TRUSTED" else "UNTRUSTED",
                color = if (peer.trusted) Success else Warning
            )
            StatusBadge(text = peer.method.name, color = PrimaryAccent)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Stacked vertically below each other as requested ("instead of providing options side by side, provide below it")
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send file to this device")
            }
            OutlinedButton(
                onClick = onPair,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (peer.trusted) "Revoke Trust" else "Trust Device")
            }
            OutlinedButton(
                onClick = onBlock,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Block Device")
            }
        }
    }
}
