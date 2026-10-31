package com.universalconverter.pro.core.formats

object FormatRegistry {

    val known: List<FormatSpec> = listOf(
        // 3D models
        FormatSpec("obj", "OBJ", listOf("obj"), listOf("model/obj", "text/plain"), FormatFamily.MESH,
            note = "Wavefront geometry. Materials and textures are not embedded."),
        FormatSpec("stl", "STL", listOf("stl"), listOf("model/stl", "application/sla"), FormatFamily.MESH,
            note = "Triangle mesh for printing. No color, no units."),
        FormatSpec("ply", "PLY", listOf("ply"), listOf("model/ply"), FormatFamily.MESH,
            note = "Stanford polygon format. Vertex colors are preserved."),
        FormatSpec("off", "OFF", listOf("off"), listOf("model/off", "text/plain"), FormatFamily.MESH,
            note = "Object File Format. Plain text triangles and polygons."),
        FormatSpec("glb", "GLB", listOf("glb"), listOf("model/gltf-binary"), FormatFamily.MESH,
            note = "Binary glTF 2.0. Best all-round 3D delivery format."),
        FormatSpec("gltf", "glTF", listOf("gltf"), listOf("model/gltf+json"), FormatFamily.MESH,
            note = "glTF 2.0 with an embedded buffer. Single-file and portable."),

        // CAD and vector
        FormatSpec("dxf", "DXF", listOf("dxf"), listOf("image/vnd.dxf", "application/dxf"), FormatFamily.CAD,
            readable = true, writable = false,
            note = "Drawing exchange format. Lines, arcs, circles, polylines, ellipses and text. Block inserts are not expanded."),
        FormatSpec("svg", "SVG", listOf("svg"), listOf("image/svg+xml"), FormatFamily.VECTOR,
            note = "Scalable vector graphics, rasterized at any resolution."),
        FormatSpec("svgz", "SVGZ", listOf("svgz"), listOf("image/svg+xml"), FormatFamily.VECTOR,
            note = "Gzip-compressed SVG."),

        // Images
        FormatSpec("jpg", "JPEG", listOf("jpg", "jpeg", "jpe", "jfif"), listOf("image/jpeg"), FormatFamily.IMAGE,
            note = "Lossy photo format. No transparency."),
        FormatSpec("png", "PNG", listOf("png"), listOf("image/png"), FormatFamily.IMAGE,
            note = "Lossless with transparency."),
        FormatSpec("webp", "WebP", listOf("webp"), listOf("image/webp"), FormatFamily.IMAGE,
            note = "Small files, transparency, great for the web."),
        FormatSpec("bmp", "BMP", listOf("bmp", "dib"), listOf("image/bmp"), FormatFamily.IMAGE,
            note = "Uncompressed bitmap. Large but universal."),
        FormatSpec("gif", "GIF", listOf("gif"), listOf("image/gif"), FormatFamily.IMAGE,
            note = "256 colors, optional animation. Import uses the first frame."),
        FormatSpec("tiff", "TIFF", listOf("tiff", "tif"), listOf("image/tiff"), FormatFamily.IMAGE,
            note = "Lossless, 8 or 16-bit, uncompressed, LZW or Deflate."),
        FormatSpec("dng", "DNG", listOf("dng"), listOf("image/x-adobe-dng", "image/dng"), FormatFamily.RAW,
            writable = false,
            note = "Adobe RAW container. Universal Converter decodes the embedded camera preview; a DNG with no preview is rejected."),
        FormatSpec("heic", "HEIC", listOf("heic", "heif", "hif"), listOf("image/heic", "image/heif"), FormatFamily.IMAGE,
            writable = false,
            note = "Apple/HEVC photo. Decoding depends on the device codec."),
        FormatSpec("avif", "AVIF", listOf("avif"), listOf("image/avif"), FormatFamily.IMAGE,
            writable = false,
            note = "AV1 photo. Decoding requires Android 12 or a vendor codec."),

        // PDF
        FormatSpec("pdf", "PDF", listOf("pdf"), listOf("application/pdf"), FormatFamily.PDF,
            note = "Portable document. Rendering, text extraction, merge and split."),

        // Documents and data
        FormatSpec("docx", "DOCX", listOf("docx"), listOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), FormatFamily.DOCUMENT,
            note = "Word document with headings, lists, emphasis and tables."),
        FormatSpec("xlsx", "XLSX", listOf("xlsx"), listOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), FormatFamily.SPREADSHEET,
            note = "Excel workbook. First sheet is used when reading."),
        FormatSpec("txt", "TXT", listOf("txt", "log"), listOf("text/plain"), FormatFamily.DOCUMENT,
            note = "UTF-8 plain text."),
        FormatSpec("md", "Markdown", listOf("md", "markdown"), listOf("text/markdown", "text/plain"), FormatFamily.DOCUMENT,
            note = "Headings, lists, emphasis, code and tables."),
        FormatSpec("html", "HTML", listOf("html", "htm"), listOf("text/html"), FormatFamily.DOCUMENT,
            note = "Self-contained HTML document."),
        FormatSpec("csv", "CSV", listOf("csv"), listOf("text/csv"), FormatFamily.DATA,
            note = "Comma separated values."),
        FormatSpec("tsv", "TSV", listOf("tsv"), listOf("text/tab-separated-values"), FormatFamily.DATA,
            note = "Tab separated values."),
        FormatSpec("json", "JSON", listOf("json"), listOf("application/json"), FormatFamily.DATA,
            note = "Structured data. Arrays of objects convert cleanly to tables."),
        FormatSpec("xml", "XML", listOf("xml"), listOf("application/xml", "text/xml"), FormatFamily.DATA,
            note = "Hierarchical markup data."),

        // Audio
        FormatSpec("mp3", "MP3", listOf("mp3"), listOf("audio/mpeg"), FormatFamily.AUDIO,
            writable = false,
            note = "Standard lossy audio. Decoding is built in."),
        FormatSpec("m4a", "M4A", listOf("m4a", "aac"), listOf("audio/mp4", "audio/aac"), FormatFamily.AUDIO,
            note = "AAC audio in an MP4 container."),
        FormatSpec("wav", "WAV", listOf("wav"), listOf("audio/wav", "audio/x-wav"), FormatFamily.AUDIO,
            note = "Uncompressed PCM audio."),
        FormatSpec("flac", "FLAC", listOf("flac"), listOf("audio/flac"), FormatFamily.AUDIO,
            note = "Lossless compressed audio."),
        FormatSpec("ogg", "OGG", listOf("ogg", "oga"), listOf("audio/ogg"), FormatFamily.AUDIO,
            note = "Ogg container, usually Vorbis."),
        FormatSpec("opus", "Opus", listOf("opus"), listOf("audio/opus", "audio/ogg"), FormatFamily.AUDIO,
            note = "Low-latency Ogg Opus."),
        FormatSpec("aiff", "AIFF", listOf("aiff", "aif", "aifc"), listOf("audio/aiff", "audio/x-aiff"), FormatFamily.AUDIO,
            readable = true, writable = false,
            note = "Apple uncompressed PCM audio."),

        // Video
        FormatSpec("mp4", "MP4", listOf("mp4", "m4v"), listOf("video/mp4"), FormatFamily.VIDEO,
            note = "H.264 + AAC MP4. Compress, trim, or hand off to another format."),
        FormatSpec("mov", "MOV", listOf("mov", "qt"), listOf("video/quicktime"), FormatFamily.VIDEO,
            writable = false,
            note = "QuickTime video. Read through the device media stack."),
        FormatSpec("mkv", "MKV", listOf("mkv"), listOf("video/x-matroska"), FormatFamily.VIDEO,
            writable = false,
            note = "Matroska video. Read through the device media stack."),
        FormatSpec("webm", "WebM", listOf("webm"), listOf("video/webm"), FormatFamily.VIDEO,
            writable = false,
            note = "WebM video. Read through the device media stack."),
        FormatSpec("3gp", "3GP", listOf("3gp"), listOf("video/3gpp"), FormatFamily.VIDEO,
            readable = true, writable = false,
            note = "Legacy mobile video."),

        // Archives
        FormatSpec("zip", "ZIP", listOf("zip"), listOf("application/zip"), FormatFamily.ARCHIVE,
            note = "Standard archive. Extract, inspect or repack."),
        FormatSpec("tar", "TAR", listOf("tar"), listOf("application/x-tar"), FormatFamily.ARCHIVE,
            note = "Uncompressed tape archive."),
        FormatSpec("tgz", "TAR.GZ", listOf("tgz", "tar.gz"), listOf("application/gzip"), FormatFamily.ARCHIVE,
            note = "Gzip-compressed tar archive."),
        FormatSpec("gz", "GZ", listOf("gz"), listOf("application/gzip"), FormatFamily.ARCHIVE,
            note = "Single-file gzip archive."),
        FormatSpec("bz2", "BZ2", listOf("bz2"), listOf("application/x-bzip2"), FormatFamily.ARCHIVE,
            writable = false,
            note = "Bzip2 compressed archive."),
        FormatSpec("xz", "XZ", listOf("xz"), listOf("application/x-xz"), FormatFamily.ARCHIVE,
            writable = false,
            note = "XZ compressed archive."),
        FormatSpec("7z", "7Z", listOf("7z"), listOf("application/x-7z-compressed"), FormatFamily.ARCHIVE,
            writable = false,
            note = "7-Zip archive. Read only."),
    )

    private val byIdMap = known.associateBy { it.id }
    private val byExtensionMap = buildMap {
        known.forEach { spec ->
            spec.extensions.forEach { put(it.lowercase(), spec) }
        }
        put("tif", byIdMap.getValue("tiff"))
        put("jpeg", byIdMap.getValue("jpg"))
        put("heif", byIdMap.getValue("heic"))
        put("tar.gz", byIdMap.getValue("tgz"))
    }
    private val byMimeMap = buildMap {
        known.forEach { spec -> spec.mimeTypes.forEach { put(it.lowercase(), spec) } }
    }

    fun byId(id: String?): FormatSpec? = id?.let(byIdMap::get)

    fun byExtension(extension: String?): FormatSpec? =
        extension?.lowercase()?.trimStart('.')?.let(byExtensionMap::get)

    fun byMime(mime: String?): FormatSpec? {
        val cleaned = mime?.substringBefore(';')?.lowercase()?.trim() ?: return null
        return byMimeMap[cleaned]
    }

    fun extensionOf(id: String, originalName: String? = null): String {
        if (id == "tgz") return "tar.gz"
        if (id == "tiff") return "tiff"
        if (id == "jpg") return "jpg"
        if (id == "gltf") return "gltf"
        byId(id)?.primaryExtension?.let { return it }
        return originalName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() } ?: "bin"
    }

    fun canonicalExtension(fileName: String): String {
        val lower = fileName.lowercase()
        val compound = listOf("tar.gz", "tar.bz2", "tar.xz").firstOrNull { lower.endsWith(".$it") }
        if (compound != null) return compound
        return lower.substringAfterLast('.', "")
    }

    fun findForFileName(fileName: String): FormatSpec? = byExtension(canonicalExtension(fileName))

    fun extensionList(): List<String> = byExtensionMap.keys.sorted()
}