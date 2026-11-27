package com.jwwviewer.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Saves opened drawings/PDFs into a dedicated **JwwViewer** folder on device storage
 * (Documents/JwwViewer — visible in Files / My Files).
 */
object SavedFiles {
    const val FOLDER_NAME = "JwwViewer"
    private val relativeDocs: String
        get() = "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER_NAME"

    fun libraryDir(context: Context): File {
        // Prefer public Documents/JwwViewer when writable (API 28 and below, or OEMs that allow it)
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            FOLDER_NAME,
        )
        try {
            if ((publicDir.exists() || publicDir.mkdirs()) && publicDir.canWrite()) {
                return publicDir
            }
        } catch (_: Exception) {
        }
        // App-visible external: Android/data/.../files/Documents/JwwViewer
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.getExternalFilesDir(null)
            ?: context.filesDir
        return File(base, FOLDER_NAME).also { it.mkdirs() }
    }

    fun saveCopy(context: Context, source: Uri, displayName: String): Uri {
        val safe = sanitizeName(displayName)
        val mime = mimeFor(safe)

        if (Build.VERSION.SDK_INT >= 29) {
            val mediaUri = saveViaMediaStore(context, safe, mime) { out ->
                context.contentResolver.openInputStream(source)?.use { input ->
                    input.copyTo(out)
                } ?: error("Cannot read source file")
            }
            if (mediaUri != null) return mediaUri
        }

        return writeToLibraryFile(context, safe, mime) { out ->
            context.contentResolver.openInputStream(source)?.use { input ->
                input.copyTo(out)
            } ?: error("Cannot read source file")
        }
    }

    fun saveBytes(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        mime: String = mimeFor(displayName),
    ): Uri {
        val safe = sanitizeName(displayName)
        if (Build.VERSION.SDK_INT >= 29) {
            val mediaUri = saveViaMediaStore(context, safe, mime) { it.write(bytes) }
            if (mediaUri != null) return mediaUri
        }
        return writeToLibraryFile(context, safe, mime) { it.write(bytes) }
    }

    /** e.g. plan.jww → plan_copy.jww */
    fun duplicateName(displayName: String): String {
        val safe = sanitizeName(displayName)
        val dot = safe.lastIndexOf('.')
        return if (dot > 0) {
            "${safe.substring(0, dot)}_copy${safe.substring(dot)}"
        } else {
            "${safe}_copy"
        }
    }

    /** Best-effort delete of a library / MediaStore file; returns true if removed. */
    fun tryDelete(context: Context, uri: Uri): Boolean {
        return try {
            when (uri.scheme) {
                "content" -> context.contentResolver.delete(uri, null, null) > 0
                "file" -> {
                    val path = uri.path ?: return false
                    File(path).takeIf { it.exists() }?.delete() == true
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun writeToLibraryFile(
        context: Context,
        safe: String,
        mime: String,
        writer: (FileOutputStream) -> Unit,
    ): Uri {
        val dir = libraryDir(context)
        var out = File(dir, safe)
        if (out.exists()) out = File(dir, uniqueName(safe))
        FileOutputStream(out).use(writer)
        runCatching {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(out.absolutePath),
                arrayOf(mime),
                null,
            )
        }
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                out,
            )
        } catch (_: Exception) {
            Uri.fromFile(out)
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        displayName: String,
        mime: String,
        writer: (java.io.OutputStream) -> Unit,
    ): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDocs)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val collection = MediaStore.Files.getContentUri("external")
            val dest = context.contentResolver.insert(collection, values) ?: return null
            context.contentResolver.openOutputStream(dest)?.use { output ->
                writer(output)
            } ?: run {
                context.contentResolver.delete(dest, null, null)
                return null
            }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(dest, values, null, null)
            }
            dest
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeName(displayName: String): String =
        displayName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "document" }

    private fun uniqueName(safe: String): String {
        val dot = safe.lastIndexOf('.')
        val base = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        return "${base}_${System.currentTimeMillis()}$ext"
    }

    private fun mimeFor(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".jww") -> "application/octet-stream"
            lower.endsWith(".jwc") -> "application/octet-stream"
            else -> "application/octet-stream"
        }
    }
}
