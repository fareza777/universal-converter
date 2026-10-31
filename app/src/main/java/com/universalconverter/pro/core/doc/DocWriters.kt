package com.universalconverter.pro.core.doc

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocWriters {

    fun toText(model: DocModel): String = model.plainText()

    fun toMarkdown(model: DocModel): String {
        val out = StringBuilder()
        for (block in model.blocks) {
            when (block) {
                is DocBlock.Heading -> out.append("#".repeat(block.level)).append(' ').append(block.text).append("\n\n")
                is DocBlock.Paragraph -> {
                    if (block.bullet) out.append("- ") else Unit
                    out.append(block.text).append("\n\n")
                }
                is DocBlock.Quote -> out.append("> ").append(block.text.replace("\n", "\n> ")).append("\n\n")
                is DocBlock.Code -> out.append("```\n").append(block.text).append("\n```\n\n")
                is DocBlock.Table -> {
                    if (block.rows.isNotEmpty()) {
                        val width = block.rows.maxOf { it.size }
                        val rows = block.rows.map { row -> List(width) { row.getOrElse(it) { "" } } }
                        fun cell(value: String) = value.replace("|", "\\|").replace("\n", "<br>")
                        out.append("| ").append(rows.first().joinToString(" | ") { cell(it) }).append(" |\n")
                        out.append("| ").append(List(width) { "---" }.joinToString(" | ")).append(" |\n")
                        rows.drop(1).forEach { row ->
                            out.append("| ").append(row.joinToString(" | ") { cell(it) }).append(" |\n")
                        }
                        out.append('\n')
                    }
                }
            }
        }
        return out.toString().trimEnd() + "\n"
    }

    fun toHtml(model: DocModel): String {
        val out = StringBuilder(4096)
        val title = escapeHtml(model.title)
        out.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
        out.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        out.append("<title>").append(title).append("</title>\n<style>")
        out.append(
            "body{margin:0;background:#F5F0E7;color:#17140F;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Manrope,sans-serif;" +
                "line-height:1.65;font-size:17px}main{max-width:760px;margin:0 auto;padding:64px 24px 96px}" +
                "h1,h2,h3,h4,h5,h6{font-family:Georgia,'Times New Roman',serif;font-weight:600;line-height:1.15;margin:1.8em 0 .6em}" +
                "h1{font-size:42px;margin-top:0}h2{font-size:30px}h3{font-size:23px}" +
                "p{margin:0 0 1.1em}blockquote{margin:1.4em 0;padding:.2em 0 .2em 20px;border-left:3px solid #C24E2A;color:#60594C;font-style:italic}" +
                "pre{background:#ECE4D6;padding:18px 20px;border-radius:12px;overflow:auto;font-size:14px;line-height:1.5}" +
                "code{font-family:'SF Mono',Consolas,monospace;font-size:.92em}" +
                "ul{padding-left:22px}li{margin:.35em 0}" +
                "table{border-collapse:collapse;width:100%;margin:1.5em 0;font-size:14px}" +
                "th,td{border:1px solid #DCD2C1;padding:8px 10px;text-align:left;vertical-align:top}th{background:#ECE4D6}" +
                "hr{border:none;border-top:1px solid #DCD2C1;margin:2em 0}" +
                "@media (prefers-color-scheme:dark){body{background:#121110;color:#F4EEE3}th,td{border-color:#37322C}" +
                "th{background:#272320}pre{background:#1A1816}blockquote{color:#A9A093}}</style>\n</head>\n<body><main>\n"
        )
        for (block in model.blocks) {
            when (block) {
                is DocBlock.Heading -> {
                    val level = block.level.coerceIn(1, 6)
                    out.append("<h$level>").append(escapeHtml(block.text)).append("</h$level>\n")
                }
                is DocBlock.Paragraph -> {
                    if (block.bullet) {
                        out.append("<ul><li>").append(escapeHtml(block.text)).append("</li></ul>\n")
                    } else {
                        out.append("<p>").append(escapeHtml(block.text).replace("\n", "<br>")).append("</p>\n")
                    }
                }
                is DocBlock.Quote -> out.append("<blockquote>")
                    .append(escapeHtml(block.text).replace("\n", "<br>")).append("</blockquote>\n")
                is DocBlock.Code -> out.append("<pre><code>").append(escapeHtml(block.text)).append("</code></pre>\n")
                is DocBlock.Table -> {
                    out.append("<table>\n")
                    block.rows.forEachIndexed { index, row ->
                        val tag = if (index == 0) "th" else "td"
                        out.append("<tr>")
                        row.forEach { cell -> out.append("<$tag>").append(escapeHtml(cell)).append("</$tag>") }
                        out.append("</tr>\n")
                    }
                    out.append("</table>\n")
                }
            }
        }
        out.append("</main></body></html>\n")
        return out.toString()
    }

    fun writeDocx(model: DocModel, file: File) {
        val documentXml = buildString(4096) {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
            append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">")
            append("<w:body>")
            for (block in model.blocks) {
                when (block) {
                    is DocBlock.Heading -> {
                        val size = when (block.level) { 1 -> 40; 2 -> 32; 3 -> 28; else -> 26 }
                        append(paragraph(block.text, bold = true, size = size, outline = block.level - 1, before = 240, after = 120))
                    }
                    is DocBlock.Paragraph -> {
                        if (block.bullet) {
                            append(paragraph("• " + block.text, indent = 360, hanging = 360, after = 80))
                        } else {
                            append(paragraph(block.text))
                        }
                    }
                    is DocBlock.Quote -> append(paragraph(block.text, italic = true, color = "60594C", indent = 360))
                    is DocBlock.Code -> append(paragraph(block.text, monospace = true, size = 20, indent = 240, shade = "F4F1EA"))
                    is DocBlock.Table -> append(tableXml(block.rows))
                }
            }
            append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>")
            append("<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr>")
            append("</w:body></w:document>")
        }
        val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            .plus("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            .plus("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            .plus("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            .plus("<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>")
            .plus("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
            .plus("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
            .plus("</Types>")
        val rootRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            .plus("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            .plus("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>")
            .plus("<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>")
            .plus("<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>")
            .plus("</Relationships>")
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val core = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            .plus("<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">")
            .plus("<dc:title>").plus(escapeXml(model.title)).plus("</dc:title>")
            .plus("<dc:creator>Universal Converter</dc:creator>")
            .plus("<cp:lastModifiedBy>Universal Converter</cp:lastModifiedBy>")
            .plus("<dcterms:created xsi:type=\"dcterms:W3CDTF\">").plus(now).plus("</dcterms:created>")
            .plus("<dcterms:modified xsi:type=\"dcterms:W3CDTF\">").plus(now).plus("</dcterms:modified>")
            .plus("</cp:coreProperties>")
        val app = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            .plus("<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">")
            .plus("<Application>Universal Converter</Application>")
            .plus("</Properties>")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("_rels/.rels", rootRels)
            put("word/document.xml", documentXml)
            put("docProps/core.xml", core)
            put("docProps/app.xml", app)
        }
    }

    private fun paragraph(
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
        size: Int = 24,
        monospace: Boolean = false,
        color: String? = null,
        indent: Int = 0,
        hanging: Int = 0,
        before: Int = 0,
        after: Int = 120,
        outline: Int? = null,
        shade: String? = null,
    ): String {
        val pPr = buildString {
            append("<w:pPr>")
            append("<w:spacing w:before=\"").append(before).append("\" w:after=\"").append(after).append("\"/>")
            if (indent > 0) append("<w:ind w:left=\"").append(indent).append("\" w:hanging=\"").append(hanging).append("\"/>")
            if (outline != null) append("<w:outlineLvl w:val=\"").append(outline).append("\"/>")
            if (shade != null) append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"").append(shade).append("\"/>")
            append("</w:pPr>")
        }
        val rPr = buildString {
            append("<w:rPr>")
            if (bold) append("<w:b/>")
            if (italic) append("<w:i/>")
            if (color != null) append("<w:color w:val=\"").append(color).append("\"/>")
            append("<w:sz w:val=\"").append(size).append("\"/><w:szCs w:val=\"").append(size).append("\"/>")
            if (monospace) append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\" w:cs=\"Consolas\"/>")
            append("</w:rPr>")
        }
        val runs = StringBuilder()
        val lines = text.split('\n')
        lines.forEachIndexed { index, line ->
            if (index > 0) runs.append("<w:br/>")
            runs.append("<w:t xml:space=\"preserve\">").append(escapeXml(line)).append("</w:t>")
        }
        return "<w:p>$pPr<w:r>$rPr$runs</w:r></w:p>"
    }

    private fun tableXml(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val width = rows.maxOf { it.size }
        val out = StringBuilder()
        out.append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>")
        for (side in listOf("top", "left", "bottom", "right", "insideH", "insideV")) {
            out.append("<w:").append(side).append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"D8D1C4\"/>")
        }
        out.append("</w:tblBorders></w:tblPr>")
        rows.forEachIndexed { rowIndex, row ->
            out.append("<w:tr>")
            for (column in 0 until width) {
                val value = row.getOrElse(column) { "" }
                out.append("<w:tc><w:tcPr><w:tcW w:w=\"2400\" w:type=\"dxa\"/></w:tcPr>")
                out.append(paragraph(value, bold = rowIndex == 0, size = 22, after = 40))
                out.append("</w:tc>")
            }
            out.append("</w:tr>")
        }
        out.append("</w:tbl>")
        return out.toString()
    }

    fun writeXlsx(table: TableModel, file: File) {
        val sheetName = sanitizeSheetName(table.name)
        val rows = table.normalize()
        val sheetData = buildString(2048) {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            append("<sheetData>")
            rows.rows.forEachIndexed { rowIndex, row ->
                append("<row r=\"").append(rowIndex + 1).append("\">")
                row.forEachIndexed { columnIndex, value ->
                    val reference = columnName(columnIndex) + (rowIndex + 1)
                    if (value.isNotBlank() && value.toDoubleOrNull() != null && !value.startsWith("0") && value.length < 18) {
                        append("<c r=\"").append(reference).append("\"><v>").append(value).append("</v></c>")
                    } else if (value.isNotEmpty()) {
                        append("<c r=\"").append(reference).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(escapeXml(value)).append("</t></is></c>")
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            .plus("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            .plus("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            .plus("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            .plus("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
            .plus("<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
            .plus("</Types>")
        val rootRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            .plus("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            .plus("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>")
            .plus("</Relationships>")
        val workbook = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            .plus("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
            .plus("<sheets><sheet name=\"").plus(escapeXml(sheetName)).plus("\" sheetId=\"1\" r:id=\"rId1\"/></sheets>")
            .plus("</workbook>")
        val workbookRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            .plus("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            .plus("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>")
            .plus("</Relationships>")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("_rels/.rels", rootRels)
            put("xl/workbook.xml", workbook)
            put("xl/_rels/workbook.xml.rels", workbookRels)
            put("xl/worksheets/sheet1.xml", sheetData)
        }
    }

    fun writeDelimited(table: TableModel, file: File, delimiter: Char) {
        val out = StringBuilder()
        fun field(value: String): String {
            val needsQuotes = value.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }
            return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
        }
        table.normalize().rows.forEach { row ->
            out.append(row.joinToString(delimiter.toString()) { field(it) }).append("\r\n")
        }
        file.writeText(out.toString(), Charsets.UTF_8)
    }

    private fun sanitizeSheetName(name: String): String {
        val cleaned = name.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().ifBlank { "Sheet1" }
        return cleaned.take(31)
    }

    private fun columnName(index: Int): String {
        var value = index
        val out = StringBuilder()
        while (true) {
            out.insert(0, ('A' + value % 26))
            value = value / 26 - 1
            if (value < 0) break
        }
        return out.toString()
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        for (char in value) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                '\t', '\n', '\r' -> append(char)
                else -> if (char.code >= 32) append(char)
            }
        }
    }

    private fun escapeHtml(value: String): String = buildString(value.length) {
        for (char in value) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(char)
            }
        }
    }
}