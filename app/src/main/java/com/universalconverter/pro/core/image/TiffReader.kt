package com.universalconverter.pro.core.image

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.InflaterInputStream

data class TiffReadResult(
    val image: PixelImage?,
    val embeddedJpeg: ByteArray?,
    val warnings: List<String>,
    val sourceDescription: String,
)

/**
 * Baseline TIFF reader: strips, 8/16-bit, RGB/gray/palette, uncompressed/LZW/Deflate/PackBits,
 * orientation, plus embedded JPEG preview discovery for DNG and JPEG-compressed TIFFs.
 */
object TiffReader {

    private const val MAX_BYTES = 320L * 1024L * 1024L

    fun read(file: File, preferPreview: Boolean = false): TiffReadResult {
        if (file.length() > MAX_BYTES) throw ImageFormatException("This TIFF is larger than 320 MB.")
        val bytes = file.readBytes()
        val fileReader = TiffFile(bytes)
        val directories = fileReader.allDirectories()
        if (directories.isEmpty()) throw ImageFormatException("This TIFF has no directories.")

        val warnings = mutableListOf<String>()
        val preview = findBestPreview(fileReader, directories, warnings)
        if (preferPreview && preview != null) {
            return resultFromPreview(preview, warnings)
        }

        val main = directories.first()
        val mainEntry = main.entries[256] ?: throw ImageFormatException("This TIFF has no image width.")
        val width = fileReader.longValues(mainEntry).firstOrNull()?.toInt() ?: 0
        val height = fileReader.longValues(main.entries[257] ?: throw ImageFormatException("TIFF height is missing."))
            .firstOrNull()?.toInt() ?: 0
        if (width <= 0 || height <= 0) throw ImageFormatException("This TIFF has empty dimensions.")
        val compression = (main.entries[259]?.let { fileReader.firstLong(it) } ?: 1L).toInt()
        if (compression == 6 || compression == 7) {
            if (preview != null) return resultFromPreview(preview, warnings)
            throw ImageFormatException("This TIFF contains JPEG-compressed data without a decodable preview.")
        }
        val decoded = decodeDirectory(fileReader, main, warnings)
        if (decoded.image != null) return decoded
        if (preview != null) {
            warnings += "The main TIFF image could not be decoded; its embedded preview was used."
            return resultFromPreview(preview, warnings)
        }
        return decoded
    }

    private fun resultFromPreview(preview: Preview, warnings: MutableList<String>): TiffReadResult {
        val allWarnings = warnings.toMutableList()
        allWarnings += preview.warning
        return if (preview.jpeg != null) {
            TiffReadResult(null, preview.jpeg, allWarnings, "Embedded camera preview (${preview.width}×${preview.height})")
        } else {
            TiffReadResult(preview.image, null, allWarnings, "Embedded preview (${preview.width}×${preview.height})")
        }
    }

