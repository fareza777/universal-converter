package com.universalconverter.pro.core.doc

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

object DocParsers {

    fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        val offset = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        return String(bytes, offset, bytes.size - offset, Charsets.UTF_8)
    }

    fun readText(file: File): DocModel {
        val text = decodeText(file.readBytes()).replace("\r\n", "\n").replace('\r', '\n')
        val blocks = mutableListOf<DocBlock>()
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
        for (paragraph in paragraphs) {
            val cleaned = paragraph.trim()
            if (cleaned.isNotEmpty()) blocks += DocBlock.Paragraph(sanitizeDocText(cleaned))
        }
        return DocModel(file.nameWithoutExtension, blocks)
    }

    fun readMarkdown(file: File): DocModel {
        val lines = decodeText(file.readBytes()).replace("\r\n", "\n").replace('\r', '\n').lines()
        val blocks = mutableListOf<DocBlock>()
        var index = 0
        var paragraph = StringBuilder()
        fun flushParagraph() {
            if (paragraph.isNotBlank()) {
                blocks += DocBlock.Paragraph(cleanInline(paragraph.toString().trim()))
                paragraph = StringBuilder()
            }
        }
        while (index < lines.size) {
            val line = lines[index]
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                flushParagraph()
                val fence = line.trimStart().take(3)
                val code = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trimStart().startsWith(fence)) {
                    code.append(lines[index]).append('\n')
                    index++
                }
                blocks += DocBlock.Code(code.toString().trimEnd())
                index++
                continue
            }
            val heading = Regex("^(#{1,6})\\s+(.*)$").find(line)
            if (heading != null) {
                flushParagraph()
                blocks += DocBlock.Heading(heading.groupValues[1].length, cleanInline(heading.groupValues[2].trim()))
                index++
                continue
            }
            val bullet = Regex("^\\s*[-*+]\\s+(.*)$").find(line)
            val numbered = Regex("^\\s*\\d+[.)]\\s+(.*)$").find(line)
            if (bullet != null || numbered != null) {
                flushParagraph()
                blocks += DocBlock.Paragraph(cleanInline((bullet ?: numbered)!!.groupValues[1]), bullet = true)
                index++
                continue
            }
            if (line.trimStart().startsWith(">")) {
                flushParagraph()
                val quote = StringBuilder()
                while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                    quote.append(lines[index].trimStart().removePrefix(">").trim()).append(' ')
                    index++
                }
                blocks += DocBlock.Quote(cleanInline(quote.toString().trim()))
                continue
            }
            if (line.contains('|') && index + 1 < lines.size && isMarkdownTableSeparator(lines[index + 1])) {
                flushParagraph()
                val rows = mutableListOf<List<String>>()
                rows += splitMarkdownRow(line)
                index += 2
                while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
                    rows += splitMarkdownRow(lines[index])
                    index++
                }
                blocks += DocBlock.Table(rows.map { row -> row.map { cleanInline(it) } })
                continue
            }
            if (line.isBlank()) {
                flushParagraph()
            } else {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line.trim())
            }
            index++
        }
        flushParagraph()
        return DocModel(file.nameWithoutExtension, blocks)
    }

    private fun isMarkdownTableSeparator(line: String): Boolean {
        val trimmed = line.trim().trim('|')
        if (trimmed.isEmpty()) return false
        return trimmed.split('|').all { cell -> cell.trim().matches(Regex(":?-{2,}:?")) }
    }

    private fun splitMarkdownRow(line: String): List<String> {
        var text = line.trim()
        if (text.startsWith("|")) text = text.substring(1)
        if (text.endsWith("|")) text = text.dropLast(1)
        return text.split('|').map { it.trim() }
    }

    private fun cleanInline(text: String): String {
        var value = text
        value = value.replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
        value = value.replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
        value = value.replace("**", "").replace("__", "").replace("*", "").replace("_", "")
        value = value.replace("`", "")
        value = value.replace("\\", "")
        return value
    }

    fun readHtml(file: File): DocModel {
        val document = Jsoup.parse(file.readText(Charsets.UTF_8))
        val title = document.title().ifBlank { file.nameWithoutExtension }
        val blocks = mutableListOf<DocBlock>()
        fun visit(element: Element) {
            when (element.tagName().lowercase()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val level = element.tagName().substring(1).toIntOrNull() ?: 1
                    if (element.text().isNotBlank()) blocks += DocBlock.Heading(level, element.text())
                }
                "p" -> if (element.text().isNotBlank()) blocks += DocBlock.Paragraph(element.text())
                "pre" -> if (element.text().isNotBlank()) blocks += DocBlock.Code(element.wholeText().trimEnd())
                "blockquote" -> if (element.text().isNotBlank()) blocks += DocBlock.Quote(element.text())
                "ul", "ol" -> {
                    for (child in element.children()) {
                        if (child.tagName().equals("li", true)) {
                            val ownText = child.ownText().ifBlank { child.text() }
                            if (ownText.isNotBlank()) blocks += DocBlock.Paragraph(ownText.trim(), bullet = true)
                            for (nested in child.children()) {
                                if (nested.tagName().equals("ul", true) || nested.tagName().equals("ol", true)) visit(nested)
                            }
                        }
                    }
                }
                "table" -> {
                    val rows = mutableListOf<List<String>>()
                    for (tr in element.select("tr")) {
                        val cells = tr.children().filter { it.tagName().equals("th", true) || it.tagName().equals("td", true) }
                        if (cells.isNotEmpty()) rows += cells.map { it.text() }
                    }
                    if (rows.isNotEmpty()) blocks += DocBlock.Table(rows)
                }
                "body", "html", "main", "article", "section", "div" -> {
                    for (child in element.children()) visit(child)
                }
                else -> {
                    if (element.children().none { isBlockElement(it) } && element.text().isNotBlank()) {
                        blocks += DocBlock.Paragraph(element.text())
                    } else {
                        for (child in element.children()) visit(child)
                    }
                }
            }
        }
        visit(document.body())
        if (blocks.isEmpty()) blocks += DocBlock.Paragraph(document.text().ifBlank { "Empty HTML document" })
        return DocModel(title, blocks)
    }

    private fun isBlockElement(element: Element): Boolean = element.tagName().lowercase() in setOf(
        "h1", "h2", "h3", "h4", "h5", "h6", "p", "pre", "blockquote", "ul", "ol", "table", "div", "section", "article", "main",
    )

    fun readDocx(file: File): DocModel {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml")
                ?: throw DocFormatException("This DOCX is missing word/document.xml.")
            val xml = zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8)
            val document = Jsoup.parse(xml, "", Parser.xmlParser())
            val blocks = mutableListOf<DocBlock>()
            fun visit(element: Element) {
                when (element.tagName().lowercase()) {
                    "w:p" -> {
                        val style = element.getElementsByTag("w:pStyle").firstOrNull()?.attr("w:val").orEmpty()
                        val outline = element.getElementsByTag("w:outlineLvl").firstOrNull()?.attr("w:val")?.toIntOrNull()
                        val text = element.text().trim()
                        if (text.isNotEmpty()) {
                            val heading = when {
                                style.lowercase().contains("title") -> 1
                                style.lowercase().contains("heading") -> style.lastOrNull()?.digitToIntOrNull()?.coerceIn(1, 6)
                                outline != null -> (outline + 1).coerceIn(1, 6)
                                else -> null
                            }
                            if (heading != null) {
                                blocks += DocBlock.Heading(heading, text)
                            } else {
                                val hasNumbering = element.getElementsByTag("w:numPr").isNotEmpty()
                                val literalBullet = text.startsWith("• ")
                                val listStyle = style.contains("list", true)
                                blocks += DocBlock.Paragraph(
                                    if (literalBullet) text.removePrefix("• ").trimStart() else text,
                                    bullet = hasNumbering || literalBullet || listStyle,
                                )
                            }
                        }
                    }
                    "w:tbl" -> {
                        val rows = mutableListOf<List<String>>()
                        for (tr in element.getElementsByTag("w:tr")) {
                            val cells = tr.children().filter { it.tagName().equals("w:tc", true) }
                            if (cells.isNotEmpty()) rows += cells.map { it.text().trim() }
                        }
                        if (rows.isNotEmpty()) blocks += DocBlock.Table(rows)
                    }
                    "w:body", "w:document", "w:sdt", "w:sdtcontent", "w:tc" -> {
                        for (child in element.children()) visit(child)
                    }
                }
            }
            val root = document.children().firstOrNull()
                ?: throw DocFormatException("This DOCX has no document root.")
            visit(root)
            val title = try {
                zip.getEntry("docProps/core.xml")?.let { coreEntry ->
                    val coreXml = zip.getInputStream(coreEntry).use { it.readBytes() }.toString(Charsets.UTF_8)
                    Jsoup.parse(coreXml, "", Parser.xmlParser()).getElementsByTag("dc:title").text()
                }
            } catch (_: Exception) {
                null
            }
            return DocModel(title.orEmpty(), blocks)
        }
    }

    fun readXlsx(file: File): TableModel {
        ZipFile(file).use { zip ->
            val shared = ArrayList<String>()
            zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
                val xml = zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8)
                val document = Jsoup.parse(xml, "", Parser.xmlParser())
                for (si in document.getElementsByTag("si")) {
                    shared += si.getElementsByTag("t").joinToString("") { it.text() }
                }
            }
            val sheetEntry = zip.entries().asSequence()
                .filter { it.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
                .sortedBy { it.name }
                .firstOrNull()
                ?: throw DocFormatException("This XLSX has no worksheet.")
            val xml = zip.getInputStream(sheetEntry).use { it.readBytes() }.toString(Charsets.UTF_8)
            val document = Jsoup.parse(xml, "", Parser.xmlParser())
            val rows = mutableListOf<List<String>>()
            var maxColumn = 0
            var rowIndex = 0
            for (rowElement in document.getElementsByTag("row")) {
                val rAttr = rowElement.attr("r").toIntOrNull()
                if (rAttr != null && rAttr > 0) rowIndex = rAttr - 1
                val cells = sortedMapOf<Int, String>()
                var lastColumn = -1
                for (cell in rowElement.children().filter { it.tagName().equals("c", true) }) {
                    val ref = cell.attr("r")
                    val column = if (ref.isNotEmpty()) columnIndex(ref) else lastColumn + 1
                    lastColumn = column
                    val type = cell.attr("t")
                    val raw = cell.getElementsByTag("v").firstOrNull()?.text()
                    val inline = cell.getElementsByTag("is").firstOrNull()?.let { isElement ->
                        isElement.getElementsByTag("t").joinToString("") { it.text() }
                    }
                    val value = when (type) {
                        "s" -> raw?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                        "inlineStr" -> inline.orEmpty()
                        "b" -> if (raw == "1") "TRUE" else "FALSE"
                        "str" -> raw.orEmpty()
                        else -> raw.orEmpty()
                    }
                    cells[column] = value
                    maxColumn = maxOf(maxColumn, column + 1)
                }
                if (cells.isNotEmpty()) {
                    while (rows.size <= rowIndex) rows.add(emptyList())
                    rows[rowIndex] = List(maxColumn) { cells[it] ?: "" }
                    rowIndex++
                }
                if (rows.size > 100_000) throw DocFormatException("This spreadsheet has more than 100,000 rows.")
            }
            val width = maxColumn.coerceAtMost(256)
            val normalized = rows.map { row -> List(width) { row.getOrElse(it) { "" } } }.filter { row -> row.any { it.isNotEmpty() } }
            if (normalized.isEmpty()) throw DocFormatException("This spreadsheet has no data.")
            return TableModel(file.nameWithoutExtension, normalized)
        }
    }

    private fun columnIndex(ref: String): Int {
        var column = 0
        for (char in ref) {
            if (char in 'A'..'Z') column = column * 26 + (char - 'A' + 1)
            else if (char in 'a'..'z') column = column * 26 + (char - 'a' + 1)
            else break
        }
        return (column - 1).coerceAtLeast(0)
    }

    fun parseDelimited(file: File, delimiter: Char): TableModel {
        val text = decodeText(file.readBytes()).replace("\r\n", "\n").replace('\r', '\n')
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val char = text[i]
            if (quoted) {
                if (char == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else quoted = false
                } else field.append(char)
            } else {
                when (char) {
                    '"' -> quoted = true
                    delimiter -> {
                        row += field.toString()
                        field.setLength(0)
                    }
                    '\n' -> {
                        row += field.toString()
                        field.setLength(0)
                        rows += row
                        row = mutableListOf()
                    }
                    else -> field.append(char)
                }
            }
            i++
            if (rows.size > 100_000) throw DocFormatException("This table has more than 100,000 rows.")
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row
        }
        val cleaned = rows.filter { rowValues -> rowValues.any { it.isNotEmpty() } }
        val width = cleaned.maxOfOrNull { it.size } ?: 0
        if (width == 0) throw DocFormatException("This table is empty.")
        return TableModel(file.nameWithoutExtension, cleaned.map { rowValues -> List(width) { rowValues.getOrElse(it) { "" } } })
    }

    fun jsonTable(file: File): TableModel {
        val root = JsonTools.parse(file)
        return JsonTools.toTable(root, file.nameWithoutExtension)
    }

    fun jsonPretty(file: File): String = JsonTools.toPrettyString(JsonTools.parse(file))

    fun xmlToJson(file: File): String {
        return try {
            val document = Jsoup.parse(file.readText(Charsets.UTF_8), "", Parser.xmlParser())
            val root = document.children().firstOrNull() ?: throw DocFormatException("This XML has no root element.")
            val json = JSONObject().put(root.tagName(), JsonTools.elementToJson(root))
            json.toString(2)
        } catch (error: DocFormatException) {
            throw error
        } catch (error: Exception) {
            throw DocFormatException("The XML could not be converted to JSON.", error)
        }
    }

    fun xmlPretty(file: File): String {
        val document = Jsoup.parse(file.readText(Charsets.UTF_8), "", Parser.xmlParser())
        document.outputSettings().prettyPrint(true).indentAmount(2).charset(Charsets.UTF_8)
        return document.outerHtml()
    }
}

