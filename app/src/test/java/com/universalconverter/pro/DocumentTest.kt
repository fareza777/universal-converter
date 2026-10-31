package com.universalconverter.pro

import com.universalconverter.pro.core.doc.DocBlock
import com.universalconverter.pro.core.doc.DocModel
import com.universalconverter.pro.core.doc.DocParsers
import com.universalconverter.pro.core.doc.DocWriters
import com.universalconverter.pro.core.doc.JsonTools
import com.universalconverter.pro.core.doc.TableModel
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DocumentTest {

    private fun dir(): File = File("build/tmp/doc-test").also { it.mkdirs() }

    @Test
    fun docxRoundTripPreservesStructure() {
        val model = DocModel(
            "Quarterly report",
            listOf(
                DocBlock.Heading(1, "Quarterly report"),
                DocBlock.Paragraph("Revenue grew across all regions."),
                DocBlock.Paragraph("Open the appendices", bullet = true),
                DocBlock.Quote("A short editorial note."),
                DocBlock.Code("val answer = 42"),
                DocBlock.Table(listOf(listOf("Region", "Revenue"), listOf("EU", "120"), listOf("APAC", "98"))),
            ),
        )
        val file = File(dir(), "report.docx")
        DocWriters.writeDocx(model, file)
        assertTrue(file.length() > 1000)
        val parsed = DocParsers.readDocx(file)
        assertTrue(parsed.blocks.any { it is DocBlock.Heading && it.text == "Quarterly report" })
        assertTrue(parsed.blocks.any { it is DocBlock.Paragraph && it.text.contains("Revenue grew") })
        assertTrue(parsed.blocks.any { it is DocBlock.Paragraph && it.bullet && it.text.contains("appendices") })
        val table = parsed.blocks.filterIsInstance<DocBlock.Table>().first()
        assertEquals(3, table.rows.size)
        assertEquals("APAC", table.rows[2][0])
        assertEquals("98", table.rows[2][1])
    }

    @Test
    fun readsWordListStyleAndBlankCoreTitle() {
        val url = javaClass.getResource("/documents/report.docx") ?: error("missing docx fixture")
        val file = File(url.toURI())
        val model = DocParsers.readDocx(file)
        assertTrue(model.blocks.any { it is DocBlock.Paragraph && it.bullet && it.text.contains("appendices") })
        assertTrue(model.title.isBlank())
    }

    @Test
    fun xlsxRoundTripPreservesValues() {
        val table = TableModel(
            "Sales",
            listOf(
                listOf("Market", "Units", "Active"),
                listOf("Jakarta", "1200", "TRUE"),
                listOf("Bandung", "860", "FALSE"),
            ),
        )
        val file = File(dir(), "sales.xlsx")
        DocWriters.writeXlsx(table, file)
        val parsed = DocParsers.readXlsx(file)
        assertEquals(3, parsed.rows.size)
        assertEquals("Jakarta", parsed.rows[1][0])
        assertEquals("1200", parsed.rows[1][1])
        assertEquals("FALSE", parsed.rows[2][2])
    }

    @Test
    fun delimitedTextHandlesQuotesAndNewlines() {
        val table = TableModel(
            "Export",
            listOf(
                listOf("Name", "Note"),
                listOf("A, B", "line one\nline two"),
                listOf("Quote \"x\"", "tab\there"),
            ),
        )
        val file = File(dir(), "export.csv")
        DocWriters.writeDelimited(table, file, ',')
        val parsed = DocParsers.parseDelimited(file, ',')
        assertEquals("A, B", parsed.rows[1][0])
        assertEquals("line one\nline two", parsed.rows[1][1])
        assertEquals("Quote \"x\"", parsed.rows[2][0])
    }

    @Test
    fun markdownAndJsonConversion() {
        val markdown = File(dir(), "notes.md")
        markdown.writeText(
            """
            # Field notes
            A paragraph with **bold** text.

            - First item
            - Second item

            | Key | Value |
            | --- | --- |
            | A | 1 |

            > A quote.

            ```
            code();
            ```
            """.trimIndent()
        )
        val model = DocParsers.readMarkdown(markdown)
        assertTrue(model.blocks.any { it is DocBlock.Heading && it.text == "Field notes" })
        assertTrue(model.blocks.any { it is DocBlock.Table })
        assertTrue(model.blocks.any { it is DocBlock.Code })
        val html = DocWriters.toHtml(model)
        assertTrue(html.contains("<h1>Field notes</h1>"))
        assertTrue(html.contains("<table>"))

        val table = TableModel("Rows", listOf(listOf("A", "B"), listOf("1", "2")))
        val json = JsonTools.tableToJson(table)
        val array = JSONArray(json)
        assertEquals("1", array.getJSONObject(0).getString("A"))
    }
}