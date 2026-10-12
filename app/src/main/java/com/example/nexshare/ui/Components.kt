package com.example.nexshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexshare.ui.theme.*

/** Smooth dark card container. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    color: Color = Card,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun Avatar(name: String, diameter: Int = 40) {
    Box(
        modifier = Modifier
            .size(diameter.dp)
            .clip(CircleShape)
            .background(PrimaryAccent.copy(alpha = 0.2f))
            .border(1.dp, PrimaryAccent.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = PrimaryText,
            fontWeight = FontWeight.Bold,
            fontSize = (diameter / 2.2).sp
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = SecondaryText, fontSize = 13.sp)
        Text(text = value, color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = SecondaryText,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    PanelCard(modifier = modifier, color = DarkPanel) {
        Text(
            text = label.uppercase(),
            color = SecondaryText,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = PrimaryText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun timeAgo(millis: Long): String {
    val minutes = ((System.currentTimeMillis() - millis) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        else -> "${minutes / 60}h ago"
    }
}

@Composable
fun IncomingRequestDialog(
    request: com.example.nexshare.transfer.PendingRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onBlock: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Incoming Transfer Request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("From: ${request.senderName}")
                Text("File: ${request.fileName}")
                Text("Size: ${android.text.format.Formatter.formatShortFileSize(context, request.fileSize)}")
                Text("Pairing Code: ${request.pairingCode}")
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = onAccept) {
                Text("Accept")
            }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = onReject) {
                    Text("Reject")
                }
                androidx.compose.material3.OutlinedButton(onClick = onBlock) {
                    Text("Block")
                }
            }
        }
    )
}
