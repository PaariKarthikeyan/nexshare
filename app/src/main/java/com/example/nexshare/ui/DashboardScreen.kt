package com.example.nexshare.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexshare.transfer.Peer
import com.example.nexshare.transfer.PeerStatus
import com.example.nexshare.ui.theme.*

@Composable
fun DashboardScreen(
    peersViewModel: PeersViewModel,
    hotspotViewModel: HotspotViewModel,
    onNavigate: (String) -> Unit
) {
    val peers by peersViewModel.peers.collectAsState()
    // Hotspot state is observed here so the dashboard can reflect it once the hotspot is wired up.
    hotspotViewModel.state.collectAsState()

    var query by remember { mutableStateOf("") }
    val deviceName = Build.MODEL.ifBlank { "This device" }
    val onlineCount = peers.count { it.status == PeerStatus.ONLINE }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MainPanel)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { DashboardHeader(deviceName) }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Search devices or files", color = SecondaryText) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = SecondaryText)
                    }
                )
            }

            item { HeroCard(onSend = { onNavigate(Route.Peers.route) }) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard("Nearby devices", onlineCount.toString(), Modifier.weight(1f))
                        StatCard("Active transfers", "0", Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard("Completed today", "0", Modifier.weight(1f))
                        StatCard("Transferred data", "0 B", Modifier.weight(1f))
                    }
                }
            }

            item { SectionTitle("Quick actions") }
            item { QuickActions(onNavigate) }

            item { SectionTitle("Nearby activity") }
            if (peers.isEmpty()) {
                item {
                    PanelCard(modifier = Modifier.fillMaxWidth(), color = DarkPanel) {
                        Text("No devices nearby.", color = SecondaryText)
                    }
                }
            } else {
                items(peers, key = { it.peerId }) { peer ->
                    PeerSummaryRow(peer)
                }
            }

            item { SectionTitle("Recent transfers") }
            item {
                PanelCard(modifier = Modifier.fillMaxWidth(), color = DarkPanel) {
                    Text("No transfers yet.", color = SecondaryText)
                }
            }

            // Spacer so the floating button never covers the last card.
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }

        FloatingActionButton(
            onClick = { onNavigate(Route.Peers.route) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = PrimaryAccent,
            contentColor = PrimaryText
        ) {
            Icon(Icons.Default.Send, contentDescription = "Send files")
        }
    }
}

@Composable
private fun DashboardHeader(deviceName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Welcome back", color = SecondaryText, fontSize = 14.sp)
            Text(
                text = "NexShare",
                style = MaterialTheme.typography.displaySmall,
                color = PrimaryText
            )
            Text(text = "This device: $deviceName", color = SecondaryText, fontSize = 12.sp)
        }
        Avatar(name = deviceName, diameter = 48)
    }
}

@Composable
private fun HeroCard(onSend: () -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth(), color = PrimaryAccent) {
        Text(
            text = "Share files directly",
            style = MaterialTheme.typography.displaySmall,
            color = PrimaryText
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "No cloud. No central server. Just direct device-to-device transfer.",
            color = PrimaryText
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onSend,
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkPanel,
                contentColor = PrimaryText
            )
        ) {
            Text("Send files")
        }
    }
}

@Composable
private fun QuickActions(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { onNavigate(Route.Receive.route) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Receive Files") }
        OutlinedButton(
            onClick = { onNavigate(Route.Send.route) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Send Files") }
        OutlinedButton(
            onClick = { onNavigate(Route.Peers.route) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Nearby Devices") }
    }
}

@Composable
private fun PeerSummaryRow(peer: Peer) {
    PanelCard(modifier = Modifier.fillMaxWidth(), color = DarkPanel) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Avatar(name = peer.deviceName, diameter = 40)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.deviceName,
                    color = PrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${peer.method.name} • ${timeAgo(peer.lastSeenMillis)}",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
            }
            StatusBadge(
                text = peer.status.name,
                color = if (peer.status == PeerStatus.ONLINE) Success else SecondaryText
            )
        }
    }
}