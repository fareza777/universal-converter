package com.universalconverter.pro.core.convert

import com.universalconverter.pro.core.formats.FormatFamily
import com.universalconverter.pro.core.formats.FormatRegistry
import com.universalconverter.pro.core.formats.FormatSpec

enum class TargetKind {
    FILE,
    PAGE_SET_ZIP,
    FOLDER,
}

data class TargetSpec(
    val id: String,
    val label: String,
    val blurb: String,
    val extension: String,
    val family: FormatFamily,
    val formatId: String?,
    val kind: TargetKind = TargetKind.FILE,
) {
    val format: FormatSpec? get() = FormatRegistry.byId(formatId)
}

object TargetMatrix {

    private fun target(
        formatId: String,
        label: String,
        blurb: String,
        extension: String = FormatRegistry.extensionOf(formatId),
        family: FormatFamily = FormatRegistry.byId(formatId)?.family ?: FormatFamily.DOCUMENT,
        kind: TargetKind = TargetKind.FILE,
    ) = TargetSpec(formatId, label, blurb, extension, family, formatId, kind)

    private val meshTargets = listOf(
        target("glb", "GLB", "Portable binary 3D. Best for sharing and viewers."),
        target("gltf", "glTF", "Readable JSON with embedded geometry."),
        target("stl", "STL", "Triangle mesh for 3D printing and CAD."),
        target("obj", "OBJ", "Classic editable mesh with normals."),
        target("ply", "PLY", "Research and scan format with vertex colors."),
        target("off", "OFF", "Plain text geometry for simple tools."),
    )

    private val svgTargets = listOf(
        target("png", "PNG", "Transparent, crisp raster at the chosen size."),
        target("jpg", "JPEG", "Small preview with a white background."),
        target("webp", "WebP", "Small, sharp web image."),
        target("pdf", "PDF", "Single-page vector-scaled document."),
    )

    private val imageTargets = listOf(
        target("jpg", "JPEG", "Photos, small files, no transparency."),
        target("png", "PNG", "Lossless with transparency."),
        target("webp", "WebP", "Modern web image with alpha."),
        target("tiff", "TIFF", "Lossless archive or print master."),
        target("bmp", "BMP", "Uncompressed bitmap."),
        target("gif", "GIF", "256 colors. Animated inputs use the first frame."),
        target("pdf", "PDF", "A clean A4 page of the image."),
    )

    private val docTargets = mapOf(
        "docx" to listOf(
            target("txt", "TXT", "Plain text, UTF-8."),
            target("md", "Markdown", "Headings, lists and emphasis."),
            target("html", "HTML", "Self-contained readable page."),
            target("pdf", "PDF", "Print-ready A4 document."),
        ),
        "xlsx" to listOf(
            target("csv", "CSV", "Comma separated values."),
            target("tsv", "TSV", "Tab separated values."),
            target("json", "JSON", "Array of row objects."),
            target("pdf", "PDF", "Table on an A4 page."),
        ),
        "txt" to listOf(
            target("md", "Markdown", "Turn paragraphs into Markdown."),
            target("html", "HTML", "Readable web page."),
            target("docx", "DOCX", "Editable Word document."),
            target("pdf", "PDF", "Print-ready A4 document."),
        ),
        "md" to listOf(
            target("html", "HTML", "Rendered, self-contained page."),
            target("txt", "TXT", "Flatten to plain text."),
            target("docx", "DOCX", "Editable Word document."),
            target("pdf", "PDF", "Print-ready A4 document."),
        ),
        "html" to listOf(
            target("txt", "TXT", "Readable text without markup."),
            target("md", "Markdown", "Headings, links and lists."),
            target("docx", "DOCX", "Editable Word document."),
            target("pdf", "PDF", "Print-ready A4 document."),
        ),
        "csv" to listOf(
            target("tsv", "TSV", "Tab separated values."),
            target("json", "JSON", "Array of row objects."),
            target("xlsx", "XLSX", "Editable spreadsheet."),
            target("pdf", "PDF", "Table on an A4 page."),
        ),
        "tsv" to listOf(
            target("csv", "CSV", "Comma separated values."),
            target("json", "JSON", "Array of row objects."),
            target("xlsx", "XLSX", "Editable spreadsheet."),
            target("pdf", "PDF", "Table on an A4 page."),
        ),
        "json" to listOf(
            target("csv", "CSV", "Flatten an array of objects into rows."),
            target("xlsx", "XLSX", "Editable spreadsheet."),
            target("txt", "TXT", "Pretty-printed JSON."),
        ),
        "xml" to listOf(
            target("json", "JSON", "Structured JSON tree."),
            target("txt", "TXT", "Readable XML with indentation."),
        ),
    )