object JsonTools {

    fun parse(file: File): Any {
        val text = DocParsers.decodeText(file.readBytes())
        return try {
            if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
        } catch (error: Exception) {
            throw DocFormatException("The JSON is not valid.", error)
        }
    }

    fun toPrettyString(value: Any): String = when (value) {
        is JSONObject -> value.toString(2)
        is JSONArray -> value.toString(2)
        else -> value.toString()
    }

    fun toTable(root: Any, name: String): TableModel {
        val array = when (root) {
            is JSONArray -> root
            is JSONObject -> {
                val candidate = root.keys().asSequence().mapNotNull { root.opt(it) }.firstOrNull {
                    it is JSONArray && it.length() > 0 && it.optJSONObject(0) != null
                }
                candidate as? JSONArray ?: throw DocFormatException("JSON must be an array of objects to become a table.")
            }
            else -> throw DocFormatException("JSON must be an array of objects to become a table.")
        }
        if (array.length() == 0) throw DocFormatException("The JSON array is empty.")
        if (array.optJSONObject(0) == null) {
            // A flat array becomes a single column.
            val rows = array.length()
            return TableModel(name, List(rows) { listOf(stringify(array.opt(it))) })
        }
        val headers = LinkedHashMap<String, Unit>()
        for (i in 0 until array.length()) {
            val objectValue = array.optJSONObject(i) ?: continue
            objectValue.keys().forEach { headers[it] = Unit }
        }
        val headerRow = headers.keys.toList()
        val rows = mutableListOf<List<String>>()
        rows += headerRow
        for (i in 0 until array.length()) {
            val objectValue = array.optJSONObject(i) ?: continue
            rows += headerRow.map { key -> stringify(objectValue.opt(key)) }
        }
        return TableModel(name, rows)
    }

