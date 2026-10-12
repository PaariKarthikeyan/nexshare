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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexshare.data.TransferEntity
import com.example.nexshare.ui.theme.*
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val items by viewModel.history.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MainPanel)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("History", style = MaterialTheme.typography.headlineMedium, color = PrimaryText)
            Text(
                text = "Only transfer metadata is stored. File contents are not stored in the database.",
                color = SecondaryText,
                fontSize = 13.sp
            )
        }
        item {
            OutlinedButton(onClick = { viewModel.clear() }, enabled = items.isNotEmpty()) {
                Text("Delete transfer history")
            }
        }
        if (items.isEmpty()) {
            item { PanelCard { Text("No transfers yet.", color = SecondaryText) } }
        } else {
            items(items, key = { it.transferId }) { HistoryRow(it) }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun HistoryRow(t: TransferEntity) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(t.fileName, color = PrimaryText, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${t.direction} • ${t.method} • ${formatBytes(t.fileSize)}",
            color = SecondaryText,
            fontSize = 12.sp
        )
        Text(
            text = "${if (t.direction == "SENT") "To" else "From"}: ${if (t.direction == "SENT") t.receiver else t.sender}",
            color = SecondaryText,
            fontSize = 12.sp
        )
        Text(
            text = DateFormat.getDateTimeInstance().format(Date(t.createdAt)),
            color = SecondaryText,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(text = t.status, color = if (t.status == "VERIFIED") Success else Danger)
            t.completedAt?.let {
                Text(
                    text = "${(it - t.createdAt) / 1000}s",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
            }
        }
        Text(
            text = "SHA-256: ${t.sha256.take(16)}…",
            color = SecondaryText,
            fontSize = 11.sp
        )
    }
}