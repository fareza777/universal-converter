package com.universalconverter.pro

import com.universalconverter.pro.core.image.BmpWriter
import com.universalconverter.pro.core.image.PixelImage
import com.universalconverter.pro.core.image.TiffReader
import com.universalconverter.pro.core.image.TiffWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImageCodecTest {

    private fun resource(name: String): File {
        val url = javaClass.getResource("/images/$name") ?: error("Missing fixture $name")
        return File(url.toURI())
    }

    private fun channel(pixel: Int, shift: Int): Int = (pixel shr shift) and 0xFF

    @Test
    fun readsLzwDeflateAndPackBitsTiffs() {
        for (name in listOf("gradient_lzw.tiff", "gradient_deflate.tiff", "gradient_packbits.tiff")) {
            val result = TiffReader.read(resource(name))
            val image = result.image ?: error("$name produced no pixels")
            assertEquals(name, 64, image.width)
            assertEquals(name, 48, image.height)
            val pixel = image.argb[(48 - 1) * 64 + 63]
            assertEquals("$name red", 255.0, channel(pixel, 16).toDouble(), 8.0)
            assertEquals("$name green", 255.0, channel(pixel, 8).toDouble(), 8.0)
            assertEquals("$name blue", 120.0, channel(pixel, 0).toDouble(), 8.0)
        }
    }

    @Test
    fun readsAlphaGrayPaletteAndJpegTiff() {
        val alpha = TiffReader.read(resource("alpha.tiff")).image ?: error("No alpha image")
        assertEquals(32, alpha.width)
        val alphaPixel = alpha.argb[0]
        assertEquals(128.0, channel(alphaPixel, 24).toDouble(), 2.0)
        assertEquals(10.0, channel(alphaPixel, 16).toDouble(), 4.0)
        assertEquals(20.0, channel(alphaPixel, 8).toDouble(), 4.0)
        assertEquals(30.0, channel(alphaPixel, 0).toDouble(), 4.0)

        val gray = TiffReader.read(resource("gray.tiff")).image ?: error("No gray image")
        val grayPixel = gray.argb[gray.width * 20 + 30]
        assertEquals(channel(grayPixel, 16), channel(grayPixel, 8))
        assertEquals(channel(grayPixel, 8), channel(grayPixel, 0))

        val palette = TiffReader.read(resource("palette.tiff")).image ?: error("No palette image")
        assertEquals(64, palette.width)
        assertTrue(channel(palette.argb[0], 16) >= 0)

        val jpegTiff = TiffReader.read(resource("gradient_jpeg.tiff"))
        assertNotNull("JPEG-in-TIFF should surface its embedded JPEG", jpegTiff.embeddedJpeg)
        assertEquals(0xFF, jpegTiff.embeddedJpeg!![0].toInt() and 0xFF)
        assertEquals(0xD8, jpegTiff.embeddedJpeg[1].toInt() and 0xFF)
    }

    @Test
    fun tiffWriterRoundTripsPixelsAndAlpha() {
        val width = 41
        val height = 33
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            ((255 - y) shl 24) or ((x * 255 / (width - 1)) shl 16) or
                ((y * 255 / (height - 1)) shl 8) or 77
        }
        val image = PixelImage(width, height, pixels)
        val file = File("build/tmp/gif-test/tiff-roundtrip.tiff")
        file.parentFile?.mkdirs()
        TiffWriter.write(image, file)
        assertTrue(file.length() > 200)
        val back = TiffReader.read(file).image ?: error("Writer output could not be read")
        assertEquals(width, back.width)
        assertEquals(height, back.height)
        for (index in pixels.indices step 37) {
            val a = pixels[index]
            val b = back.argb[index]
            assertEquals("red $index", channel(a, 16).toDouble(), channel(b, 16).toDouble(), 2.0)
            assertEquals("green $index", channel(a, 8).toDouble(), channel(b, 8).toDouble(), 2.0)
            assertEquals("blue $index", channel(a, 0).toDouble(), channel(b, 0).toDouble(), 2.0)
            assertEquals("alpha $index", channel(a, 24).toDouble(), channel(b, 24).toDouble(), 2.0)
        }
    }

    @Test
    fun bmpWriterWritesBottomUpRows() {
        val image = PixelImage(3, 2, intArrayOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF808080.toInt(),
        ))
        val file = File("build/tmp/gif-test/sample.bmp")
        file.parentFile?.mkdirs()
        BmpWriter.write(image, file)
        val bytes = file.readBytes()
        assertEquals('B'.code.toByte(), bytes[0])
        assertEquals('M'.code.toByte(), bytes[1])
        val width = (bytes[18].toInt() and 0xFF) or ((bytes[19].toInt() and 0xFF) shl 8) or
            ((bytes[20].toInt() and 0xFF) shl 16) or ((bytes[21].toInt() and 0xFF) shl 24)
        assertEquals(3, width)
        // First stored row is the bottom row, first pixel white (BGR 255,255,255).
        assertEquals(255, bytes[54].toInt() and 0xFF)
        assertEquals(255, bytes[55].toInt() and 0xFF)
        assertEquals(255, bytes[56].toInt() and 0xFF)
        // Top-left red appears at offset 54 + stride + 0.
        assertEquals(0, bytes[54 + 12].toInt())
        assertEquals(0, bytes[54 + 13].toInt())
        assertEquals(255, bytes[54 + 14].toInt() and 0xFF)
    }
}