package com.universalconverter.pro.core.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.doc.DocFormatException
import com.universalconverter.pro.core.doc.DocModel
import com.universalconverter.pro.core.doc.DocParsers
import com.universalconverter.pro.core.doc.DocWriters
import com.universalconverter.pro.core.doc.JsonTools
import com.universalconverter.pro.core.doc.TableModel
import com.universalconverter.pro.core.image.ImageFormatException
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class DocumentEngine {

    fun convert(
        source: File,
        sourceFormat: String,
        targetId: String,
        options: ConversionOptions,
        output: File,
        progress: (Float, String) -> Unit,
    ): EngineReport {
        val steps = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        progress(0.05f, "Reading document")
        if (sourceFormat == "pdf") {
            return convertPdf(source, targetId, options, output, progress)
        }
        if (targetId == "txt" && (sourceFormat == "json")) {
            output.writeText(DocParsers.jsonPretty(source), Charsets.UTF_8)
            return EngineReport(listOf("Pretty-printed JSON"))
        }
        if (targetId == "txt" && sourceFormat == "xml") {
            output.writeText(DocParsers.xmlPretty(source), Charsets.UTF_8)
            return EngineReport(listOf("Indented XML"))
        }
        if (targetId == "json" && sourceFormat == "xml") {
            output.writeText(DocParsers.xmlToJson(source), Charsets.UTF_8)
            return EngineReport(listOf("XML tree converted to JSON"))
        }

        val tableSource = sourceFormat in setOf("xlsx", "csv", "tsv")
        val table = when {
            targetId in setOf("csv", "tsv", "xlsx") || (sourceFormat == "json" && targetId in setOf("csv", "xlsx")) -> when (sourceFormat) {
                "xlsx" -> DocParsers.readXlsx(source)
                "csv" -> DocParsers.parseDelimited(source, ',')
                "tsv" -> DocParsers.parseDelimited(source, '\t')
                "json" -> DocParsers.jsonTable(source)
                else -> null
            }
            sourceFormat in setOf("xlsx", "csv", "tsv") -> when (sourceFormat) {
                "xlsx" -> DocParsers.readXlsx(source)
                "csv" -> DocParsers.parseDelimited(source, ',')
                else -> DocParsers.parseDelimited(source, '\t')
            }
            else -> null
        }

        if (targetId in setOf("csv", "tsv", "xlsx")) {
            val data = table ?: throw DocFormatException("This source does not contain tabular data.")
            progress(0.55f, "Writing table")
            when (targetId) {
                "csv" -> DocWriters.writeDelimited(data, output, ',')
                "tsv" -> DocWriters.writeDelimited(data, output, '\t')
                "xlsx" -> DocWriters.writeXlsx(data, output)
            }
            steps += "${data.rowCount} rows × ${data.columnCount} columns"
            return EngineReport(steps, warnings)
        }
        if (targetId == "json" && tableSource) {
            val data = table ?: throw DocFormatException("This source does not contain tabular data.")
            output.writeText(JsonTools.tableToJson(data), Charsets.UTF_8)
            steps += "Table serialized as an array of objects"
            return EngineReport(steps, warnings)
        }

        val document: DocModel = when {
            table != null -> table.toDocument(table.name)
            sourceFormat == "docx" -> DocParsers.readDocx(source)
            sourceFormat == "md" -> DocParsers.readMarkdown(source)
            sourceFormat == "html" -> DocParsers.readHtml(source)
            sourceFormat == "txt" -> DocParsers.readText(source)
            sourceFormat == "json" -> DocModel(source.nameWithoutExtension, listOf(com.universalconverter.pro.core.doc.DocBlock.Code(DocParsers.jsonPretty(source))))
            else -> throw DocFormatException("$sourceFormat documents are not supported here.")
        }
        progress(0.55f, "Building ${targetId.uppercase()}")
        when (targetId) {
            "txt" -> output.writeText(DocWriters.toText(document), Charsets.UTF_8)
            "md" -> output.writeText(DocWriters.toMarkdown(document), Charsets.UTF_8)
            "html" -> output.writeText(DocWriters.toHtml(document), Charsets.UTF_8)
            "docx" -> DocWriters.writeDocx(document, output)
            "pdf" -> DocPdfWriter().write(document, output, options, progress)
            else -> throw DocFormatException("Unsupported document target $targetId.")
        }
        steps += "${document.blocks.size} content blocks"
        progress(1f, "Document ready")
        return EngineReport(steps, warnings)
    }

    private fun convertPdf(
        source: File,
        targetId: String,
        options: ConversionOptions,
        output: File,
        progress: (Float, String) -> Unit,
    ): EngineReport {
        return when (targetId) {
            "txt" -> {
                val text = try {
                    PDDocument.load(source).use { document ->
                        val stripper = PDFTextStripper().apply { sortByPosition = true }
                        stripper.getText(document)
                    }
                } catch (error: InvalidPasswordException) {
                    throw DocFormatException("This PDF is password-protected and cannot be read.", error)
                } catch (error: Exception) {
                    throw DocFormatException("The PDF text could not be extracted.", error)
                }
                if (text.isBlank()) {
                    throw DocFormatException("This PDF has no embedded text. Scanned pages would need OCR, which is not included.")
                }
                output.writeText(text, Charsets.UTF_8)
                EngineReport(listOf("Selectable text extracted with PDFBox"))
            }
            "pdf_png", "pdf_jpg" -> {
                renderPdfPages(source, targetId == "pdf_png", options, output, progress)
            }
            else -> throw DocFormatException("Unsupported PDF target $targetId.")
        }
    }

    private fun renderPdfPages(
        source: File,
        png: Boolean,
        options: ConversionOptions,
        output: File,
        progress: (Float, String) -> Unit,
    ): EngineReport {
        val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Exception) {
            descriptor.close()
            throw DocFormatException("This PDF is damaged or password-protected.", error)
        }
        val warnings = mutableListOf<String>()
        val targetEdge = if (options.imageMaxEdge > 0) options.imageMaxEdge else 2200
        var pageCount = 0
        try {
            ZipOutputStream(output.outputStream().buffered()).use { zip ->
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = minOf(
                            targetEdge.toFloat() / max(page.width, page.height),
                            3.2f,
                        ).coerceAtLeast(0.5f)
                        var width = max(1, (page.width * scale).roundToInt())
                        var height = max(1, (page.height * scale).roundToInt())
                        val maxPixels = 32_000_000L
                        if (width.toLong() * height > maxPixels) {
                            val shrink = Math.sqrt(maxPixels.toDouble() / (width.toLong() * height)).toFloat()
                            width = max(1, (width * shrink).roundToInt())
                            height = max(1, (height * shrink).roundToInt())
                            warnings += "Page ${index + 1} was capped to fit the memory budget."
                        }
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val extension = if (png) "png" else "jpg"
                        val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                        zip.putNextEntry(ZipEntry("page-${(index + 1).toString().padStart(3, '0')}.$extension"))
                        bitmap.compress(format, options.imageQuality.coerceIn(35, 100), zip)
                        zip.closeEntry()
                        bitmap.recycle()
                        pageCount++
                    }
                    progress((index + 1).toFloat() / renderer.pageCount * 0.9f, "Rendering page ${index + 1}")
                }
            }
        } finally {
            renderer.close()
            descriptor.close()
        }
        return EngineReport(
            steps = listOf("$pageCount PDF pages rendered as ${if (png) "PNG" else "JPEG"} inside a ZIP"),
            warnings = warnings,
        )
    }
}