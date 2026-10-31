package com.universalconverter.pro.core.engine

import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.image.ImageFormatException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.tukaani.xz.XZInputStream
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.math.min

class ArchiveEngine {

    fun extract(
        source: File,
        sourceFormat: String,
        outputDir: File,
        progress: (Float, String) -> Unit,
    ): EngineReport {
        if (!outputDir.exists() && !outputDir.mkdirs()) throw ArchiveException("Could not create the extraction folder.")
        val warnings = mutableListOf<String>()
        var files = 0
        var bytes = 0L
        val used = HashSet<String>()
        walk(source, sourceFormat, warnings) { entry ->
            if (entry.isDirectory) {
                val dir = safeTarget(outputDir, entry.name)
                if (dir != null) dir.mkdirs()
                return@walk
            }
            val target = safeTarget(outputDir, uniqueName(entry.name, used))
                ?: throw ArchiveException("The archive contains an unsafe path: ${entry.name}")
            target.parentFile?.mkdirs()
            entry.input.use { input ->
                target.outputStream().buffered().use { output ->
                    val copied = input.copyTo(output)
                    bytes += copied
                    if (bytes > 8L * 1024L * 1024L * 1024L) {
                        throw ArchiveException("The archive expands beyond the 8 GB safety limit.")
                    }
                }
            }
            target.setLastModified(entry.lastModified.takeIf { it > 0 } ?: System.currentTimeMillis())
            files++
            if (files > 100_000) throw ArchiveException("The archive has more than 100,000 files.")
            progress((files % 100) / 100f + 0.1f, "Extracting ${entry.name.take(48)}")
        }
        progress(1f, "Archive extracted")
        val steps = listOf("$files files extracted (${bytes / 1024} KB)")
        return EngineReport(steps, warnings)
    }

    fun repack(
        source: File,
        sourceFormat: String,
        targetId: String,
        output: File,
        options: ConversionOptions,
        progress: (Float, String) -> Unit,
    ): EngineReport {
        val warnings = mutableListOf<String>()
        val level = options.archiveLevel.coerceIn(1, 9)
        when (targetId) {
            "zip" -> ZipArchiveOutputStream(output).use { zip ->
                zip.setLevel(level)
                walk(source, sourceFormat, warnings) { entry ->
                    if (entry.isDirectory) return@walk
                    val name = sanitizeEntryName(entry.name)
                    val zipEntry = ZipArchiveEntry(name)
                    zipEntry.time = entry.lastModified
                    zip.putArchiveEntry(zipEntry)
                    entry.input.use { it.copyTo(zip) }
                    zip.closeArchiveEntry()
                }
            }
            "tar", "tgz" -> {
                val outputStream = if (targetId == "tgz") {
                    GzipCompressorOutputStream(output.outputStream().buffered(), org.apache.commons.compress.compressors.gzip.GzipParameters().apply {
                        compressionLevel = level
                    })
                } else {
                    output.outputStream().buffered()
                }
                TarArchiveOutputStream(outputStream).use { tar ->
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    walk(source, sourceFormat, warnings) { entry ->
                        if (entry.isDirectory) return@walk
                        val name = sanitizeEntryName(entry.name)
                        val size = if (entry.size >= 0) entry.size else null
                        if (size == null) {
                            val temp = File.createTempFile("uc-tar-", ".tmp")
                            try {
                                entry.input.use { input -> temp.outputStream().buffered().use { input.copyTo(it) } }
                                val tarEntry = TarArchiveEntry(name)
                                tarEntry.size = temp.length()
                                tarEntry.modTime = java.util.Date(entry.lastModified)
                                tar.putArchiveEntry(tarEntry)
                                temp.inputStream().use { it.copyTo(tar) }
                                tar.closeArchiveEntry()
                            } finally {
                                temp.delete()
                            }
                        } else {
                            val tarEntry = TarArchiveEntry(name)
                            tarEntry.size = size
                            tarEntry.modTime = java.util.Date(entry.lastModified)
                            tar.putArchiveEntry(tarEntry)
                            entry.input.use { it.copyTo(tar) }
                            tar.closeArchiveEntry()
                        }
                    }
                }
            }
            else -> throw ArchiveException("Unsupported archive target $targetId.")
        }
        progress(1f, "Archive ready")
        return EngineReport(listOf("Repacked as ${targetId.uppercase()} at level $level"), warnings)
    }

    private data class Entry(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
        val input: InputStream,
    )