    private fun decodeDirectory(
        fileReader: TiffFile,
        directory: TiffDirectory,
        warnings: MutableList<String>,
    ): TiffReadResult {
        val entries = directory.entries
        val width = fileReader.longValues(entries[256] ?: throw ImageFormatException("TIFF width is missing.")).first().toInt()
        val height = fileReader.longValues(entries[257] ?: throw ImageFormatException("TIFF height is missing.")).first().toInt()
        if (width <= 0 || height <= 0 || width.toLong() * height > 200_000_000L) {
            throw ImageFormatException("Unsupported TIFF dimensions.")
        }
        val compression = (entries[259]?.let { fileReader.firstLong(it) } ?: 1L).toInt()
        if (compression == 6 || compression == 7) {
            val jpeg = extractJpeg(fileReader, directory)
                ?: throw ImageFormatException("This TIFF uses JPEG compression but no JPEG stream was found.")
            return TiffReadResult(null, jpeg, warnings, "JPEG-compressed TIFF")
        }
        val bits = entries[258]?.let { fileReader.longValues(it).map { value -> value.toInt() } }
            ?: listOf(1)
        val samplesPerPixel = (entries[277]?.let { fileReader.firstLong(it) } ?: 1L).toInt()
        val photometric = (entries[262]?.let { fileReader.firstLong(it) } ?: 1L).toInt()
        val orientation = (entries[274]?.let { fileReader.firstLong(it) } ?: 1L).toInt()
        val predictor = (entries[317]?.let { fileReader.firstLong(it) } ?: 1L).toInt()
        val planar = (entries[284]?.let { fileReader.firstLong(it) } ?: 1L).toInt()
        val fillOrder = (entries[266]?.let { fileReader.firstLong(it) } ?: 1L).toInt()
        if (planar != 1) throw ImageFormatException("Planar TIFF files are not supported.")
        if (photometric == 32803) {
            throw ImageFormatException("This DNG contains raw CFA data with no camera preview. Re-save the DNG with a preview.")
        }
        if (photometric !in setOf(0, 1, 2, 3)) {
            throw ImageFormatException("Unsupported TIFF colour space ($photometric).")
        }
        val bitsPerSample = bits.firstOrNull() ?: 1
        if (bitsPerSample != 8 && bitsPerSample != 16 && !(photometric == 3 && bitsPerSample <= 16)) {
            throw ImageFormatException("Unsupported TIFF bit depth ($bitsPerSample).")
        }
        val bytesPerSample = if (bitsPerSample > 8) 2 else 1
        val rowBytes = width.toLong() * samplesPerPixel * bytesPerSample
        if (rowBytes * height > 512L * 1024L * 1024L) throw ImageFormatException("This TIFF is too large in memory.")
        val raw = ByteArray((rowBytes * height).toInt())

        val strips = readStrips(fileReader, directory)
            ?: throw ImageFormatException("Tiled TIFF is not supported unless it has a preview.")
        val rowsPerStrip = (entries[278]?.let { fileReader.firstLong(it) } ?: height.toLong()).toInt()
            .coerceAtLeast(1)
        var row = 0
        for (strip in strips) {
            val rows = minOf(rowsPerStrip, height - row)
            if (rows <= 0) break
            var data = decompress(strip, compression)
            val expected = (rowBytes * rows).toInt()
            if (data.size < expected) {
                throw ImageFormatException("Compressed TIFF strip is shorter than expected.")
            }
            if (predictor == 2) applyPredictor(data, rowBytes.toInt(), rows, samplesPerPixel, bytesPerSample, fileReader.littleEndian)
            if (fillOrder == 2) reverseBits(data)
            val targetOffset = (row.toLong() * rowBytes).toInt()
            System.arraycopy(data, 0, raw, targetOffset, expected)
            row += rows
        }
        if (row < height) throw ImageFormatException("This TIFF is missing image rows.")

        val palette = if (photometric == 3) {
            val entry = entries[320] ?: throw ImageFormatException("Palette TIFF is missing its colour map.")
            fileReader.longValues(entry).map { (it shr 8).toInt() and 0xFF }.toIntArray()
        } else null
        val out = IntArray(width * height)
        var pixel = 0
        val bytesPerPixel = samplesPerPixel * bytesPerSample
        for (y in 0 until height) {
            var offset = y * rowBytes.toInt()
            for (x in 0 until width) {
                val r: Int
                val g: Int
                val b: Int
                val a: Int
                when {
                    photometric == 0 || photometric == 1 -> {
                        val value = sample(raw, offset, bytesPerSample, fileReader.littleEndian)
                        var gray = if (bitsPerSample > 8) (value shr (bitsPerSample - 8)) else value
                        if (photometric == 0) gray = 255 - gray
                        r = gray; g = gray; b = gray
                        a = if (samplesPerPixel >= 2) {
                            val alphaValue = sample(raw, offset + bytesPerSample, bytesPerSample, fileReader.littleEndian)
                            if (bitsPerSample > 8) (alphaValue shr (bitsPerSample - 8)) else alphaValue
                        } else 255
                    }
                    photometric == 3 -> {
                        val index = sample(raw, offset, bytesPerSample, fileReader.littleEndian)
                        val count = 1 shl bitsPerSample
                        val safe = index.coerceIn(0, count - 1)
                        r = palette?.getOrNull(safe) ?: 0
                        g = palette?.getOrNull(count + safe) ?: 0
                        b = palette?.getOrNull(2 * count + safe) ?: 0
                        a = 255
                    }
                    else -> {
                        r = valueTo8(sample(raw, offset, bytesPerSample, fileReader.littleEndian), bitsPerSample, 0)
                        g = valueTo8(sample(raw, offset + bytesPerSample, bytesPerSample, fileReader.littleEndian), bitsPerSample, 1)
                        b = valueTo8(sample(raw, offset + 2 * bytesPerSample, bytesPerSample, fileReader.littleEndian), bitsPerSample, 2)
                        a = if (samplesPerPixel >= 4) valueTo8(
                            sample(raw, offset + 3 * bytesPerSample, bytesPerSample, fileReader.littleEndian), bitsPerSample, 3,
                        ) else 255
                    }
                }
                out[pixel++] = (a shl 24) or (r shl 16) or (g shl 8) or b
                offset += bytesPerPixel
            }
        }
        val image = PixelImage(width, height, out).oriented(orientation)
        return TiffReadResult(image, null, warnings, "TIFF")
    }

