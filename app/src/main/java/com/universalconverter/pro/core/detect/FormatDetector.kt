package com.universalconverter.pro.core.detect

import com.universalconverter.pro.core.formats.FormatRegistry
import com.universalconverter.pro.core.formats.FormatSpec
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

data class DetectionResult(
    val format: FormatSpec?,
    val confidence: Float,
    val verified: Boolean,
    val reason: String,
    val declared: FormatSpec?,
    val mismatch: Boolean,
) {
    val extensionLabel: String?
        get() = declared?.let { declared.primaryExtension }
}

object FormatDetector {

    private const val HEADER_BYTES = 64 * 1024

    fun detect(file: File, displayName: String = file.name): DetectionResult {
        if (!file.isFile) {
            return DetectionResult(null, 0f, false, "File is missing", null, false)
        }
        val declared = FormatRegistry.findForFileName(displayName)
            ?: FormatRegistry.findForFileName(file.name)
        val header = try {
            file.inputStream().use { input -> readUpTo(input, HEADER_BYTES) }
        } catch (_: Exception) {
            ByteArray(0)
        }
        val extended = try {
            val needed = maxOf(HEADER_BYTES, 600)
            file.inputStream().use { input -> readUpTo(input, needed) }
        } catch (_: Exception) {
            header
        }

        val magic = magic(header, extended, file, displayName, declared)
        if (magic != null) {
            val mismatch = declared != null && declared.id != magic.format.id
            return DetectionResult(
                format = magic.format,
                confidence = magic.confidence,
                verified = true,
                reason = magic.reason,
                declared = declared,
                mismatch = mismatch,
            )
        }

        val text = decodeText(header)
        val textGuess = if (text != null) classifyText(text, displayName, declared) else null
        if (textGuess != null) {
            val mismatch = declared != null && declared.id != textGuess.format.id
            return DetectionResult(
                format = textGuess.format,
                confidence = textGuess.confidence,
                verified = textGuess.confidence >= 0.8f,
                reason = textGuess.reason,
                declared = declared,
                mismatch = mismatch,
            )
        }

        if (declared != null) {
            return DetectionResult(
                format = declared,
                confidence = 0.32f,
                verified = false,
                reason = "Extension only — content could not be verified",
                declared = declared,
                mismatch = false,
            )
        }

        return DetectionResult(null, 0f, false, "Unrecognised file", null, false)
    }

    private data class MagicGuess(val format: FormatSpec, val reason: String, val confidence: Float = 1f)
    private data class TextGuess(val format: FormatSpec, val reason: String, val confidence: Float)