    fun stringify(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is JSONObject -> value.toString()
        is JSONArray -> value.toString()
        is String -> value
        is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        is Float -> if (value % 1.0f == 0.0f) value.toLong().toString() else value.toString()
        else -> value.toString()
    }

    fun tableToJson(table: TableModel): String {
        if (table.rows.isEmpty()) return "[]"
        val header = table.rows.first()
        val array = JSONArray()
        for (row in table.rows.drop(1)) {
            val objectValue = JSONObject()
            header.forEachIndexed { index, key ->
                val safeKey = key.ifBlank { "column_${index + 1}" }
                var uniqueKey = safeKey
                var suffix = 2
                while (objectValue.has(uniqueKey)) uniqueKey = "${safeKey}_${suffix++}"
                objectValue.put(uniqueKey, row.getOrElse(index) { "" })
            }
            array.put(objectValue)
        }
        return array.toString(2)
    }

    fun elementToJson(element: Element): Any {
        val objectValue = JSONObject()
        for (attribute in element.attributes()) {
            objectValue.put("@" + attribute.key, attribute.value)
        }
        val children = element.children()
        val grouped = children.groupBy { it.tagName() }
        for ((name, group) in grouped) {
            if (group.size == 1) objectValue.put(name, elementToJson(group.first()))
            else objectValue.put(name, JSONArray().apply { group.forEach { put(elementToJson(it)) } })
        }
        val text = element.ownText().trim()
        if (text.isNotEmpty()) {
            if (children.isEmpty() && objectValue.length() == 0) return text
            objectValue.put("#text", text)
        }
        return if (objectValue.length() == 0) "" else objectValue
    }
}

class DocFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)