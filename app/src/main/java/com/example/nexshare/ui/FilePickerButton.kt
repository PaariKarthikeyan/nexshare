package com.example.nexshare.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Picker entry points placed vertically stacked below each other.
 */
@Composable
fun FilePickerButtons(
    onFilesPicked: (List<Uri>) -> Unit,
    onFolderPicked: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val multiple = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onFilesPicked(uris) }

    val folder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(onFolderPicked) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { multiple.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select Files to Send")
        }
        OutlinedButton(
            onClick = { folder.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select Folder to Send")
        }
    }
}