    private fun magic(
        head: ByteArray,
        extended: ByteArray,
        file: File,
        displayName: String,
        declared: FormatSpec?,
    ): MagicGuess? {
        if (head.isEmpty()) return null
        fun at(offset: Int, text: String): Boolean {
            if (head.size < offset + text.length) return false
            for (i in text.indices) if (head[offset + i] != text[i].code.toByte()) return false
            return true
        }
        fun ascii(offset: Int, length: Int): String {
            val end = minOf(head.size, offset + length)
            if (end <= offset) return ""
            return String(head, offset, end - offset, Charsets.ISO_8859_1)
        }

        // 3D
        if (head.size >= 12 && at(0, "glTF")) {
            return MagicGuess(FormatRegistry.byId("glb")!!, "glTF binary header")
        }
        if (at(0, "ply") && (head.size < 4 || head[3] == '\n'.code.toByte() || head[3] == '\r'.code.toByte())) {
            return MagicGuess(FormatRegistry.byId("ply")!!, "PLY header")
        }

        // Archives and office documents (zip based)
        if (head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            (head[2] == 0x03.toByte() || head[2] == 0x05.toByte() || head[2] == 0x07.toByte())
        ) {
            val zipKind = probeZip(file)
            if (zipKind != null) return zipKind
            return MagicGuess(FormatRegistry.byId("zip")!!, "ZIP archive signature")
        }
        if (head.size >= 4 && head[0] == 0x37.toByte() && head[1] == 0x7A.toByte() &&
            head[2] == 0xBC.toByte() && head[3] == 0xAF.toByte()
        ) {
            return MagicGuess(FormatRegistry.byId("7z")!!, "7-Zip signature")
        }
        if (head.size >= 6 && head[0] == 0xFD.toByte() && head[1] == 0x37.toByte() &&
            head[2] == 0x7A.toByte() && head[3] == 0x58.toByte() && head[4] == 0x5A.toByte() && head[5] == 0x00.toByte()
        ) {
            return MagicGuess(FormatRegistry.byId("xz")!!, "XZ signature")
        }
        if (head.size >= 3 && at(0, "BZh")) {
            return MagicGuess(FormatRegistry.byId("bz2")!!, "Bzip2 signature")
        }
        if (head.size >= 2 && head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte()) {
            val inside = gzipHead(file)
            if (inside != null) {
                if (inside.size > 262 && String(inside, 257, 5, Charsets.ISO_8859_1) == "ustar") {
                    return MagicGuess(FormatRegistry.byId("tgz")!!, "Gzip containing a TAR archive")
                }
                val text = decodeText(inside)
                if (text != null && (text.contains("<svg", true) || text.contains("<?xml", true)) &&
                    (declared?.id == "svgz" || displayName.endsWith(".svgz", true))
                ) {
                    return MagicGuess(FormatRegistry.byId("svgz")!!, "Gzip containing SVG markup")
                }
            }
            return MagicGuess(FormatRegistry.byId("gz")!!, "Gzip signature")
        }
        if (extended.size > 262 && String(extended, 257, 5, Charsets.ISO_8859_1) == "ustar") {
            return MagicGuess(FormatRegistry.byId("tar")!!, "TAR header at byte 257")
        }

        // PDF
        if (at(0, "%PDF-")) {
            return MagicGuess(FormatRegistry.byId("pdf")!!, "PDF header")
        }

        // Images
        if (head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()) {
            return MagicGuess(FormatRegistry.byId("jpg")!!, "JPEG marker")
        }
        if (head.size >= 8 && head[0] == 0x89.toByte() && at(1, "PNG")) {
            return MagicGuess(FormatRegistry.byId("png")!!, "PNG signature")
        }
        if (head.size >= 6 && (at(0, "GIF87a") || at(0, "GIF89a"))) {
            return MagicGuess(FormatRegistry.byId("gif")!!, "GIF signature")
        }
        if (head.size >= 2 && at(0, "BM") && head.size >= 14) {
            return MagicGuess(FormatRegistry.byId("bmp")!!, "BMP signature")
        }
        if (head.size >= 12 && at(0, "RIFF")) {
            val kind = ascii(8, 4)
            if (kind == "WEBP") return MagicGuess(FormatRegistry.byId("webp")!!, "WebP RIFF container")
            if (kind == "WAVE") return MagicGuess(FormatRegistry.byId("wav")!!, "WAV RIFF container")
        }
        if (head.size >= 4 && at(0, "fLaC")) {
            return MagicGuess(FormatRegistry.byId("flac")!!, "FLAC signature")
        }
        if (head.size >= 12 && at(0, "FORM")) {
            val kind = ascii(8, 4)
            if (kind == "AIFF" || kind == "AIFC") return MagicGuess(FormatRegistry.byId("aiff")!!, "AIFF FORM header")
        }
        if (head.size >= 4 && at(0, "OggS")) {
            val sample = ascii(0, minOf(head.size, 8192))
            if (sample.contains("OpusHead")) {
                return MagicGuess(FormatRegistry.byId("opus")!!, "Ogg Opus stream")
            }
            return MagicGuess(FormatRegistry.byId("ogg")!!, "Ogg stream")
        }
        if (head.size >= 4 && (at(0, "II*\u0000") || at(0, "MM\u0000*"))) {
            val dng = declared?.id == "dng" || displayName.endsWith(".dng", true)
            return if (dng) {
                MagicGuess(FormatRegistry.byId("dng")!!, "DNG/TIFF header")
            } else {
                MagicGuess(FormatRegistry.byId("tiff")!!, "TIFF header")
            }
        }
        if (head.size >= 12 && at(4, "ftyp")) {
            return probeIsoBmff(head, declared)
        }
        if (head.size >= 2 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() &&
            head.size >= 4 && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()
        ) {
            val sample = ascii(0, minOf(head.size, 16384)).lowercase()
            return if (sample.contains("webm")) {
                MagicGuess(FormatRegistry.byId("webm")!!, "EBML WebM header")
            } else {
                MagicGuess(FormatRegistry.byId("mkv")!!, "EBML Matroska header")
            }
        }

        // Audio without a usable header signature
        if (head.size >= 3 && at(0, "ID3")) {
            return MagicGuess(FormatRegistry.byId("mp3")!!, "ID3 audio tag")
        }
        if (isMp3Frame(head)) {
            return MagicGuess(FormatRegistry.byId("mp3")!!, "MPEG audio frame sync")
        }
        if (head.size >= 2 && head[0] == 0xFF.toByte() && (head[1].toInt() and 0xF6) == 0xF0) {
            if (declared?.id == "aac" || displayName.endsWith(".aac", true)) {
                return MagicGuess(FormatRegistry.byId("m4a")!!, "ADTS AAC stream")
            }
        }

        // Binary STL: 80 byte header, 4 byte triangle count, 50 bytes per triangle.
        if (head.size >= 84) {
            val count = uint32(head, 80)
            val expected = 84L + count * 50L
            if (count in 1..20_000_000L && expected == file.length()) {
                return MagicGuess(FormatRegistry.byId("stl")!!, "Binary STL size checksum")
            }
        }
        return null
    }

