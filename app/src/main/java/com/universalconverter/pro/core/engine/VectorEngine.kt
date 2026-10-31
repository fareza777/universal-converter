package com.universalconverter.pro.core.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.caverock.androidsvg.SVG
import com.universalconverter.pro.core.cad.DxfReader
import com.universalconverter.pro.core.cad.DxfSvg
import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.convert.PdfPageSize
import com.universalconverter.pro.core.image.ImageFormatException
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class VectorEngine(private val imageEngine: ImageEngine) {

    fun convert(
        source: File,
        sourceFormat: String,
        targetId: String,
        options: ConversionOptions,
        output: File,
        progress: (Float, String) -> Unit,
    ): EngineReport {
        val warnings = mutableListOf<String>()
        val steps = mutableListOf<String>()
        progress(0.05f, "Reading drawing")
        val svgSource: SvgSource = when (sourceFormat) {
            "dxf" -> {
                val drawing = DxfReader.read(source)
                warnings += drawing.warnings
                val rendered = DxfSvg.render(drawing)
                steps += "${drawing.entityCount} DXF entities"
                steps += "Layer colours and line weights preserved"
                SvgSource(rendered.svg.toByteArray(Charsets.UTF_8), rendered.width, rendered.height)
            }
            "svgz" -> {
                val text = gunzip(source).toString(Charsets.UTF_8)
                val svg = parseSvg(text.toByteArray(Charsets.UTF_8))
                SvgSource(text.toByteArray(Charsets.UTF_8), dimensionOf(svg).first, dimensionOf(svg).second)
            }
            else -> {
                val bytes = source.readBytes()
                val svg = parseSvg(bytes)
                SvgSource(bytes, dimensionOf(svg).first, dimensionOf(svg).second)
            }
        }

        when (targetId) {
            "svg" -> {
                if (svgSource.isGzip) throw ImageFormatException("This drawing is already compressed.")
                output.writeBytes(svgSource.bytes)
                steps += "SVG markup"
            }
            "svgz" -> {
                GZIPOutputStream(output.outputStream().buffered()).use { gzip ->
                    gzip.write(svgSource.bytes)
                }
                steps += "Gzip-compressed SVG"
            }
            "pdf" -> {
                writePdf(svgSource, output, options)
                steps += "Vector PDF page ${options.pdfPage.label}"
            }
            "png", "jpg", "webp" -> {
                val bitmap = renderBitmap(svgSource, options, targetId)
                progress(0.6f, "Rendering pixels")
                imageEngine.encode(bitmap, source, sourceFormat, targetId, options, output, progress)
                bitmap.recycle()
                steps += "Raster ${targetId.uppercase()} at ${svcName(targetId)}"
            }
            else -> throw ImageFormatException("Unsupported drawing target $targetId.")
        }
        progress(1f, "Drawing ready")
        return EngineReport(steps, warnings)
    }

    private fun svcName(targetId: String) = targetId.uppercase()

    private fun renderBitmap(source: SvgSource, options: ConversionOptions, targetId: String): Bitmap {
        val aspect = source.width / source.height
        val desired = when {
            options.imageMaxEdge > 0 -> options.imageMaxEdge.toFloat()
            options.preset == com.universalconverter.pro.core.convert.QualityPreset.ARCHIVE -> 3072f
            else -> 2048f
        }
        var width: Int
        var height: Int
        if (aspect >= 1f) {
            width = desired.roundToInt()
            height = max(1, (width / aspect).roundToInt())
        } else {
            height = desired.roundToInt()
            width = max(1, (height * aspect).roundToInt())
        }
        val maxPixels = 30_000_000L
        if (width.toLong() * height > maxPixels) {
            val scale = Math.sqrt(maxPixels.toDouble() / (width.toLong() * height)).toFloat()
            width = max(1, (width * scale).roundToInt())
            height = max(1, (height * scale).roundToInt())
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (targetId == "jpg") canvas.drawColor(Color.WHITE)
        val svg = parseSvg(source.bytes)
        val picture = svg.renderToPicture()
        canvas.save()
        canvas.scale(width / source.width, height / source.height)
        canvas.drawPicture(picture)
        canvas.restore()
        return bitmap
    }

    private fun writePdf(source: SvgSource, output: File, options: ConversionOptions) {
        val aspect = source.width / source.height
        val pageWidth: Int
        val pageHeight: Int
        when (options.pdfPage) {
            PdfPageSize.A4 -> if (aspect >= 1f) { pageWidth = 842; pageHeight = 595 } else { pageWidth = 595; pageHeight = 842 }
            PdfPageSize.LETTER -> if (aspect >= 1f) { pageWidth = 792; pageHeight = 612 } else { pageWidth = 612; pageHeight = 792 }
            PdfPageSize.FIT -> {
                val longest = 842f
                if (aspect >= 1f) {
                    pageWidth = longest.roundToInt()
                    pageHeight = max(80, (longest / aspect).roundToInt())
                } else {
                    pageHeight = longest.roundToInt()
                    pageWidth = max(80, (longest * aspect).roundToInt())
                }
            }
        }
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        val margin = if (options.pdfPage == PdfPageSize.FIT) 0f else 28f
        val availableWidth = pageWidth - margin * 2
        val availableHeight = pageHeight - margin * 2
        val scale = minOf(availableWidth / source.width, availableHeight / source.height)
        val drawWidth = source.width * scale
        val drawHeight = source.height * scale
        val left = (pageWidth - drawWidth) / 2f
        val top = (pageHeight - drawHeight) / 2f
        val svg = parseSvg(source.bytes)
        val picture = svg.renderToPicture()
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(drawWidth / source.width, drawHeight / source.height)
        canvas.drawPicture(picture)
        canvas.restore()
        document.finishPage(page)
        output.outputStream().use { document.writeTo(it) }
        document.close()
    }

    private fun parseSvg(bytes: ByteArray): SVG {
        return try {
            SVG.getFromInputStream(BufferedInputStream(bytes.inputStream()))
        } catch (error: Exception) {
            throw ImageFormatException("The SVG could not be parsed. It may use unsupported features.", error)
        }
    }

    private fun dimensionOf(svg: SVG): Pair<Float, Float> {
        var width = svg.documentWidth
        var height = svg.documentHeight
        if (width <= 0f || height <= 0f) {
            val box = svg.documentViewBox
            if (box != null && box.width() > 0f && box.height() > 0f) {
                width = box.width()
                height = box.height()
            }
        }
        if (width <= 0f || height <= 0f) {
            width = 1024f
            height = 1024f
        }
        return width to height
    }

    private fun gunzip(file: File): ByteArray =
        GZIPInputStream(file.inputStream().buffered()).use { it.readBytes() }

    private data class SvgSource(
        val bytes: ByteArray,
        val width: Float,
        val height: Float,
        val isGzip: Boolean = false,
    ) {
        constructor(svg: SVG) : this(ByteArray(0), 1f, 1f)
    }
}