    fun targetsFor(sourceFormatId: String?, caps: DeviceCaps = DeviceCaps.Universal): List<TargetSpec> {
        val source = FormatRegistry.byId(sourceFormatId) ?: return emptyList()
        return when (source.id) {
            "obj", "stl", "ply", "off", "glb", "gltf" -> meshTargets
            "dxf" -> listOf(
                TargetSpec("svg", "SVG", "Scalable drawing with layer colors.", "svg", FormatFamily.VECTOR, "svg"),
                TargetSpec("pdf", "PDF", "Scaleable print sheet.", "pdf", FormatFamily.PDF, "pdf"),
                TargetSpec("png", "PNG", "High resolution drawing preview.", "png", FormatFamily.IMAGE, "png"),
                TargetSpec("jpg", "JPEG", "Quick preview with white paper.", "jpg", FormatFamily.IMAGE, "jpg"),
            )
            "svg" -> buildList {
                addAll(svgTargets)
                add(TargetSpec("svgz", "SVGZ", "Gzip-compressed SVG.", "svgz", FormatFamily.VECTOR, "svgz"))
            }
            "svgz" -> buildList {
                add(TargetSpec("svg", "SVG", "Decompressed editable SVG.", "svg", FormatFamily.VECTOR, "svg"))
                addAll(svgTargets)
            }
            "dng", "heic", "avif" -> listOf(
                target("jpg", "JPEG", "Develop the photo into a universal image."),
                target("png", "PNG", "Lossless conversion."),
                target("webp", "WebP", "Small web-ready photo."),
                target("tiff", "TIFF", "Lossless archival master."),
                target("pdf", "PDF", "Photo on an A4 page."),
            )
            "jpg", "png", "webp", "bmp", "tiff", "gif" -> imageTargets
            "pdf" -> listOf(
                TargetSpec("pdf_png", "PNG pages", "Every page as PNG, zipped together.", "zip", FormatFamily.IMAGE, "png", TargetKind.PAGE_SET_ZIP),
                TargetSpec("pdf_jpg", "JPEG pages", "Every page as JPEG, zipped together.", "zip", FormatFamily.IMAGE, "jpg", TargetKind.PAGE_SET_ZIP),
                target("txt", "TXT", "Extract selectable text from the PDF."),
            )
            in docTargets.keys -> docTargets[source.id].orEmpty()
            "mp3", "m4a", "wav", "flac", "ogg", "opus", "aiff" -> buildList {
                if (caps.hasAac) add(target("m4a", "M4A", "AAC audio for phones, cars and the web."))
                add(target("wav", "WAV", "Uncompressed PCM for editing."))
            }
            "mp4", "mov", "mkv", "webm", "3gp" -> buildList {
                if (caps.videoAvcEncoder && caps.hasAac) {
                    add(target("mp4", "MP4", "Compressed H.264 video with AAC sound."))
                }
                add(TargetSpec("gif", "GIF", "A silent, looping clip for chat.", "gif", FormatFamily.VIDEO, "gif"))
                add(TargetSpec("contact_sheet", "Contact sheet", "Twelve frames in one JPEG strip.", "jpg", FormatFamily.IMAGE, "jpg"))
                if (caps.hasAac) add(TargetSpec("m4a", "Audio (M4A)", "Keep only the soundtrack.", "m4a", FormatFamily.AUDIO, "m4a"))
                add(TargetSpec("wav", "Audio (WAV)", "Soundtrack as uncompressed PCM.", "wav", FormatFamily.AUDIO, "wav"))
            }
            "zip", "tar", "tgz", "gz", "bz2", "xz", "7z" -> buildList {
                add(TargetSpec("extract", "Extract files", "Unpack everything into a vault folder.", "", FormatFamily.ARCHIVE, null, TargetKind.FOLDER))
                if (source.id != "zip") add(target("zip", "ZIP", "Repack as a widely supported ZIP."))
                if (source.id != "tar") add(target("tar", "TAR", "Repack as an uncompressed TAR."))
                if (source.id != "tgz") add(target("tgz", "TAR.GZ", "Repack and compress with gzip."))
            }
            else -> emptyList()
        }.distinctBy { it.id }
    }

    fun defaultTarget(sourceFormatId: String?, caps: DeviceCaps): TargetSpec? {
        val targets = targetsFor(sourceFormatId, caps)
        if (targets.isEmpty()) return null
        val source = FormatRegistry.byId(sourceFormatId)
        return when (source?.id) {
            "xlsx" -> targets.firstOrNull { it.id == "pdf" } ?: targets.first()
            "docx", "txt", "md", "html" -> targets.firstOrNull { it.id == "pdf" } ?: targets.first()
            "csv", "tsv" -> targets.firstOrNull { it.id == "xlsx" } ?: targets.first()
            "json" -> targets.firstOrNull { it.id == "txt" } ?: targets.first()
            "xml" -> targets.firstOrNull { it.id == "json" } ?: targets.first()
            else -> when (source?.family) {
                FormatFamily.MESH -> targets.firstOrNull { it.id == "glb" } ?: targets.first()
                FormatFamily.RAW, FormatFamily.IMAGE -> targets.firstOrNull { it.id == "jpg" } ?: targets.first()
                FormatFamily.AUDIO -> targets.firstOrNull { it.id == "m4a" } ?: targets.first()
                FormatFamily.VIDEO -> targets.firstOrNull { it.id == "mp4" } ?: targets.first()
                FormatFamily.ARCHIVE -> targets.firstOrNull { it.id == "extract" } ?: targets.first()
                FormatFamily.CAD -> targets.firstOrNull { it.id == "svg" } ?: targets.first()
                FormatFamily.VECTOR -> targets.firstOrNull { it.id == "png" } ?: targets.first()
                FormatFamily.PDF -> targets.firstOrNull { it.id == "pdf_png" } ?: targets.first()
                else -> targets.first()
            }
        }
    }
}