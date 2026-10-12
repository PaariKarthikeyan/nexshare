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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexshare.transfer.ActiveTransfer
import com.example.nexshare.ui.theme.*

@Composable
fun TransfersScreen(viewModel: TransfersViewModel, onRetry: (ActiveTransfer) -> Unit) {
    val items by viewModel.transfers.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MainPanel)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Transfers", style = MaterialTheme.typography.headlineMedium, color = PrimaryText)
        }
        if (items.isEmpty()) {
            item {
                PanelCard { Text("No active transfers.", color = SecondaryText) }
            }
        } else {
            items(items, key = { it.transferId }) { t ->
                TransferCard(
                    item = t,
                    onPause = { viewModel.pause(t) },
                    onResume = { viewModel.resume(t) },
                    onCancel = { viewModel.cancel(t) },
                    onRetry = { viewModel.retry(t, onRetry) }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun TransferCard(
    item: ActiveTransfer,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val terminal = item.state in setOf("VERIFIED", "FAILED", "CANCELLED", "HASH_MISMATCH")
    val paused = item.state == "PAUSED"
    val remaining = if (item.bytesPerSecond > 0) {
        (item.bytesTotal - item.bytesDone) / item.bytesPerSecond
    } else -1L

    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(item.fileName, color = PrimaryText, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${item.direction} • ${item.peerName}",
            color = SecondaryText,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatusBadge(text = item.state, color = stateColor(item.state))
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { item.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = PrimaryAccent,
            trackColor = LightCard
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${(item.fraction * 100).toInt()}% • ${formatBytes(item.bytesDone)} of ${formatBytes(item.bytesTotal)}",
            color = SecondaryText,
            fontSize = 12.sp
        )
        if (!terminal) {
            Text(
                text = "Speed ${formatBytes(item.bytesPerSecond)}/s" +
                        if (remaining >= 0) " • about ${remaining}s left" else "",
                color = SecondaryText,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                terminal && item.state != "VERIFIED" -> OutlinedButton(onClick = onRetry) { Text("Retry") }
                paused -> OutlinedButton(onClick = onRetry) { Text("Resume") }
                !terminal -> OutlinedButton(onClick = onPause) { Text("Pause") }
            }
            if (!terminal) OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

private fun stateColor(state: String) = when (state) {
    "VERIFIED" -> Success
    "FAILED", "HASH_MISMATCH", "CANCELLED" -> Danger
    "PAUSED" -> Warning
    else -> PrimaryAccent
}