package com.universalconverter.pro

import com.universalconverter.pro.core.image.GifEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class GifWriterTest {

    @Test
    fun writesReadableAnimatedGif() {
        val dir = File("build/tmp/gif-test").also { it.mkdirs() }
        val file = File(dir, "animation.gif")
        val width = 64
        val height = 48
        GifEncoder(file, width, height).use { encoder ->
            encoder.addFrame(IntArray(width * height) { 0xFFFF0000.toInt() }, 8)
            encoder.addFrame(IntArray(width * height) { 0xFF0000FF.toInt() }, 8)
        }
        val gif = GifTestParser.parse(file.readBytes())
        assertEquals("GIF89a", gif.signature)
        assertEquals(width, gif.width)
        assertEquals(height, gif.height)
        assertEquals(2, gif.frames.size)
        assertTrue(gif.looping)
        val red = gif.frames[0]
        assertEquals(width, red.width)
        assertEquals(height, red.height)
        val redPacked = red.palette[(red.indices[10 * width + 10].toInt() and 0xFF)]
        assertTrue("Expected red, got ${Integer.toHexString(redPacked)}", redPacked shr 16 and 0xFF > 200)
        val blue = gif.frames[1]
        val bluePacked = blue.palette[(blue.indices[10 * width + 10].toInt() and 0xFF)]
        assertTrue("Expected blue, got ${Integer.toHexString(bluePacked)}", bluePacked and 0xFF > 200)
        assertEquals(8, red.delayCentiseconds)
    }

    @Test
    fun ditheringPathStaysReadable() {
        val dir = File("build/tmp/gif-test").also { it.mkdirs() }
        val file = File(dir, "gradient.gif")
        val width = 100
        val height = 60
        GifEncoder(file, width, height).use { encoder ->
            val pixels = IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                val r = (x * 255 / width).coerceIn(0, 255)
                val g = (y * 255 / height).coerceIn(0, 255)
                (0xFF shl 24) or (r shl 16) or (g shl 8) or 80
            }
            encoder.addFrame(pixels, 10, 128, dither = true)
        }
        val gif = GifTestParser.parse(file.readBytes())
        assertEquals(width, gif.width)
        assertEquals(1, gif.frames.size)
        assertEquals(width * height, gif.frames[0].indices.size)
    }
}

private data class ParsedGif(
    val signature: String,
    val width: Int,
    val height: Int,
    val looping: Boolean,
    val frames: List<ParsedFrame>,
)

private data class ParsedFrame(
    val width: Int,
    val height: Int,
    val delayCentiseconds: Int,
    val palette: IntArray,
    val indices: ByteArray,
)

private object GifTestParser {
    fun parse(bytes: ByteArray): ParsedGif {
        var pos = 0
        fun u8(): Int = bytes[pos++].toInt() and 0xFF
        fun u16(): Int = u8() or (u8() shl 8)
        val signature = String(bytes, 0, 6, Charsets.US_ASCII)
        pos = 6
        val width = u16()
        val height = u16()
        val packed = u8()
        u8() // background
        u8() // aspect
        if (packed and 0x80 != 0) pos += 3 * (1 shl ((packed and 0x07) + 1))
        var looping = false
        var delay = 0
        val frames = mutableListOf<ParsedFrame>()
        while (pos < bytes.size) {
            when (u8()) {
                0x21 -> {
                    val label = u8()
                    if (label == 0xF9) {
                        val blockSize = u8()
                        u8() // flags
                        delay = u16()
                        u8() // transparent index
                        if (blockSize != 4) repeat(blockSize - 4) { pos++ }
                        u8() // sub-block terminator
                    } else {
                        if (label == 0xFF) {
                            val blockSize = u8()
                            val app = String(bytes, pos, blockSize, Charsets.US_ASCII)
                            pos += blockSize
                            if (app.startsWith("NETSCAPE")) {
                                looping = true
                            }
                        } else {
                            pos += u8()
                        }
                        skipSubBlocks(bytes, pos).let { pos = it }
                    }
                }
                0x2C -> {
                    val left = u16()
                    val top = u16()
                    val frameWidth = u16()
                    val frameHeight = u16()
                    val imagePacked = u8()
                    val localTable = imagePacked and 0x80 != 0
                    var palette = IntArray(0)
                    if (localTable) {
                        val count = 1 shl ((imagePacked and 0x07) + 1)
                        palette = IntArray(count) { (u8() shl 16) or (u8() shl 8) or u8() }
                    }
                    val minCodeSize = u8()
                    val dataStart = pos
                    pos = skipSubBlocks(bytes, pos)
                    val compressed = extractSubBlocks(bytes, dataStart)
                    val indices = LzwDecoder.decode(compressed, minCodeSize)
                    frames += ParsedFrame(frameWidth, frameHeight, delay, palette, indices)
                    @Suppress("UNUSED_VARIABLE") val unusedGeometry = left + top
                }
                0x3B -> pos = bytes.size
                else -> error("Unexpected GIF block 0x${(bytes[pos - 1].toInt() and 0xFF).toString(16)}")
            }
        }
        return ParsedGif(signature, width, height, looping, frames)
    }