    private fun probeIsoBmff(head: ByteArray, declared: FormatSpec?): MagicGuess? {
        fun ascii(offset: Int, length: Int): String {
            val end = minOf(head.size, offset + length)
            if (end <= offset) return ""
            return String(head, offset, end - offset, Charsets.ISO_8859_1)
        }
        val major = ascii(8, 4)
        val boxSize = minOf(uint32(head, 0).toInt(), head.size)
        val brands = buildSet {
            add(major)
            var offset = 16
            while (offset + 4 <= boxSize) {
                add(ascii(offset, 4))
                offset += 4
            }
        }
        return when {
            brands.any { it in setOf("avif", "avis") } ->
                MagicGuess(FormatRegistry.byId("avif")!!, "AVIF image brand")
            brands.any { it in setOf("heic", "heix", "hevc", "hevx", "heif", "mif1", "msf1") } ->
                MagicGuess(FormatRegistry.byId("heic")!!, "HEIF image brand $major")
            brands.any { it in setOf("M4A ", "M4B ", "F4A ", "mp4a") } ->
                MagicGuess(FormatRegistry.byId("m4a")!!, "M4A audio brand")
            brands.contains("qt  ") ->
                MagicGuess(FormatRegistry.byId("mov")!!, "QuickTime brand")
            declared?.id == "m4a" || declared?.id == "aac" ->
                MagicGuess(FormatRegistry.byId("m4a")!!, "MP4 container declared as audio")
            else ->
                MagicGuess(FormatRegistry.byId("mp4")!!, "MP4 brand $major")
        }
    }