    private fun sample(bytes: ByteArray, offset: Int, bytesPerSample: Int, littleEndian: Boolean): Int {
        if (offset >= bytes.size) return 0
        return if (bytesPerSample == 1) {
            bytes[offset].toInt() and 0xFF
        } else {
            val b0 = bytes[offset].toInt() and 0xFF
            val b1 = bytes[offset + 1].toInt() and 0xFF
            if (littleEndian) b0 or (b1 shl 8) else (b0 shl 8) or b1
        }
    }

    private fun valueTo8(value: Int, bits: Int, sampleIndex: Int): Int {
        if (bits <= 8) return value.coerceIn(0, 255)
        // Filee-style convention: alpha uses the full range, colour channels use the upper byte.
        return (value shr (bits - 8)).coerceIn(0, 255)
    }

    private fun readStrips(fileReader: TiffFile, directory: TiffDirectory): List<ByteArray>? {
        val entries = directory.entries
        val offsetsEntry = entries[273] ?: return null
        val countsEntry = entries[279] ?: return null
        val offsets = fileReader.longValues(offsetsEntry)
        val counts = fileReader.longValues(countsEntry)
        if (offsets.isEmpty()) return null
        val result = ArrayList<ByteArray>(offsets.size)
        for (i in offsets.indices) {
            val offset = offsets[i].toInt()
            val count = (counts.getOrNull(i) ?: counts.first()).toInt()
            if (offset < 0 || count < 0 || offset.toLong() + count > fileReader.bytes.size) {
                throw ImageFormatException("A TIFF strip points outside the file.")
            }
            result += fileReader.bytes.copyOfRange(offset, offset + count)
        }
        return result
    }

    private fun decompress(data: ByteArray, compression: Int): ByteArray {
        return when (compression) {
            1 -> data
            5 -> lzwDecode(data)
            8, 32946 -> InflaterInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
            32773 -> packBits(data)
            else -> throw ImageFormatException("Unsupported TIFF compression ($compression).")
        }
    }

    private fun applyPredictor(
        data: ByteArray,
        rowBytes: Int,
        rows: Int,
        samples: Int,
        bytesPerSample: Int,
        littleEndian: Boolean,
    ) {
        if (bytesPerSample == 1) {
            for (row in 0 until rows) {
                val start = row * rowBytes
                for (i in samples until rowBytes) {
                    val index = start + i
                    data[index] = (data[index].toInt() + data[index - samples].toInt()).toByte()
                }
            }
        } else {
            for (row in 0 until rows) {
                val start = row * rowBytes
                for (i in samples until rowBytes / 2) {
                    val index = start + i * 2
                    val previous = start + (i - samples) * 2
                    val value = sample(data, index, 2, littleEndian) + sample(data, previous, 2, littleEndian)
                    val wrapped = value and 0xFFFF
                    if (littleEndian) {
                        data[index] = wrapped.toByte()
                        data[index + 1] = (wrapped shr 8).toByte()
                    } else {
                        data[index] = (wrapped shr 8).toByte()
                        data[index + 1] = wrapped.toByte()
                    }
                }
            }
        }
    }

