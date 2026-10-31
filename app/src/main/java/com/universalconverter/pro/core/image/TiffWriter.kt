package com.universalconverter.pro.core.image

import com.universalconverter.pro.core.io.ByteList
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/** Writes a widely compatible little-endian TIFF using Adobe Deflate strips. */
object TiffWriter {

    private const val ROWS_PER_STRIP = 64

    private data class Entry(val tag: Int, val type: Int, val count: Int, val data: ByteArray) {
        val typeSize: Int
            get() = when (type) {
                3 -> 2
                4 -> 4
                5 -> 8
                else -> 1
            }
        val byteSize: Int get() = data.size
    }

    fun write(image: PixelImage, file: File, level: Int = 6) {
        val channels = if (image.hasTransparency()) 4 else 3
        val strips = ArrayList<ByteArray>()
        val counts = ArrayList<Int>()
        var y = 0
        while (y < image.height) {
            val rows = minOf(ROWS_PER_STRIP, image.height - y)
            val raw = ByteArray(image.width * rows * channels)
            var index = 0
            for (row in 0 until rows) {
                val offset = (y + row) * image.width
                for (x in 0 until image.width) {
                    val pixel = image.argb[offset + x]
                    raw[index++] = ((pixel shr 16) and 0xFF).toByte()
                    raw[index++] = ((pixel shr 8) and 0xFF).toByte()
                    raw[index++] = (pixel and 0xFF).toByte()
                    if (channels == 4) raw[index++] = ((pixel ushr 24) and 0xFF).toByte()
                }
            }
            val compressed = compress(raw, level)
            strips += compressed
            counts += compressed.size
            y += rows
        }
        val stripOffsets = IntArray(strips.size)
        val entries = ArrayList<Entry>()
        fun short(value: Int) = byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())
        fun long(value: Int) = byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )
        fun shorts(values: IntArray): ByteArray {
            val out = ByteArray(values.size * 2)
            values.forEachIndexed { i, value ->
                out[i * 2] = (value and 0xFF).toByte()
                out[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
            }
            return out
        }
        fun longs(values: IntArray): ByteArray {
            val out = ByteArray(values.size * 4)
            values.forEachIndexed { i, value ->
                val bytes = long(value)
                bytes.copyInto(out, i * 4)
            }
            return out
        }
        fun rational(numerator: Int, denominator: Int): ByteArray = long(numerator) + long(denominator)
        fun ascii(text: String): ByteArray = text.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)

        entries += Entry(256, 4, 1, long(image.width))
        entries += Entry(257, 4, 1, long(image.height))
        entries += Entry(258, 3, channels, shorts(IntArray(channels) { 8 }))
        entries += Entry(259, 3, 1, short(8))
        entries += Entry(262, 3, 1, short(2))
        entries += Entry(273, 4, strips.size, longs(IntArray(strips.size)))
        entries += Entry(274, 3, 1, short(1))
        entries += Entry(277, 3, 1, short(channels))
        entries += Entry(278, 4, 1, long(ROWS_PER_STRIP))
        entries += Entry(279, 4, counts.size, longs(counts.toIntArray()))
        entries += Entry(282, 5, 1, rational(72, 1))
        entries += Entry(283, 5, 1, rational(72, 1))
        entries += Entry(284, 3, 1, short(1))
        entries += Entry(296, 3, 1, short(2))
        entries += Entry(305, 2, 24, ascii("Universal Converter"))
        entries += Entry(
            306, 2, 20,
            ascii(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))),
        )
        if (channels == 4) entries += Entry(338, 3, 1, short(2))
        entries += Entry(339, 3, channels, shorts(IntArray(channels) { 1 }))
        entries.sortBy { it.tag }

        val ifdSize = 2 + entries.size * 12 + 4
        var extraOffset = 8 + ifdSize
        val extraOffsets = HashMap<Int, Int>()
        for (entry in entries) {
            if (entry.byteSize > 4) {
                if (extraOffset % 2 != 0) extraOffset++
                extraOffsets[entry.tag] = extraOffset
                extraOffset += entry.byteSize
            }
        }
        while (extraOffset % 4 != 0) extraOffset++
        val stripDataStart = extraOffset
        var writeOffset = stripDataStart
        for (i in strips.indices) {
            stripOffsets[i] = writeOffset
            writeOffset += strips[i].size
        }
        val offsetsEntry = entries.first { it.tag == 273 }
        val newOffsets = longs(stripOffsets).also { data ->
            data.copyInto(offsetsEntry.data)
        }
        @Suppress("UNUSED_VARIABLE") val unusedOffsets = newOffsets

        val out = ByteList(writeOffset)
        out.writeAscii("II")
        out.writeU16LE(42)
        out.writeU32LE(8)
        out.writeU16LE(entries.size)
        for (entry in entries) {
            out.writeU16LE(entry.tag)
            out.writeU16LE(entry.type)
            out.writeU32LE(entry.count)
            if (entry.byteSize <= 4) {
                out.writeBytes(entry.data)
                repeat(4 - entry.byteSize) { out.writeByte(0) }
            } else {
                out.writeU32LE(extraOffsets.getValue(entry.tag))
            }
        }
        out.writeU32LE(0)
        val extrasStart = 8 + ifdSize
        while (out.size < extrasStart) out.writeByte(0)
        for (entry in entries) {
            if (entry.byteSize <= 4) continue
            while (out.size < extraOffsets.getValue(entry.tag)) out.writeByte(0)
            out.writeBytes(entry.data)
        }
        while (out.size < stripDataStart) out.writeByte(0)
        for (strip in strips) out.writeBytes(strip)
        file.writeBytes(out.toByteArray())
    }

    private fun compress(raw: ByteArray, level: Int): ByteArray {
        val output = ByteArrayOutputStream(raw.size / 2 + 64)
        val deflater = Deflater(level.coerceIn(1, 9), false)
        DeflaterOutputStream(output, deflater, 1 shl 14).use { it.write(raw) }
        deflater.end()
        return output.toByteArray()
    }
}