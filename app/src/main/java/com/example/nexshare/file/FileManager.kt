package com.example.nexshare.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.nexshare.transfer.SelectedFile
import java.io.InputStream
import java.io.OutputStream

/**
 * Reads metadata and streams content from content:// URIs.
 * Never loads a whole file into memory.
 */
class FileManager(private val context: Context) {

    private val resolver get() = context.contentResolver

    /** Reads display name, size, and MIME type for a picked document. */
    fun describe(uri: Uri): SelectedFile {
        var name = "unnamed"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        val mime = resolver.getType(uri) ?: mimeFromExtension(name)
        return SelectedFile(
            uri = uri,
            displayName = name,
            sizeBytes = size,
            mimeType = mime
        )
    }

    /** Lists the files directly inside a folder chosen with OpenDocumentTree. */
    fun listFolder(treeUri: Uri): List<SelectedFile> {
        val folder = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return folder.listFiles()
            .filter { it.isFile }
            .map { describe(it.uri) }
    }

    fun openInput(uri: Uri): InputStream? = resolver.openInputStream(uri)

    fun openOutput(uri: Uri): OutputStream? = resolver.openOutputStream(uri)

    private fun mimeFromExtension(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }
}