    private fun reverseBits(data: ByteArray) {
        for (i in data.indices) {
            var value = data[i].toInt() and 0xFF
            var reversed = 0
            repeat(8) {
                reversed = (reversed shl 1) or (value and 1)
                value = value ushr 1
            }
            data[i] = reversed.toByte()
        }
    }

    private fun packBits(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size * 2)
        var i = 0
        while (i < data.size) {
            val control = data[i++].toInt()
            when {
                control >= 0 -> {
                    val count = control + 1
                    if (i + count > data.size) break
                    out.write(data, i, count)
                    i += count
                }
                control == -128 -> Unit
                else -> {
                    if (i >= data.size) break
                    val count = 1 - control
                    val value = data[i++]
                    repeat(count) { out.write(value.toInt()) }
                }
            }
        }
        return out.toByteArray()
    }

    private fun lzwDecode(data: ByteArray): ByteArray {
        val clear = 256
        val end = 257
        var codeSize = 9
        var next = 258
        val prefix = IntArray(4096)
        val suffix = IntArray(4096)
        val first = IntArray(4096)
        val length = IntArray(4096)
        for (i in 0 until 256) {
            prefix[i] = -1
            suffix[i] = i
            first[i] = i
            length[i] = 1
        }
        val out = ByteArrayOutputStream(data.size * 3)
        var bitBuffer = 0
        var bitCount = 0
        var pos = 0
        var old = -1
        fun reset() {
            codeSize = 9
            next = 258
            old = -1
        }
        fun readCode(): Int? {
            while (bitCount < codeSize) {
                if (pos >= data.size) return null
                bitBuffer = (bitBuffer shl 8) or (data[pos++].toInt() and 0xFF)
                bitCount += 8
            }
            val code = (bitBuffer ushr (bitCount - codeSize)) and ((1 shl codeSize) - 1)
            bitCount -= codeSize
            if (bitCount == 0) bitBuffer = 0 else bitBuffer = bitBuffer and ((1 shl bitCount) - 1)
            return code
        }
        fun expand(code: Int, target: ByteArrayOutputStream) {
            if (code < 0 || code >= next) return
            val len = length[code]
            if (len <= 0 || len > 1 shl 20) return
            val bytes = ByteArray(len)
            var current = code
            var i = len - 1
            while (i >= 0 && current >= 0) {
                bytes[i] = suffix[current].toByte()
                current = prefix[current]
                i--
            }
            target.write(bytes)
        }
        reset()
        while (true) {
            val code = readCode() ?: break
            if (code == clear) {
                reset()
                continue
            }
            if (code == end) break
            if (old < 0) {
                if (code >= 256) break
                out.write(code)
                old = code
                continue
            }
            val firstByte: Int
            if (code < next) {
                firstByte = first[code]
                expand(code, out)
            } else if (code == next) {
                firstByte = first[old]
                val temp = ByteArrayOutputStream()
                expand(old, temp)
                temp.write(firstByte)
                out.write(temp.toByteArray())
            } else {
                break
            }
            if (next < 4096) {
                prefix[next] = old
                suffix[next] = firstByte
                first[next] = first[old]
                length[next] = length[old] + 1
                next++
                if (next == (1 shl codeSize) - 1 && codeSize < 12) codeSize++
            }
            old = code
        }
        return out.toByteArray()
    }

    private data class Preview(
        val jpeg: ByteArray? = null,
        val image: PixelImage? = null,
        val width: Int,
        val height: Int,
        val warning: String,
    )

    private fun findBestPreview(
        fileReader: TiffFile,
        directories: List<TiffDirectory>,
        warnings: MutableList<String>,
    ): Preview? {
        var best: Preview? = null
        for (directory in directories) {
            val jpeg = try {
                extractJpeg(fileReader, directory)
            } catch (_: Exception) {
                null
            }
            if (jpeg != null) {
                val (w, h) = try {
                    jpegSize(jpeg)
                } catch (_: Exception) {
                    0 to 0
                }
                if (best == null || w.toLong() * h > best.width.toLong() * best.height) {
                    best = Preview(jpeg = jpeg, width = w, height = h, warning = "DNG/RAW developed from the embedded camera preview.")
                }
            }
        }
        if (best != null) return best
        // Some DNGs carry an uncompressed RGB preview in a sub IFD.
        for (directory in directories.drop(1)) {
            val photometric = (directory.entries[262]?.let { fileReader.firstLong(it) } ?: 0L).toInt()
            val compression = (directory.entries[259]?.let { fileReader.firstLong(it) } ?: 1L).toInt()
            if (photometric in setOf(0, 1, 2) && compression in setOf(1, 5, 8, 32946, 32773)) {
                try {
                    val decoded = decodeDirectory(fileReader, directory, mutableListOf())
                    val image = decoded.image ?: continue
                    val warning = "RAW image decoded from a full-resolution embedded preview."
                    val preview = Preview(image = image, width = image.width, height = image.height, warning = warning)
                    if (best == null || image.width.toLong() * image.height > best.width.toLong() * best.height) best = preview
                } catch (_: Exception) {
                }
            }
        }
        return best
    }

    private fun extractJpeg(fileReader: TiffFile, directory: TiffDirectory): ByteArray? {
        val compression = directory.entries[259]?.let { fileReader.firstLong(it) } ?: return null
        if (compression != 6L && compression != 7L) return null
        val photometric = directory.entries[262]?.let { fileReader.firstLong(it) } ?: 0L
        if (photometric == 32803L) return null // Lossless JPEG raw data is not a viewable preview.
        val offsetsEntry = directory.entries[273] ?: return null
        val countsEntry = directory.entries[279] ?: return null
        val offsets = fileReader.longValues(offsetsEntry)
        val counts = fileReader.longValues(countsEntry)
        if (offsets.isEmpty()) return null
        val out = ByteArrayOutputStream()
        for (i in offsets.indices) {
            val offset = offsets[i].toInt()
            val count = (counts.getOrNull(i) ?: counts.first()).toInt()
            if (offset < 0 || count <= 0 || offset.toLong() + count > fileReader.bytes.size) continue
            out.write(fileReader.bytes, offset, count)
        }
        val jpeg = out.toByteArray()
        return if (jpeg.size > 4 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()) jpeg else null
    }

    private fun jpegSize(jpeg: ByteArray): Pair<Int, Int> {
        var pos = 2
        while (pos + 9 < jpeg.size) {
            if (jpeg[pos] != 0xFF.toByte()) { pos++; continue }
            val marker = jpeg[pos + 1].toInt() and 0xFF
            pos += 2
            if (marker == 0xD8 || marker == 0xD9) continue
            if (marker in 0xD0..0xD7) continue
            if (pos + 2 > jpeg.size) break
            val length = ((jpeg[pos].toInt() and 0xFF) shl 8) or (jpeg[pos + 1].toInt() and 0xFF)
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                val height = ((jpeg[pos + 3].toInt() and 0xFF) shl 8) or (jpeg[pos + 4].toInt() and 0xFF)
                val width = ((jpeg[pos + 5].toInt() and 0xFF) shl 8) or (jpeg[pos + 6].toInt() and 0xFF)
                return width to height
            }
            pos += length
        }
        return 0 to 0
    }

    private class TiffFile(val bytes: ByteArray) {
        val littleEndian: Boolean
        private val firstIfd: Long

        init {
            if (bytes.size < 8) throw ImageFormatException("This is not a TIFF file.")
            littleEndian = when {
                bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte() -> true
                bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte() -> false
                else -> throw ImageFormatException("This is not a TIFF file.")
            }
            val magic = u16(2)
            if (magic != 42) throw ImageFormatException("BigTIFF is not supported yet.")
            firstIfd = u32(4)
            if (firstIfd <= 0L || firstIfd >= bytes.size.toLong()) throw ImageFormatException("Invalid TIFF IFD offset.")
        }

        fun allDirectories(): List<TiffDirectory> {
            val result = ArrayList<TiffDirectory>()
            val visited = HashSet<Long>()
            val queue = ArrayDeque<Long>()
            queue += firstIfd
            while (queue.isNotEmpty() && result.size < 24) {
                val offset = queue.removeFirst()
                if (!visited.add(offset)) continue
                val directory = readDirectory(offset)
                result += directory
                val next = directory.nextIfd
                if (next > 0L && next < bytes.size.toLong()) queue += next
                val sub = directory.entries[330]?.let { longValues(it) } ?: emptyList()
                for (entry in sub) if (entry > 0L && entry < bytes.size.toLong()) queue += entry
            }
            return result
        }

        private fun readDirectory(offset: Long): TiffDirectory {
            val start = offset.toInt()
            val count = u16(start)
            if (count < 0 || count > 512) throw ImageFormatException("Invalid TIFF directory.")
            val entries = HashMap<Int, TiffEntry>()
            var pos = start + 2
            for (i in 0 until count) {
                if (pos + 12 > bytes.size) throw ImageFormatException("Truncated TIFF directory.")
                val tag = u16(pos)
                val type = u16(pos + 2)
                val amount = u32((pos + 4).toLong()).toInt()
                if (amount < 0) throw ImageFormatException("Invalid TIFF value count.")
                val typeSize = typeSize(type)
                val total = amount.toLong() * typeSize
                val valueOffset = if (total <= 4L) (pos + 8).toLong() else u32((pos + 8).toLong())
                if (valueOffset < 0L || valueOffset >= bytes.size.toLong()) {
                    pos += 12
                    continue
                }
                entries[tag] = TiffEntry(tag, type, amount, valueOffset)
                pos += 12
            }
            val next = if (pos + 4 <= bytes.size) u32(pos.toLong()) else 0L
            return TiffDirectory(entries, next)
        }

        fun longValues(entry: TiffEntry): List<Long> {
            val size = typeSize(entry.type)
            val count = entry.count.coerceAtMost(1_000_000)
            val result = ArrayList<Long>(minOf(count, 4096))
            var offset = entry.valueOffset
            for (i in 0 until count) {
                if (offset + size > bytes.size.toLong()) break
                val value = when (entry.type) {
                    1, 6, 7 -> (bytes[offset.toInt()].toInt() and 0xFF).toLong()
                    3, 8 -> u16(offset.toInt()).toLong()
                    4, 9, 13 -> u32(offset)
                    else -> 0L
                }
                result += value
                offset += size
            }
            return result
        }

        fun firstLong(entry: TiffEntry): Long = longValues(entry).firstOrNull() ?: 0L

        fun u16(offset: Int): Int {
            if (offset < 0 || offset + 2 > bytes.size) return 0
            return if (littleEndian) {
                (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            } else {
                ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
            }
        }

        fun u32(offset: Long): Long {
            val i = offset.toInt()
            if (i < 0 || i + 4 > bytes.size) return 0
            return if (littleEndian) {
                (bytes[i].toLong() and 0xFF) or
                    ((bytes[i + 1].toLong() and 0xFF) shl 8) or
                    ((bytes[i + 2].toLong() and 0xFF) shl 16) or
                    ((bytes[i + 3].toLong() and 0xFF) shl 24)
            } else {
                ((bytes[i].toLong() and 0xFF) shl 24) or
                    ((bytes[i + 1].toLong() and 0xFF) shl 16) or
                    ((bytes[i + 2].toLong() and 0xFF) shl 8) or
                    (bytes[i + 3].toLong() and 0xFF)
            }
        }
    }

    private data class TiffEntry(val tag: Int, val type: Int, val count: Int, val valueOffset: Long)
    private data class TiffDirectory(val entries: Map<Int, TiffEntry>, val nextIfd: Long)

    private fun typeSize(type: Int): Int = when (type) {
        1, 2, 6, 7 -> 1
        3, 8 -> 2
        4, 9, 11, 13 -> 4
        5, 10, 12 -> 8
        else -> 1
    }
}

class ImageFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)