package com.example.nexshare.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.compose.ui.unit.dp

/**
 * Three picker entry points. The system picker grants temporary read access
 * to the chosen URIs, so the app never requests broad storage permissions.
 */
@Composable
fun FilePickerButtons(
    onFilesPicked: (List<Uri>) -> Unit,
    onFolderPicked: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val single = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onFilesPicked(listOf(it)) } }

    val multiple = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onFilesPicked(uris) }

    val folder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(onFolderPicked) }

    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = { single.launch(arrayOf("*/*")) }) { Text("File") }
        OutlinedButton(onClick = { multiple.launch(arrayOf("*/*")) }) { Text("Multiple") }
        OutlinedButton(onClick = { folder.launch(null) }) { Text("Folder") }
    }
}