package com.universalconverter.pro.core.image

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Small GIF89a writer with per-frame palettes, median-cut quantisation and optional
 * Floyd–Steinberg dithering. Pixels are non-premultiplied ARGB.
 */
class GifEncoder(
    file: File,
    private val width: Int,
    private val height: Int,
    loopForever: Boolean = true,
    bufferSize: Int = 1 shl 16,
) : Closeable {

    init {
        require(width in 1..65535 && height in 1..65535) { "GIF dimensions must fit in 16 bits." }
    }

    private val out: OutputStream = BufferedOutputStream(file.outputStream(), bufferSize)
    private var finished = false
    private var frameCount = 0

    init {
        writeHeader(loopForever)
    }

    fun addFrame(
        pixels: IntArray,
        delayCentiseconds: Int,
        maxColors: Int = 256,
        dither: Boolean = false,
        backgroundArgb: Int = 0xFFF5F0E7.toInt(),
    ) {
        require(pixels.size == width * height) { "Frame size does not match the GIF canvas." }
        val quantizer = FrameQuantizer(pixels, width, height, maxColors, dither, backgroundArgb)
        writeGraphicsControl(delayCentiseconds)
        writeImageDescriptor(quantizer.tableSize, quantizer.tableBits)
        quantizer.writeLzw(out)
        frameCount++
    }

    private fun writeHeader(loopForever: Boolean) {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, width)
        writeShort(out, height)
        out.write(0x70) // no global table, 8-bit colour resolution
        out.write(0)
        out.write(0)
        if (loopForever) {
            out.write(0x21)
            out.write(0xFF)
            out.write(0x0B)
            out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            out.write(0x03)
            out.write(0x01)
            writeShort(out, 0)
            out.write(0)
        }
    }

    private fun writeGraphicsControl(delayCentiseconds: Int) {
        out.write(0x21)
        out.write(0xF9)
        out.write(0x04)
        out.write(0x04) // disposal method 1, no transparency
        writeShort(out, delayCentiseconds.coerceIn(2, 65535))
        out.write(0)
        out.write(0)
    }

    private fun writeImageDescriptor(tableSize: Int, tableBits: Int) {
        out.write(0x2C)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, width)
        writeShort(out, height)
        out.write(0x80 or (tableBits - 1)) // local colour table, no interlace
    }

    private fun writeShort(stream: OutputStream, value: Int) {
        stream.write(value and 0xFF)
        stream.write((value ushr 8) and 0xFF)
    }

    override fun close() {
        if (finished) return
        finished = true
        if (frameCount == 0) {
            // A valid, empty GIF: a single transparent-free black frame.
            val empty = IntArray(width * height)
            addFrame(empty, 10)
        }
        out.write(0x3B)
        out.flush()
        out.close()
    }

    private class FrameQuantizer(
        pixels: IntArray,
        private val width: Int,
        private val height: Int,
        maxColors: Int,
        dither: Boolean,
        backgroundArgb: Int,
    ) {
        private val histogram = IntArray(32768)
        private val sumR = LongArray(32768)
        private val sumG = LongArray(32768)
        private val sumB = LongArray(32768)
        private val usedBins = ArrayList<Int>(min(256, maxColors))
        private val palette: IntArray
        val tableSize: Int
        val tableBits: Int
        private val indices: ByteArray = ByteArray(width * height)
        private val nearestCache = IntArray(32768) { -1 }

        init {
            val limit = maxColors.coerceIn(2, 256)
            val bgR = (backgroundArgb ushr 16) and 0xFF
            val bgG = (backgroundArgb ushr 8) and 0xFF
            val bgB = backgroundArgb and 0xFF
            for (argb in pixels) {
                val alpha = (argb ushr 24) and 0xFF
                var r: Int
                var g: Int
                var b: Int
                if (alpha == 255) {
                    r = (argb ushr 16) and 0xFF
                    g = (argb ushr 8) and 0xFF
                    b = argb and 0xFF
                } else {
                    val a = alpha / 255f
                    val inverse = 1f - a
                    r = (((argb ushr 16) and 0xFF) * a + bgR * inverse).roundToInt().coerceIn(0, 255)
                    g = (((argb ushr 8) and 0xFF) * a + bgG * inverse).roundToInt().coerceIn(0, 255)
                    b = ((argb and 0xFF) * a + bgB * inverse).roundToInt().coerceIn(0, 255)
                }
                val bin = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                if (histogram[bin] == 0) usedBins += bin
                histogram[bin]++
                sumR[bin] += r.toLong()
                sumG[bin] += g.toLong()
                sumB[bin] += b.toLong()
            }
            if (usedBins.isEmpty()) usedBins += 0

            val colors = medianCut(usedBins, limit)
            palette = IntArray(colors.size)
            for (i in colors.indices) palette[i] = colors[i]
            var size = 2
            var bits = 1
            while (size < palette.size) {
                size = size shl 1
                bits++
            }
            if (bits < 2) {
                size = 4
                bits = 2
            }
            tableSize = size
            tableBits = bits
            renderIndices(pixels, dither, bgR, bgG, bgB)
        }

        private fun medianCut(bins: ArrayList<Int>, limit: Int): IntArray {
            if (bins.size <= limit) {
                return IntArray(bins.size) { representative(bins[it]) }
            }
            val keys = IntArray(bins.size) { bins[it] }
            val boxes = ArrayList<Box>()
            var total = 0L
            for (key in keys) total += histogram[key]
            boxes += Box(0, keys.size, total)
            while (boxes.size < limit) {
                var bestIndex = -1
                var bestScore = -1L
                for (i in boxes.indices) {
                    val box = boxes[i]
                    if (box.end - box.start <= 1) continue
                    val score = box.count
                    if (score > bestScore) {
                        bestScore = score
                        bestIndex = i
                    }
                }
                if (bestIndex < 0) break
                val box = boxes[bestIndex]
                val range = channelRanges(keys, box.start, box.end)
                val channel = when {
                    range[0] >= range[1] && range[0] >= range[2] -> 0
                    range[1] >= range[2] -> 1
                    else -> 2
                }
                sortByChannel(keys, box.start, box.end, channel)
                var cumulative = 0L
                var split = box.start + 1
                val half = box.count / 2
                for (i in box.start until box.end - 1) {
                    cumulative += histogram[keys[i]]
                    split = i + 1
                    if (cumulative >= half) break
                }
                split = split.coerceIn(box.start + 1, box.end - 1)
                var leftCount = 0L
                for (i in box.start until split) leftCount += histogram[keys[i]]
                boxes[bestIndex] = Box(box.start, split, leftCount)
                boxes += Box(split, box.end, box.count - leftCount)
            }
            return IntArray(boxes.size) { box ->
                var r = 0L
                var g = 0L
                var b = 0L
                var count = 0L
                for (i in boxes[box].start until boxes[box].end) {
                    val key = keys[i]
                    r += sumR[key]
                    g += sumG[key]
                    b += sumB[key]
                    count += histogram[key]
                }
                if (count == 0L) 0 else pack(
                    (r / count).toInt().coerceIn(0, 255),
                    (g / count).toInt().coerceIn(0, 255),
                    (b / count).toInt().coerceIn(0, 255),
                )
            }
        }

        private fun representative(key: Int): Int {
            val count = histogram[key]
            if (count == 0) return 0
            return pack(
                (sumR[key] / count).toInt().coerceIn(0, 255),
                (sumG[key] / count).toInt().coerceIn(0, 255),
                (sumB[key] / count).toInt().coerceIn(0, 255),
            )
        }

        private fun channelRanges(keys: IntArray, start: Int, end: Int): IntArray {
            var minR = 31; var maxR = 0
            var minG = 31; var maxG = 0
            var minB = 31; var maxB = 0
            for (i in start until end) {
                val key = keys[i]
                val r = (key shr 10) and 31
                val g = (key shr 5) and 31
                val b = key and 31
                if (r < minR) minR = r; if (r > maxR) maxR = r
                if (g < minG) minG = g; if (g > maxG) maxG = g
                if (b < minB) minB = b; if (b > maxB) maxB = b
            }
            return intArrayOf(maxR - minR, maxG - minG, maxB - minB)
        }

        private fun channel(key: Int, channel: Int): Int = when (channel) {
            0 -> (key shr 10) and 31
            1 -> (key shr 5) and 31
            else -> key and 31
        }

        private fun sortByChannel(keys: IntArray, start: Int, end: Int, channel: Int) {
            if (end - start < 2) return
            quickSort(keys, start, end - 1, channel)
        }

        private fun quickSort(keys: IntArray, low: Int, high: Int, channel: Int) {
            var i = low
            var j = high
            val pivot = channel(keys[(low + high) ushr 1], channel)
            while (i <= j) {
                while (channel(keys[i], channel) < pivot) i++
                while (channel(keys[j], channel) > pivot) j--
                if (i <= j) {
                    val tmp = keys[i]
                    keys[i] = keys[j]
                    keys[j] = tmp
                    i++
                    j--
                }
            }
            if (low < j) quickSort(keys, low, j, channel)
            if (i < high) quickSort(keys, i, high, channel)
        }

        private fun renderIndices(pixels: IntArray, dither: Boolean, bgR: Int, bgG: Int, bgB: Int) {
            if (!dither) {
                for (i in pixels.indices) {
                    val argb = pixels[i]
                    val a = (argb ushr 24) and 0xFF
                    var r = (argb ushr 16) and 0xFF
                    var g = (argb ushr 8) and 0xFF
                    var b = argb and 0xFF
                    if (a != 255) {
                        val af = a / 255f
                        r = (r * af + bgR * (1f - af)).roundToInt().coerceIn(0, 255)
                        g = (g * af + bgG * (1f - af)).roundToInt().coerceIn(0, 255)
                        b = (b * af + bgB * (1f - af)).roundToInt().coerceIn(0, 255)
                    }
                    val bin = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                    val index = nearest(bin)
                    indices[i] = index.toByte()
                }
                return
            }
            var errR = IntArray(width + 2)
            var errG = IntArray(width + 2)
            var errB = IntArray(width + 2)
            var nextR = IntArray(width + 2)
            var nextG = IntArray(width + 2)
            var nextB = IntArray(width + 2)
            var y = 0
            while (y < height) {
                java.util.Arrays.fill(nextR, 0)
                java.util.Arrays.fill(nextG, 0)
                java.util.Arrays.fill(nextB, 0)
                var x = 0
                while (x < width) {
                    val argb = pixels[y * width + x]
                    val a = (argb ushr 24) and 0xFF
                    var r = (argb ushr 16) and 0xFF
                    var g = (argb ushr 8) and 0xFF
                    var b = argb and 0xFF
                    if (a != 255) {
                        val af = a / 255f
                        r = (r * af + bgR * (1f - af)).roundToInt()
                        g = (g * af + bgG * (1f - af)).roundToInt()
                        b = (b * af + bgB * (1f - af)).roundToInt()
                    }
                    val rr = (r + errR[x + 1]).coerceIn(0, 255)
                    val gg = (g + errG[x + 1]).coerceIn(0, 255)
                    val bb = (b + errB[x + 1]).coerceIn(0, 255)
                    val bin = ((rr shr 3) shl 10) or ((gg shr 3) shl 5) or (bb shr 3)
                    val index = nearest(bin)
                    indices[y * width + x] = index.toByte()
                    val color = palette[index]
                    val er = rr - ((color shr 16) and 0xFF)
                    val eg = gg - ((color shr 8) and 0xFF)
                    val eb = bb - (color and 0xFF)
                    // x maps to error slot x + 1 because the row has one slot of padding.
                    errR[x + 2] += er * 7 / 16
                    errG[x + 2] += eg * 7 / 16
                    errB[x + 2] += eb * 7 / 16
                    nextR[x] += er * 3 / 16
                    nextG[x] += eg * 3 / 16
                    nextB[x] += eb * 3 / 16
                    nextR[x + 1] += er * 5 / 16
                    nextG[x + 1] += eg * 5 / 16
                    nextB[x + 1] += eb * 5 / 16
                    nextR[x + 2] += er / 16
                    nextG[x + 2] += eg / 16
                    nextB[x + 2] += eb / 16
                    x++
                }
                val tmpR = errR; errR = nextR; nextR = tmpR
                val tmpG = errG; errG = nextG; nextG = tmpG
                val tmpB = errB; errB = nextB; nextB = tmpB
                y++
            }
        }

        private fun nearest(bin: Int): Int {
            val cached = nearestCache[bin]
            if (cached >= 0) return cached
            val r = ((bin shr 10) and 31) * 255 / 31
            val g = ((bin shr 5) and 31) * 255 / 31
            val b = (bin and 31) * 255 / 31
            var best = 0
            var bestDistance = Long.MAX_VALUE
            for (i in palette.indices) {
                val color = palette[i]
                val dr = r - ((color shr 16) and 0xFF)
                val dg = g - ((color shr 8) and 0xFF)
                val db = b - (color and 0xFF)
                val distance = (dr * dr * 2 + dg * dg * 4 + db * db * 3).toLong()
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = i
                }
            }
            nearestCache[bin] = best
            return best
        }

        fun writeLzw(out: OutputStream) {
            val tableSizeLocal = tableSize
            for (i in 0 until tableSizeLocal) {
                val color = if (i < palette.size) palette[i] else 0
                out.write((color shr 16) and 0xFF)
                out.write((color shr 8) and 0xFF)
                out.write(color and 0xFF)
            }
            out.write(tableBits)
            val lzw = LzwWriter(out, tableBits)
            lzw.write(indices)
            lzw.finish()
        }

        private data class Box(val start: Int, val end: Int, val count: Long)

        private fun pack(r: Int, g: Int, b: Int): Int = (r shl 16) or (g shl 8) or b
    }

    private class LzwWriter(private val out: OutputStream, private val minCodeSize: Int) {
        private val clearCode = 1 shl minCodeSize
        private val endCode = clearCode + 1
        private var codeSize = minCodeSize + 1
        private var nextCode = endCode + 1
        private var codes = HashMap<Int, Int>(1 shl 12)
        private var prefix = -1
        private var bitBuffer = 0
        private var bitCount = 0
        private val block = ByteArray(255)
        private var blockLength = 0

        fun write(indices: ByteArray) {
            emit(clearCode, codeSize)
            for (value in indices) {
                val byte = value.toInt() and 0xFF
                if (prefix < 0) {
                    prefix = byte
                    continue
                }
                val key = (prefix shl 8) or byte
                val found = codes[key]
                if (found != null) {
                    prefix = found
                } else {
                    emit(prefix, codeSize)
                    if (nextCode < 4096) {
                        codes[key] = nextCode
                        nextCode++
                        // The decoder is one dictionary entry behind the encoder, so the
                        // width grows only after the next code would not fit.
                        if (nextCode > (1 shl codeSize) && codeSize < 12) codeSize++
                    } else {
                        emit(clearCode, codeSize)
                        codes.clear()
                        nextCode = endCode + 1
                        codeSize = minCodeSize + 1
                    }
                    prefix = byte
                }
            }
            if (prefix >= 0) emit(prefix, codeSize)
            emit(endCode, codeSize)
            flushBits()
            if (blockLength > 0) writeBlock()
            out.write(0)
        }

        private fun emit(code: Int, size: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += size
            while (bitCount >= 8) {
                pushByte(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        private fun flushBits() {
            if (bitCount > 0) {
                pushByte(bitBuffer and 0xFF)
                bitBuffer = 0
                bitCount = 0
            }
        }

        private fun pushByte(value: Int) {
            block[blockLength++] = value.toByte()
            if (blockLength == 255) writeBlock()
        }

        private fun writeBlock() {
            if (blockLength == 0) return
            out.write(blockLength)
            out.write(block, 0, blockLength)
            blockLength = 0
        }

        fun finish() {
            // sub-block terminator was written by write()
        }
    }
}