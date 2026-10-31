package com.universalconverter.pro.core.engine

import android.content.Context
import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.convert.TargetKind
import com.universalconverter.pro.core.convert.TargetSpec
import com.universalconverter.pro.core.detect.FormatDetector
import com.universalconverter.pro.core.formats.FormatRegistry
import com.universalconverter.pro.core.media.MediaEngine
import com.universalconverter.pro.core.mesh.MeshIO
import com.universalconverter.pro.core.mesh.MeshWriteOptions
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlin.system.measureTimeMillis

data class ConversionOutcome(
    val output: File,
    val outputIsDirectory: Boolean,
    val sizeBytes: Long,
    val checksumSha256: String?,
    val verified: Boolean,
    val verification: String,
    val steps: List<String>,
    val warnings: List<String>,
    val engine: String,
    val durationMs: Long,
)

class ConversionEngine(context: Context, private val cacheDir: File) {

    private val appContext = context.applicationContext
    private val imageEngine = ImageEngine(appContext)
    private val vectorEngine = VectorEngine(imageEngine)
    private val documentEngine = DocumentEngine()
    private val mediaEngine = MediaEngine(cacheDir)
    private val archiveEngine = ArchiveEngine()

    fun convert(
        source: File,
        sourceFormatId: String,
        target: TargetSpec,
        options: ConversionOptions,
        output: File,
        progress: (Float, String) -> Unit,
    ): ConversionOutcome {
        require(source.isFile) { "The source file is missing." }
        output.parentFile?.mkdirs()
        if (output.exists()) throw ConversionException("An output file with that name already exists.")
        val started = System.currentTimeMillis()
        var engine = "Native engine"
        var steps = mutableListOf<String>()
        var warnings = mutableListOf<String>()
        var verified = false
        var outputIsDirectory = false
        var verification = "File written"
        var size = 0L
        var checksum: String? = null
        val elapsed = measureTimeMillis {
            if (target.kind == TargetKind.FOLDER) {
                val part = File(output.parentFile, "${output.name}.part-${UUID.randomUUID()}")
                try {
                    val report = archiveEngine.extract(source, sourceFormatId, part) { fraction, stage ->
                        progress(0.02f + fraction * 0.94f, stage)
                    }
                    if (!part.renameTo(output)) {
                        Files.move(part.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    val stats = directoryStats(output)
                    if (stats.files == 0) throw ConversionException("The archive did not produce any files.")
                    size = stats.bytes
                    outputIsDirectory = true
                    verified = true
                    verification = "${stats.files} files extracted"
                    steps = report.steps.toMutableList()
                    warnings = report.warnings.toMutableList()
                    engine = "Archive engine"
                    progress(1f, "Extracted")
                } catch (error: Throwable) {
                    part.deleteRecursively()
                    output.deleteRecursively()
                    throw error
                }
                return@measureTimeMillis
            }

            val part = File(output.parentFile, "${output.name}.part-${UUID.randomUUID()}")
            try {
                val report: EngineReport = when {
                    sourceFormatId in MeshIO.readableIds && target.formatId in MeshIO.writableIds -> {
                        progress(0.02f, "Reading 3D model")
                        val loaded = MeshIO.read(source, sourceFormatId) { fraction ->
                            progress(0.02f + fraction * 0.4f, "Reading 3D model")
                        }
                        progress(0.45f, "Writing ${target.id.uppercase()}")
                        MeshIO.write(
                            loaded.mesh,
                            part,
                            target.formatId!!,
                            MeshWriteOptions(binary = options.meshBinary),
                        ) { fraction ->
                            progress(0.45f + fraction * 0.5f, "Writing ${target.id.uppercase()}")
                        }
                        warnings = loaded.warnings.toMutableList()
                        steps = mutableListOf(
                            "${loaded.mesh.triangleCount} triangles · ${loaded.mesh.vertexCount} vertices",
                            "Mesh topology preserved",
                        )
                        engine = "Mesh engine"
                        EngineReport(steps, warnings)
                    }
                    sourceFormatId in setOf("dxf", "svg", "svgz") -> {
                        engine = "Vector engine"
                        vectorEngine.convert(source, sourceFormatId, target.id, options, part, progress)
                    }
                    sourceFormatId in setOf("jpg", "png", "webp", "bmp", "tiff", "gif", "dng", "heic", "avif") -> {
                        engine = if (sourceFormatId == "dng") "RAW preview engine" else "Image engine"
                        progress(0.05f, "Decoding image")
                        val decoded = imageEngine.decode(source, sourceFormatId, options.imageMaxEdge)
                        progress(0.55f, "Image decoded")
                        val encoded = imageEngine.encode(decoded.bitmap, source, sourceFormatId, target.id, options, part, progress)
                        decoded.bitmap.recycle()
                        EngineReport(encoded.steps, decoded.warnings + encoded.warnings)
                    }
                    sourceFormatId == "pdf" || sourceFormatId in setOf(
                        "docx", "xlsx", "txt", "md", "html", "csv", "tsv", "json", "xml",
                    ) -> {
                        engine = "Document engine"
                        documentEngine.convert(source, sourceFormatId, target.id, options, part, progress)
                    }
                    sourceFormatId in setOf("mp3", "m4a", "wav", "flac", "ogg", "opus", "aiff", "mp4", "mov", "mkv", "webm", "3gp") -> {
                        engine = "Media engine"
                        mediaEngine.convert(source, sourceFormatId, target.id, options, part, progress)
                    }
                    sourceFormatId in setOf("zip", "tar", "tgz", "gz", "bz2", "xz", "7z") -> {
                        engine = "Archive engine"
                        archiveEngine.repack(source, sourceFormatId, target.id, part, options, progress)
                    }
                    else -> throw ConversionException("${FormatRegistry.byId(sourceFormatId)?.displayName ?: sourceFormatId} → ${target.label} is not supported.")
                }
                steps = report.steps.toMutableList()
                warnings = report.warnings.toMutableList()
                if (!part.isFile || part.length() == 0L) throw ConversionException("The converter produced an empty file.")
                Files.move(part.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
                val check = verify(output, target)
                verified = check.first
                verification = check.second
                if (!verified) throw ConversionException(verification)
                size = output.length()
                checksum = sha256(output)
                progress(1f, "Ready")
            } catch (error: Throwable) {
                part.delete()
                output.delete()
                throw error
            }
        }
        return ConversionOutcome(
            output = output,
            outputIsDirectory = outputIsDirectory,
            sizeBytes = size,
            checksumSha256 = checksum,
            verified = verified,
            verification = verification,
            steps = steps,
            warnings = warnings,
            engine = engine,
            durationMs = System.currentTimeMillis() - started,
        )
    }

    private fun verify(output: File, target: TargetSpec): Pair<Boolean, String> {
        if (!output.isFile || output.length() == 0L) return false to "The output file is empty."
        if (target.kind == TargetKind.PAGE_SET_ZIP) {
            val detection = FormatDetector.detect(output)
            val ok = detection.format?.id == "zip"
            return if (ok) true to "ZIP container verified" else false to "The page set is not a valid ZIP."
        }
        val expected = target.formatId ?: return true to "File written"
        val detection = FormatDetector.detect(output)
        val detected = detection.format?.id
        val textual = expected in setOf("txt", "md", "html", "csv", "tsv", "json", "xml")
        if (textual && !detection.verified) return true to "Text file written"
        if (detected == null) return true to "File written"
        if (detection.verified && detected != expected) {
            return false to "Output check failed: expected $expected, detected $detected."
        }
        return if (detection.verified) true to "Header verified as ${FormatRegistry.byId(detected)?.displayName ?: detected}"
        else true to "File written"
    }

    private fun sha256(file: File): String? {
        return try {
            if (file.length() > 2L * 1024L * 1024L * 1024L) return null
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        } catch (_: Exception) {
            null
        }
    }

    private data class DirectoryStats(val files: Int, val bytes: Long)

    private fun directoryStats(directory: File): DirectoryStats {
        var files = 0
        var bytes = 0L
        val stack = ArrayDeque<File>()
        stack += directory
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) stack += child
                else {
                    files++
                    bytes += child.length()
                }
            }
        }
        return DirectoryStats(files, bytes)
    }
}

class ConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)