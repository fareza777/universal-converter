package com.universalconverter.pro.core.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.convert.PdfPageSize
import com.universalconverter.pro.core.doc.DocBlock
import com.universalconverter.pro.core.doc.DocModel
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Lays out a document into a clean, paginated PDF with the app's editorial palette. */
class DocPdfWriter {

    private val ink = Color.rgb(23, 20, 15)
    private val muted = Color.rgb(96, 89, 76)
    private val ember = Color.rgb(180, 71, 36)
    private val paper = Color.WHITE
    private val rule = Color.rgb(220, 210, 193)
    private val parchment = Color.rgb(244, 241, 234)

    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = 10.5f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = 8.6f; typeface = Typeface.MONOSPACE
    }
    private val italic = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted; textSize = 10.5f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
    }
    private val cell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = 8.2f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val cellHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = 8.2f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val headings = listOf(
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); color = ink; textSize = 23f },
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); color = ink; textSize = 17f },
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); color = ink; textSize = 13.5f },
    )
    private val eyebrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ember; textSize = 8f; letterSpacing = 0.18f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = parchment }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = rule; style = Paint.Style.STROKE; strokeWidth = 0.6f
    }
    private val emberFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ember }

    private lateinit var document: PdfDocument
    private lateinit var model: DocModel
    private var pageWidth = 595f
    private var pageHeight = 842f
    private var margin = 52f
    private val footerSpace = 30f
    private var contentWidth = 0f
    private lateinit var page: PdfDocument.Page
    private lateinit var canvas: Canvas
    private var y = 0f
    private var pageNumber = 1
    private var firstPage = true

    fun write(model: DocModel, output: File, options: ConversionOptions, progress: (Float, String) -> Unit = { _, _ -> }) {
        this.model = model
        when (options.pdfPage) {
            PdfPageSize.LETTER -> { pageWidth = 612f; pageHeight = 792f }
            PdfPageSize.FIT, PdfPageSize.A4 -> { pageWidth = 595f; pageHeight = 842f }
        }
        contentWidth = pageWidth - margin * 2
        document = PdfDocument()
        pageNumber = 1
        firstPage = true
        beginPage()
        for ((index, block) in model.blocks.withIndex()) {
            when (block) {
                is DocBlock.Heading -> drawHeading(block)
                is DocBlock.Paragraph -> drawParagraph(block)
                is DocBlock.Quote -> drawQuote(block)
                is DocBlock.Code -> drawCode(block)
                is DocBlock.Table -> drawTable(block.rows)
            }
            progress((index + 1).toFloat() / max(1, model.blocks.size) * 0.9f, "Laying out page")
        }
        endPage()
        output.outputStream().use { document.writeTo(it) }
        document.close()
        progress(1f, "PDF ready")
    }

    private fun beginPage() {
        val info = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageNumber).create()
        page = document.startPage(info)
        canvas = page.canvas
        canvas.drawColor(paper)
        y = margin
        if (firstPage) {
            drawMasthead()
            firstPage = false
        }
    }

    private fun drawMasthead() {
        canvas.drawText("CONVERTED DOCUMENT", margin, y + 5f, eyebrow)
        y += 16f
        if (model.title.isNotBlank()) {
            val text = trimToWidth(model.title, headings[0], contentWidth)
            y += 30f
            canvas.drawText(text, margin, y, headings[0])
            y += 14f
        }
        canvas.drawLine(margin, y, pageWidth - margin, y, stroke)
        y += 22f
    }

    private fun endPage() {
        canvas.drawLine(margin, pageHeight - margin + 3f, pageWidth - margin, pageHeight - margin + 3f, stroke)
        canvas.drawText("UNIVERSAL CONVERTER", margin, pageHeight - margin + 17f, eyebrow)
        val label = "PAGE $pageNumber"
        val width = eyebrow.measureText(label)
        canvas.drawText(label, pageWidth - margin - width, pageHeight - margin + 17f, eyebrow)
        document.finishPage(page)
        pageNumber++
    }

    private fun ensure(space: Float) {
        if (y + space > pageHeight - margin - footerSpace) {
            endPage()
            beginPage()
        }
    }

    private fun drawHeading(block: DocBlock.Heading) {
        val paint = headings[(block.level - 1).coerceIn(0, 2)]
        val lines = wrap(block.text, paint, contentWidth)
        val lineHeight = paint.textSize * 1.2f
        val before = if (block.level == 1) 18f else 12f
        ensure(before + lines.size * lineHeight + 12f)
        y += before
        for (line in lines) {
            y += lineHeight
            canvas.drawText(line, margin, y, paint)
        }
        if (block.level == 1) {
            y += 8f
            canvas.drawRect(margin, y, margin + min(54f, contentWidth * 0.2f), y + 2f, emberFill)
            y += 4f
        }
        y += 8f
    }

    private fun drawParagraph(block: DocBlock.Paragraph) {
        val paint = body
        val indent = if (block.bullet) 15f else 0f
        val lines = wrap(block.text, paint, contentWidth - indent)
        val lineHeight = paint.textSize * 1.52f
        for ((index, line) in lines.withIndex()) {
            ensure(lineHeight)
            if (index == 0 && block.bullet) {
                val bullet = Paint(body).apply { color = ember; textSize = 12f }
                canvas.drawText("•", margin, y + lineHeight * 0.78f, bullet)
            }
            y += lineHeight
            canvas.drawText(line, margin + indent, y - lineHeight * 0.26f, paint)
        }
        y += 5f
    }

    private fun drawQuote(block: DocBlock.Quote) {
        val lines = wrap(block.text, italic, contentWidth - 18f)
        val lineHeight = italic.textSize * 1.55f
        for (line in lines) {
            ensure(lineHeight)
            canvas.drawRect(margin, y + 1f, margin + 2.5f, y + lineHeight, emberFill)
            y += lineHeight
            canvas.drawText(line, margin + 13f, y - lineHeight * 0.26f, italic)
        }
        y += 8f
    }

    private fun drawCode(block: DocBlock.Code) {
        val lines = wrapCode(block.text, mono, contentWidth - 22f)
        val lineHeight = mono.textSize * 1.5f
        val padding = 9f
        var index = 0
        while (index < lines.size) {
            val availableLines = ((pageHeight - margin - footerSpace - y - padding * 2) / lineHeight).toInt().coerceAtLeast(1)
            val chunkSize = min(lines.size - index, availableLines)
            val blockHeight = chunkSize * lineHeight + padding * 2
            ensure(blockHeight + 6f)
            canvas.drawRoundRect(RectF(margin, y, pageWidth - margin, y + blockHeight), 5f, 5f, fill)
            var ty = y + padding + mono.textSize
            for (lineIndex in index until index + chunkSize) {
                canvas.drawText(lines[lineIndex], margin + 11f, ty, mono)
                ty += lineHeight
            }
            y += blockHeight + 9f
            index += chunkSize
            if (index < lines.size) {
                endPage()
                beginPage()
            }
        }
    }

    private fun drawTable(rows: List<List<String>>) {
        if (rows.isEmpty()) return
        val columns = rows.maxOf { it.size }.coerceAtMost(12)
        if (columns == 0) return
        val columnWidth = contentWidth / columns
        val cellPadding = 4.5f
        val lineHeight = 10.5f

        fun wrapRow(row: List<String>, paint: Paint): List<List<String>> =
            List(columns) { index -> wrap(row.getOrElse(index) { "" }, paint, columnWidth - cellPadding * 2) }

        val headerWrapped = wrapRow(rows.first(), cellHeader)
        val headerHeight = max(1, headerWrapped.maxOf { it.size }) * lineHeight + cellPadding * 2

        fun drawHeader() {
            ensure(headerHeight)
            canvas.drawRect(margin, y, margin + contentWidth, y + headerHeight, fill)
            var x = margin
            for (column in 0 until columns) {
                canvas.drawRect(x, y, x + columnWidth, y + headerHeight, stroke)
                var ty = y + cellPadding + cellHeader.textSize
                for (line in headerWrapped[column]) {
                    canvas.drawText(line, x + cellPadding, ty, cellHeader)
                    ty += lineHeight
                }
                x += columnWidth
            }
            y += headerHeight
        }

        drawHeader()
        for (row in rows.drop(1)) {
            val wrapped = wrapRow(row, cell)
            val rowHeight = max(1, wrapped.maxOf { it.size }) * lineHeight + cellPadding * 2
            if (y + rowHeight > pageHeight - margin - footerSpace) {
                endPage()
                beginPage()
                drawHeader()
            }
            var x = margin
            for (column in 0 until columns) {
                canvas.drawRect(x, y, x + columnWidth, y + rowHeight, stroke)
                var ty = y + cellPadding + cell.textSize
                for (line in wrapped[column]) {
                    canvas.drawText(line, x + cellPadding, ty, cell)
                    ty += lineHeight
                }
                x += columnWidth
            }
            y += rowHeight
        }
        y += 13f
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            if (paragraph.isEmpty()) {
                lines += ""
                continue
            }
            var current = StringBuilder()
            for (word in paragraph.split(' ')) {
                if (word.isEmpty()) continue
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                    current = StringBuilder(candidate)
                } else {
                    lines += current.toString()
                    current = StringBuilder(word)
                }
                while (paint.measureText(current.toString()) > maxWidth) {
                    val fit = paint.breakText(current.toString(), true, maxWidth, null).coerceAtLeast(1)
                    lines += current.substring(0, fit)
                    current.delete(0, fit)
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
        }
        return lines.ifEmpty { listOf("") }
    }

    private fun wrapCode(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        for (line in text.split('\n')) {
            if (paint.measureText(line) <= maxWidth) {
                lines += line
            } else {
                var remaining = line
                while (remaining.isNotEmpty()) {
                    val fit = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                    lines += remaining.substring(0, fit)
                    remaining = remaining.substring(fit)
                }
            }
        }
        return lines.ifEmpty { listOf("") }
    }

    private fun trimToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val end = paint.breakText(text, true, maxWidth, null).coerceAtLeast(1)
        return text.substring(0, end).trimEnd() + "…"
    }
}