package com.jwwviewer.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object SavedFiles {
    private const val DIR = "saved"

    fun saveCopy(context: Context, source: Uri, displayName: String): Uri {
        val dir = File(context.filesDir, DIR).also { it.mkdirs() }
        val safe = displayName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "document" }
        var out = File(dir, safe)
        if (out.exists()) {
            val dot = safe.lastIndexOf('.')
            val base = if (dot > 0) safe.substring(0, dot) else safe
            val ext = if (dot > 0) safe.substring(dot) else ""
            out = File(dir, "${base}_${System.currentTimeMillis()}$ext")
        }
        context.contentResolver.openInputStream(source)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot read source file")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            out,
        )
    }
}