    private fun skipSubBlocks(bytes: ByteArray, start: Int): Int {
        var pos = start
        while (pos < bytes.size) {
            val size = bytes[pos].toInt() and 0xFF
            pos++
            if (size == 0) return pos
            pos += size
        }
        error("Unterminated GIF sub-block")
    }

    private fun extractSubBlocks(bytes: ByteArray, start: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = start
        while (pos < bytes.size) {
            val size = bytes[pos].toInt() and 0xFF
            pos++
            if (size == 0) break
            out.write(bytes, pos, size)
            pos += size
        }
        return out.toByteArray()
    }

    private object LzwDecoder {
        fun decode(data: ByteArray, minCodeSize: Int): ByteArray {
            val clear = 1 shl minCodeSize
            val end = clear + 1
            var codeSize = minCodeSize + 1
            var next = end + 1
            var dictPrefix = IntArray(4096)
            var dictSuffix = ByteArray(4096)
            var dictLength = IntArray(4096)
            for (i in 0 until clear) {
                dictSuffix[i] = i.toByte()
                dictLength[i] = 1
            }
            val out = ByteArrayOutputStream()
            var previous = -1
            var bitBuffer = 0
            var bitCount = 0
            var pos = 0
            fun reset() {
                codeSize = minCodeSize + 1
                next = end + 1
                previous = -1
            }
            fun expand(code: Int): ByteArray {
                val length = dictLength[code]
                val bytes = ByteArray(length)
                var current = code
                for (i in length - 1 downTo 0) {
                    bytes[i] = dictSuffix[current]
                    current = dictPrefix[current]
                }
                return bytes
            }
            reset()
            while (true) {
                while (bitCount < codeSize) {
                    if (pos >= data.size) return out.toByteArray()
                    bitBuffer = bitBuffer or ((data[pos++].toInt() and 0xFF) shl bitCount)
                    bitCount += 8
                }
                val code = bitBuffer and ((1 shl codeSize) - 1)
                bitBuffer = bitBuffer ushr codeSize
                bitCount -= codeSize
                if (code == clear) {
                    reset()
                    continue
                }
                if (code == end) break
                if (code < next) {
                    val entry = expand(code)
                    out.write(entry)
                    if (previous >= 0 && next < 4096) {
                        dictPrefix[next] = previous
                        dictSuffix[next] = entry[0]
                        dictLength[next] = dictLength[previous] + 1
                        next++
                        if (next == (1 shl codeSize) && codeSize < 12) codeSize++
                    }
                    previous = code
                } else if (code == next && previous >= 0) {
                    val previousBytes = expand(previous)
                    val entry = previousBytes + previousBytes[0]
                    out.write(entry)
                    if (next < 4096) {
                        dictPrefix[next] = previous
                        dictSuffix[next] = previousBytes[0]
                        dictLength[next] = dictLength[previous] + 1
                        next++
                        if (next == (1 shl codeSize) && codeSize < 12) codeSize++
                    }
                    previous = code
                } else {
                    error("Bad LZW code $code (next $next)")
                }
            }
            return out.toByteArray()
        }
    }
}