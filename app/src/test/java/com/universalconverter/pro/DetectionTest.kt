package com.universalconverter.pro

import com.universalconverter.pro.core.detect.FormatDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DetectionTest {

    private fun tempFile(name: String): File {
        val dir = File("build/tmp/detection-test")
        dir.mkdirs()
        return File(dir, name).also { it.delete() }
    }

    @Test
    fun detectsMeshesAndMarkerFormats() {
        val obj = tempFile("cube.obj")
        obj.writeText("o cube\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n")
        assertEquals("obj", FormatDetector.detect(obj).format?.id)

        val glb = tempFile("cube.glb")
        glb.writeBytes(
            byteArrayOf(
                'g'.code.toByte(), 'l'.code.toByte(), 'T'.code.toByte(), 'F'.code.toByte(),
                2, 0, 0, 0,
                24, 0, 0, 0,
                12, 0, 0, 0,
                0x4A, 0x53, 0x4F, 0x4E,
                '{'.code.toByte(), '}'.code.toByte(), 0x20, 0x20,
            )
        )
        assertEquals("glb", FormatDetector.detect(glb).format?.id)

        val pdf = tempFile("document.pdf")
        pdf.writeText("%PDF-1.7\n1 0 obj\n<<>>\nendobj\n")
        assertEquals("pdf", FormatDetector.detect(pdf).format?.id)

        val svg = tempFile("drawing.svg")
        svg.writeText("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"10\" height=\"10\"/></svg>")
        assertEquals("svg", FormatDetector.detect(svg).format?.id)
    }

    @Test
    fun detectsMismatchedExtension() {
        val dir = File("build/tmp/detection-test").also { it.mkdirs() }
        val fake = File(dir, "really-a-png.pdf")
        fake.writeBytes(
            byteArrayOf(
                0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 8, 0, 0, 0, 8,
                8, 6, 0, 0, 0, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(),
            )
        )
        val detection = FormatDetector.detect(fake)
        assertEquals("png", detection.format?.id)
        assertEquals("pdf", detection.declared?.id)
        assertTrue(detection.mismatch)
    }

    @Test
    fun detectsOfficeZipByContent() {
        val dir = File("build/tmp/detection-test").also { it.mkdirs() }
        val docx = File(dir, "letter.docx")
        ZipOutputStream(docx.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("<Types/>".toByteArray())
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write("<w:document/>".toByteArray())
        }
        assertEquals("docx", FormatDetector.detect(docx).format?.id)
    }
}