    private fun walk(
        source: File,
        format: String,
        warnings: MutableList<String>,
        onEntry: (Entry) -> Unit,
    ) {
        when (format) {
            "zip" -> {
                try {
                    ZipFile(source).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.isDirectory) {
                                onEntry(Entry(entry.name, true, 0, entry.time, ByteArrayInputStream(ByteArray(0))))
                            } else {
                                val input = try {
                                    zip.getInputStream(entry)
                                } catch (error: Exception) {
                                    throw ArchiveException("This ZIP entry is encrypted or unreadable: ${entry.name}", error)
                                }
                                onEntry(Entry(entry.name, false, entry.size, entry.time, input))
                            }
                        }
                    }
                } catch (error: ArchiveException) {
                    throw error
                } catch (error: Exception) {
                    throw ArchiveException("This ZIP could not be read. It may be encrypted or damaged.", error)
                }
            }
            "tar", "tgz" -> {
                val wrapped: InputStream = if (format == "tgz") {
                    GzipCompressorInputStream(source.inputStream().buffered(), true)
                } else source.inputStream().buffered()
                TarArchiveInputStream(wrapped).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        onEntry(Entry(entry.name, entry.isDirectory, entry.size, entry.lastModifiedDate?.time ?: 0L, NonClosingInputStream(tar)))
                    }
                }
            }
            "gz", "bz2", "xz" -> {
                val name = when {
                    source.name.endsWith(".gz", true) -> source.name.dropLast(3)
                    source.name.endsWith(".bz2", true) -> source.name.dropLast(4)
                    source.name.endsWith(".xz", true) -> source.name.dropLast(3)
                    else -> source.nameWithoutExtension
                }.ifBlank { "decompressed.bin" }
                val input: InputStream = when (format) {
                    "gz" -> GzipCompressorInputStream(source.inputStream().buffered(), true)
                    "bz2" -> BZip2CompressorInputStream(source.inputStream().buffered(), true)
                    else -> XZInputStream(source.inputStream().buffered())
                }
                onEntry(Entry(name, false, -1, source.lastModified(), input))
            }
            "7z" -> {
                try {
                    SevenZFile.builder().setFile(source).get().use { seven ->
                        while (true) {
                            val entry = seven.nextEntry ?: break
                            if (entry.isDirectory) {
                                onEntry(Entry(entry.name, true, 0, entry.lastModifiedDate?.time ?: 0L, ByteArrayInputStream(ByteArray(0))))
                            } else {
                                val input = seven.getInputStream(entry)
                                onEntry(Entry(entry.name, false, entry.size, entry.lastModifiedDate?.time ?: 0L, NonClosingInputStream(input)))
                            }
                        }
                    }
                } catch (error: Exception) {
                    throw ArchiveException("This 7Z could not be read. Encrypted archives are not supported.", error)
                }
            }
            else -> throw ArchiveException("$format archives are not supported.")
        }
        @Suppress("UNUSED_VARIABLE") val unused = warnings
    }

    private fun safeTarget(root: File, name: String): File? {
        val cleaned = name.replace('\\', '/').trimStart('/')
        if (cleaned.isBlank()) return null
        val parts = cleaned.split('/')
        if (parts.any { it == ".." || it.contains(':') }) return null
        val target = File(root, cleaned)
        val canonicalRoot = root.canonicalFile
        val canonicalTarget = target.canonicalFile
        return if (canonicalTarget.path == canonicalRoot.path || canonicalTarget.path.startsWith(canonicalRoot.path + File.separator)) {
            canonicalTarget
        } else null
    }

    private fun uniqueName(name: String, used: MutableSet<String>): String {
        val normalized = sanitizeEntryName(name)
        if (used.add(normalized)) return normalized
        val dot = normalized.lastIndexOf('.')
        val base = if (dot > 0) normalized.substring(0, dot) else normalized
        val extension = if (dot > 0) normalized.substring(dot) else ""
        var index = 2
        while (true) {
            val candidate = "$base ($index)$extension"
            if (used.add(candidate)) return candidate
            index++
        }
    }

    private fun sanitizeEntryName(name: String): String {
        val cleaned = name.replace('\\', '/').trimStart('/')
        val parts = cleaned.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
        if (parts.isEmpty()) return "file"
        return parts.joinToString("/").take(240)
    }
}

private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
    override fun close() {
        // The archive stream owns the lifecycle; only the iterator may close it.
    }
}

class ArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause)