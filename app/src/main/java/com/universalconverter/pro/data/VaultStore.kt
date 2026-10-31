package com.universalconverter.pro.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.universalconverter.pro.core.convert.TargetKind
import com.universalconverter.pro.core.convert.TargetSpec
import com.universalconverter.pro.core.detect.DetectionResult
import com.universalconverter.pro.core.detect.FormatDetector
import com.universalconverter.pro.core.formats.FormatRegistry
import java.io.File
import java.io.IOException
import java.util.UUID

data class ImportedFile(
    val file: File,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val detection: DetectionResult,
)

data class LibraryItem(
    val file: File,
    val displayName: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val childCount: Int = 0,
)

class VaultStore(private val context: Context) {

    val inboxDir: File = File(context.filesDir, "inbox").apply { mkdirs() }
    val vaultDir: File = File(context.filesDir, "vault").apply { mkdirs() }

    fun importUri(uri: Uri, preferredName: String? = null): ImportedFile {
        val resolver = context.contentResolver
        var displayName = preferredName
        var declaredSize = 0L
        var mime: String? = null
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (displayName == null && nameIndex >= 0) displayName = cursor.getString(nameIndex)
                    if (sizeIndex >= 0) declaredSize = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {
        }
        mime = try { resolver.getType(uri) } catch (_: Exception) { null }
        val rawName = displayName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "import.bin"
        val safeName = uniqueFile(inboxDir, sanitizeFileName(rawName))
        val part = File(safeName.parentFile, "${safeName.name}.part-${UUID.randomUUID()}")
        try {
            val input = resolver.openInputStream(uri)
                ?: throw IOException("The source could not be opened.")
            input.use { source ->
                part.outputStream().buffered().use { destination -> source.copyTo(destination, 1 shl 16) }
            }
            if (!part.renameTo(safeName)) {
                part.copyTo(safeName, overwrite = true)
                part.delete()
            }
        } catch (error: Exception) {
            part.delete()
            throw IOException("The file could not be copied into the vault.", error)
        }
        val size = if (safeName.length() > 0) safeName.length() else declaredSize
        val detection = FormatDetector.detect(safeName, rawName)
        return ImportedFile(safeName, rawName, size, mime, detection)
    }

    fun resolveOutput(sourceName: String, sourceFormatId: String, target: TargetSpec): File {
        val base = sanitizeFileName(sourceName.substringBeforeLast('.').ifBlank { sourceName })
            .take(80)
            .ifBlank { "converted" }
        return when {
            target.kind == TargetKind.FOLDER -> uniqueDirectory(vaultDir, base)
            target.kind == TargetKind.PAGE_SET_ZIP -> uniqueFile(vaultDir, "$base pages.zip")
            target.id == "contact_sheet" -> uniqueFile(vaultDir, "$base contact.jpg")
            else -> uniqueFile(vaultDir, "$base.${target.extension.ifBlank { "bin" }}")
        }
    }

    fun uniqueFile(directory: File, name: String): File {
        directory.mkdirs()
        var candidate = File(directory, sanitizeFileName(name))
        if (!candidate.exists()) return candidate
        val dot = candidate.name.lastIndexOf('.')
        val base = if (dot > 0) candidate.name.substring(0, dot) else candidate.name
        val extension = if (dot > 0) candidate.name.substring(dot) else ""
        var index = 2
        while (true) {
            candidate = File(directory, "$base ($index)$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    fun uniqueDirectory(directory: File, name: String): File {
        directory.mkdirs()
        var candidate = File(directory, sanitizeFileName(name).ifBlank { "extracted" })
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "${sanitizeFileName(name).ifBlank { "extracted" }} ($index)")
            index++
        }
        return candidate
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim()
            .trim('.')
            .take(160)
        return cleaned.ifBlank { "file" }
    }

    fun listVault(): List<LibraryItem> = listDirectory(vaultDir)

    fun listDirectory(directory: File): List<LibraryItem> {
        val children = directory.listFiles() ?: return emptyList()
        return children.mapNotNull { child ->
            if (child.name.startsWith(".")) return@mapNotNull null
            if (child.isDirectory) {
                val stats = directoryStats(child)
                LibraryItem(child, child.name, true, stats.bytes, child.lastModified(), stats.files)
            } else {
                LibraryItem(child, child.name, false, child.length(), child.lastModified())
            }
        }.sortedWith(compareByDescending<LibraryItem> { it.isDirectory }.thenByDescending { it.modifiedAt })
    }

    fun listInbox(): List<LibraryItem> {
        val children = inboxDir.listFiles() ?: return emptyList()
        return children.filter { it.isFile }.map {
            LibraryItem(it, it.name, false, it.length(), it.lastModified())
        }.sortedByDescending { it.modifiedAt }
    }

    fun totalVaultBytes(): Long {
        var total = 0L
        val stack = ArrayDeque<File>()
        stack += vaultDir
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            for (child in current.listFiles() ?: emptyArray()) {
                if (child.isDirectory) stack += child else total += child.length()
            }
        }
        return total
    }

    fun delete(file: File): Boolean {
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    fun rename(file: File, newName: String): File? {
        val target = uniqueFile(file.parentFile ?: return null, sanitizeFileName(newName))
        return if (file.renameTo(target)) target else null
    }

    fun shareIntent(file: File): Intent? {
        if (!file.isFile) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val mime = mimeOf(file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun viewIntent(file: File): Intent? {
        if (!file.isFile) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeOf(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun mimeOf(file: File): String {
        return FormatDetector.detect(file).format?.mimeType
            ?: FormatRegistry.findForFileName(file.name)?.mimeType
            ?: "application/octet-stream"
    }

    fun exportToDownloads(file: File, displayName: String = file.name): Uri? {
        if (!file.isFile || Build.VERSION.SDK_INT < 29) return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeOf(file))
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Universal Converter")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return try {
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().buffered().use { input -> input.copyTo(output, 1 shl 16) }
            } ?: return null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) {
            null
        }
    }

    private fun directoryStats(directory: File): DirectoryStats {
        var files = 0
        var bytes = 0L
        val stack = ArrayDeque<File>()
        stack += directory
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            for (child in current.listFiles() ?: emptyArray()) {
                if (child.isDirectory) stack += child else {
                    files++
                    bytes += child.length()
                }
            }
        }
        return DirectoryStats(files, bytes)
    }

    private data class DirectoryStats(val files: Int, val bytes: Long)
}