    private fun probeZip(file: File): MagicGuess? {
        return try {
            ZipFile(file).use { zip ->
                val names = zip.entries().asSequence().take(600).map { it.name.lowercase() }.toSet()
                when {
                    "[content_types].xml" in names && "word/document.xml" in names ->
                        MagicGuess(FormatRegistry.byId("docx")!!, "OOXML Word package")
                    "[content_types].xml" in names && "xl/workbook.xml" in names ->
                        MagicGuess(FormatRegistry.byId("xlsx")!!, "OOXML Excel package")
                    "[content_types].xml" in names && names.any { it.startsWith("ppt/") } ->
                        MagicGuess(FormatRegistry.byId("zip")!!, "Office package (presentation) is not supported")
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun gzipHead(file: File): ByteArray? {
        return try {
            file.inputStream().use { raw ->
                GZIPInputStream(raw, 4096).use { gz -> readUpTo(gz, 1024) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun classifyText(text: String, displayName: String, declared: FormatSpec?): TextGuess? {
        val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (trimmed.isEmpty()) return null
        val lower = trimmed.take(64 * 1024).lowercase()

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            if (lower.contains("\"asset\"") && lower.contains("\"version\"") &&
                (lower.contains("\"meshes\"") || lower.contains("\"nodes\"") || lower.contains("\"scenes\""))
            ) {
                return TextGuess(FormatRegistry.byId("gltf")!!, "glTF JSON document", 0.96f)
            }
            if (looksLikeJson(trimmed)) {
                return TextGuess(FormatRegistry.byId("json")!!, "JSON document", 0.92f)
            }
        }
        if (trimmed.startsWith("<")) {
            if (lower.contains("<svg") && (lower.contains("xmlns") || lower.contains("<?xml"))) {
                return TextGuess(FormatRegistry.byId("svg")!!, "SVG vector markup", 0.98f)
            }
            if (lower.startsWith("<!doctype html") || lower.contains("<html")) {
                return TextGuess(FormatRegistry.byId("html")!!, "HTML document", 0.97f)
            }
            if (lower.startsWith("<?xml") || lower.startsWith("<")) {
                return TextGuess(FormatRegistry.byId("xml")!!, "XML document", 0.9f)
            }
        }
        if (lower.contains("section") && lower.contains("entities") &&
            (lower.contains("acadver") || lower.contains("header") || lower.contains("0\nsection"))
        ) {
            return TextGuess(FormatRegistry.byId("dxf")!!, "DXF sections and entities", 0.93f)
        }
        val lines = trimmed.lineSequence().take(400).toList()
        if (lines.firstOrNull()?.trim() == "ply") {
            return TextGuess(FormatRegistry.byId("ply")!!, "PLY header", 0.98f)
        }
        val first = lines.firstOrNull()?.trim()?.uppercase()
        if (first == "OFF" || first == "COFF" || first == "NOFF") {
            return TextGuess(FormatRegistry.byId("off")!!, "OFF header", 0.95f)
        }
        if (lower.contains("facet normal") && lower.contains("endfacet") &&
            (lower.startsWith("solid") || declared?.id == "stl")
        ) {
            return TextGuess(FormatRegistry.byId("stl")!!, "ASCII STL facets", 0.95f)
        }
        val objVertices = lines.count { it.trimStart().startsWith("v ") }
        val objFaces = lines.count { it.trimStart().startsWith("f ") }
        if (objVertices >= 3 && objFaces >= 1) {
            return TextGuess(FormatRegistry.byId("obj")!!, "OBJ vertices and faces", 0.88f)
        }
        if (declared != null && declared.family == com.universalconverter.pro.core.formats.FormatFamily.DOCUMENT &&
            declared.id in setOf("txt", "md")
        ) {
            return TextGuess(declared, "UTF-8 text", 0.55f)
        }
        if (declared != null && declared.family == com.universalconverter.pro.core.formats.FormatFamily.DATA &&
            declared.id in setOf("csv", "tsv")
        ) {
            return TextGuess(declared, "Delimited text", 0.6f)
        }
        return null
    }

    private fun looksLikeJson(text: String): Boolean {
        val start = text.firstOrNull { !it.isWhitespace() } ?: return false
        if (start != '{' && start != '[') return false
        if (text.length > 64 * 1024) return true
        return text.contains(':') || text.trim().endsWith("[]") || text.trim().endsWith("{}")
    }

    private fun isMp3Frame(bytes: ByteArray): Boolean {
        var offset = 0
        if (bytes.size > 10 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            val size = ((bytes[6].toInt() and 0x7F) shl 21) or
                ((bytes[7].toInt() and 0x7F) shl 14) or
                ((bytes[8].toInt() and 0x7F) shl 7) or
                (bytes[9].toInt() and 0x7F)
            offset = 10 + size
        }
        if (offset + 4 > bytes.size) return false
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return false
        val version = (b1 shr 3) and 0x3
        val layer = (b1 shr 1) and 0x3
        return version != 1 && layer == 1
    }

    private fun decodeText(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        if (bytes.any { it == 0.toByte() }) {
            // Nulls are normal in UTF-16; only accept text that has no other control noise.
            val control = bytes.count { it.toInt() in 1..8 || it.toInt() in 14..31 }
            if (control > bytes.size / 100) return null
        }
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        val utf8 = String(bytes, start, bytes.size - start, Charsets.UTF_8)
        val replacement = utf8.count { it == '\uFFFD' }
        if (replacement > utf8.length / 20) return null
        val control = utf8.count { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
        if (control > utf8.length / 50) return null
        return utf8
    }

    private fun readUpTo(input: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(minOf(max, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (total < max) {
            val read = input.read(buffer, 0, minOf(buffer.size, max - total))
            if (read <= 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    private fun uint32(bytes: ByteArray, offset: Int): Long {
        if (offset + 4 > bytes.size) return 0
        return (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }
}