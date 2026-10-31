package com.universalconverter.pro.core.media

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.roundToInt

data class PcmData(
    val sampleRate: Int,
    val channels: Int,
    val rawFile: File,
    val totalFrames: Long,
    val warnings: List<String>,
) {
    val durationUs: Long get() = if (sampleRate > 0) totalFrames * 1_000_000L / sampleRate else 0L
    val frameBytes: Int get() = channels * 2
}

class AudioException(message: String, cause: Throwable? = null) : Exception(message, cause)

class AudioPipeline(private val cacheDir: File) {

    fun decodeToPcm(source: File, sourceFormat: String, cacheKey: String, progress: (Float, String) -> Unit = { _, _ -> }): PcmData {
        if (sourceFormat == "wav") return readWav(source, progress)
        if (sourceFormat == "aiff") return readAiff(source, progress)
        return decodeWithMedia(source, cacheKey, progress)
    }

    private fun newTemp(cacheKey: String): File {
        val dir = File(cacheDir, "media").apply { mkdirs() }
        return File(dir, "$cacheKey-${System.nanoTime()}.pcm")
    }

    private fun readWav(source: File, progress: (Float, String) -> Unit): PcmData {
        RandomAccessFile(source, "r").use { raf ->
            val header = ByteArray(12)
            raf.readFully(header)
            if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF" || String(header, 8, 4, Charsets.US_ASCII) != "WAVE") {
                throw AudioException("This file is not a valid WAV container.")
            }
            var channels = 0
            var sampleRate = 0
            var bitsPerSample = 0
            var audioFormat = 1
            var dataOffset = -1L
            var dataSize = 0L
            val total = raf.length()
            while (raf.filePointer + 8 <= total) {
                val chunkId = ByteArray(4)
                raf.readFully(chunkId)
                val size = readUInt32Le(raf)
                val name = String(chunkId, Charsets.US_ASCII)
                val next = raf.filePointer + size + (size and 1L)
                when (name) {
                    "fmt " -> {
                        val fmt = ByteArray(size.coerceAtMost(64).toInt())
                        raf.readFully(fmt)
                        val buffer = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        audioFormat = buffer.getShort(0).toInt() and 0xFFFF
                        channels = buffer.getShort(2).toInt() and 0xFFFF
                        sampleRate = buffer.getInt(4)
                        bitsPerSample = buffer.getShort(14).toInt() and 0xFFFF
                        if (audioFormat == 0xFFFE && fmt.size >= 26) {
                            audioFormat = buffer.getShort(24).toInt() and 0xFFFF
                        }
                    }
                    "data" -> {
                        dataOffset = raf.filePointer
                        dataSize = size
                    }
                }
                raf.seek(next)
            }
            if (dataOffset < 0 || channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0) {
                throw AudioException("This WAV has no readable PCM data.")
            }
            if (audioFormat !in setOf(1, 3)) {
                throw AudioException("Compressed WAV (format $audioFormat) is not supported. Use PCM WAV.")
            }
            val bytesPerSample = bitsPerSample / 8
            val frameSize = bytesPerSample * channels
            if (frameSize <= 0) throw AudioException("This WAV has an invalid sample layout.")
            val available = minOf(dataSize, total - dataOffset)
            val temp = newTemp("wav")
            val rawOut = BufferedOutputStream(temp.outputStream(), 1 shl 16)
            raf.seek(dataOffset)
            var readBytes = 0L
            val buffer = ByteArray(64 * 1024)
            var carry = ByteArray(0)
            try {
                while (readBytes < available) {
                    val toRead = minOf(buffer.size.toLong(), available - readBytes).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read <= 0) break
                    readBytes += read
                    val combined = if (carry.isEmpty()) buffer.copyOf(read) else carry + buffer.copyOf(read)
                    val usable = combined.size / frameSize * frameSize
                    if (usable > 0) writeConverted(rawOut, combined, usable, audioFormat, bitsPerSample)
                    carry = if (usable < combined.size) combined.copyOfRange(usable, combined.size) else ByteArray(0)
                    progress((readBytes.toDouble() / available).coerceIn(0.0, 1.0).toFloat() * 0.9f, "Reading WAV")
                }
            } finally {
                rawOut.close()
            }
            val frames = temp.length() / (channels * 2L)
            if (frames <= 0) {
                temp.delete()
                throw AudioException("This WAV has no audio frames.")
            }
            val warnings = mutableListOf<String>()
            if (bitsPerSample != 16) warnings += "WAV samples were converted from ${bitsPerSample}-bit to 16-bit PCM."
            return PcmData(sampleRate, channels, temp, frames, warnings)
        }
    }

    private fun readAiff(source: File, progress: (Float, String) -> Unit): PcmData {
        RandomAccessFile(source, "r").use { raf ->
            val form = ByteArray(12)
            raf.readFully(form)
            val container = String(form, 8, 4, Charsets.US_ASCII)
            if (String(form, 0, 4, Charsets.US_ASCII) != "FORM" || container !in setOf("AIFF", "AIFC")) {
                throw AudioException("This file is not a valid AIFF container.")
            }
            var channels = 0
            var sampleFrames = 0L
            var bits = 0
            var sampleRate = 0
            var compression = "NONE"
            var soundOffset = -1L
            var soundSize = 0L
            var blockSize = 0L
            val total = raf.length()
            while (raf.filePointer + 8 <= total) {
                val id = ByteArray(4)
                raf.readFully(id)
                val size = readUInt32Be(raf)
                val name = String(id, Charsets.US_ASCII)
                val next = raf.filePointer + size + (size and 1L)
                when (name) {
                    "COMM" -> {
                        val bytes = ByteArray(size.coerceAtMost(64).toInt())
                        raf.readFully(bytes)
                        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                        channels = buffer.getShort(0).toInt() and 0xFFFF
                        sampleFrames = buffer.getInt(2).toLong() and 0xFFFFFFFFL
                        bits = buffer.getShort(6).toInt() and 0xFFFF
                        sampleRate = extendedToInt(bytes, 8)
                        if (container == "AIFC" && bytes.size >= 22) {
                            compression = String(bytes, 18, 4, Charsets.US_ASCII)
                        }
                    }
                    "SSND" -> {
                        val offset = readUInt32Be(raf)
                        blockSize = readUInt32Be(raf)
                        soundOffset = raf.filePointer + offset
                        soundSize = size - offset - 8
                    }
                }
                raf.seek(next)
            }
            if (soundOffset < 0 || channels <= 0 || sampleRate <= 0 || bits <= 0) {
                throw AudioException("This AIFF has no readable PCM sound data.")
            }
            if (compression !in setOf("NONE", "sowt", "fl32", "FL32")) {
                throw AudioException("AIFF compression $compression is not supported.")
            }
            val littleEndian = compression == "sowt"
            val isFloat = compression.equals("fl32", true)
            val bytesPerSample = if (isFloat) 4 else (bits + 7) / 8
            val frameSize = bytesPerSample * channels
            val available = minOf(soundSize, total - soundOffset)
            val temp = newTemp("aiff")
            val output = BufferedOutputStream(temp.outputStream(), 1 shl 16)
            raf.seek(soundOffset)
            var readBytes = 0L
            val buffer = ByteArray(64 * 1024)
            try {
                while (readBytes < available) {
                    val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), available - readBytes).toInt())
                    if (read <= 0) break
                    val usable = read / frameSize * frameSize
                    if (usable > 0) writeConverted(output, buffer, usable, if (isFloat) 3 else 1, bytesPerSample * 8, littleEndian)
                    readBytes += read
                    progress((readBytes.toDouble() / available).coerceIn(0.0, 1.0).toFloat() * 0.9f, "Reading AIFF")
                }
            } finally {
                output.close()
            }
            val frames = temp.length() / (channels * 2L)
            if (frames == 0L) {
                temp.delete()
                throw AudioException("This AIFF has no audio frames.")
            }
            val warnings = mutableListOf<String>()
            if (bits != 16) warnings += "AIFF samples were converted from ${bits}-bit to 16-bit PCM."
            if (blockSize > 0) warnings += "AIFF block alignment was ignored."
            return PcmData(sampleRate, channels, temp, frames, warnings)
        }
    }

    private fun writeConverted(
        out: BufferedOutputStream,
        bytes: ByteArray,
        usable: Int,
        audioFormat: Int,
        bits: Int,
        littleEndian: Boolean = true,
    ) {
        val bytesPerSample = bits / 8
        val output = ByteArray(1 shl 16)
        var outIndex = 0
        var index = 0
        while (index + bytesPerSample <= usable) {
            val value: Int = if (audioFormat == 3) {
                val raw = if (littleEndian) {
                    (bytes[index].toInt() and 0xFF) or ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[index + 2].toInt() and 0xFF) shl 16) or ((bytes[index + 3].toInt() and 0xFF) shl 24)
                } else {
                    ((bytes[index].toInt() and 0xFF) shl 24) or ((bytes[index + 1].toInt() and 0xFF) shl 16) or
                        ((bytes[index + 2].toInt() and 0xFF) shl 8) or (bytes[index + 3].toInt() and 0xFF)
                }
                (Float.fromBits(raw) * 32767f).roundToInt().coerceIn(-32768, 32767)
            } else when (bits) {
                8 -> ((bytes[index].toInt() and 0xFF) - 128) shl 8
                16 -> if (littleEndian) {
                    (bytes[index].toInt() and 0xFF) or ((bytes[index + 1].toInt() and 0xFF) shl 8)
                } else {
                    ((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)
                }
                24 -> if (littleEndian) {
                    (bytes[index + 1].toInt() and 0xFF) or ((bytes[index + 2].toInt() and 0xFF) shl 8)
                } else {
                    ((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)
                }
                32 -> if (littleEndian) {
                    (bytes[index + 2].toInt() and 0xFF) or ((bytes[index + 3].toInt() and 0xFF) shl 8)
                } else {
                    ((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)
                }
                else -> 0
            }
            if (outIndex + 2 > output.size) {
                out.write(output, 0, outIndex)
                outIndex = 0
            }
            output[outIndex++] = (value and 0xFF).toByte()
            output[outIndex++] = ((value shr 8) and 0xFF).toByte()
            index += bytesPerSample
        }
        if (outIndex > 0) out.write(output, 0, outIndex)
    }

    private fun decodeWithMedia(source: File, cacheKey: String, progress: (Float, String) -> Unit): PcmData {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var temp: File? = null
        try {
            extractor.setDataSource(source.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            if (trackIndex < 0 || format == null) throw AudioException("This file has no audio track.")
            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw AudioException("The audio track has no codec.")
            if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
                throw AudioException("Raw PCM audio in this container is not supported; use WAV or AIFF.")
            }
            val outputFormat = MediaFormat().apply {
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            temp = newTemp(cacheKey)
            val output = BufferedOutputStream(temp.outputStream(), 1 shl 16)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            var lastProgress = 0f
            while (!outputDone) {
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = decoder.outputFormat
                    sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                    channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, channels)
                    pcmEncoding = if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else AudioFormat.ENCODING_PCM_16BIT
                } else if (index >= 0) {
                    if (info.size > 0) {
                        val buffer = decoder.getOutputBuffer(index)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        writePcm(buffer, channels, pcmEncoding, output)
                    }
                    decoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
                if (durationUs > 0) {
                    val fraction = (extractor.sampleTime.coerceAtLeast(0).toDouble() / durationUs).coerceIn(0.0, 1.0).toFloat()
                    if (fraction - lastProgress > 0.02f) {
                        lastProgress = fraction
                        progress(fraction * 0.9f, "Decoding audio")
                    }
                }
            }
            output.flush()
            output.close()
            val frames = temp.length() / (channels * 2L)
            if (frames <= 0) throw AudioException("The audio decoder produced no samples.")
            val warnings = mutableListOf<String>()
            if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) warnings += "Audio was converted to 16-bit PCM."
            if (channels > 2) warnings += "Multichannel audio is preserved when the encoder supports it."
            return PcmData(sampleRate, channels, temp, frames, warnings)
        } catch (error: AudioException) {
            throw error
        } catch (error: Exception) {
            temp?.delete()
            throw AudioException("This audio could not be decoded on this device.", error)
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun writePcm(buffer: ByteBuffer, channels: Int, encoding: Int, output: BufferedOutputStream) {
        val frameBytes = channels * 2
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = buffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                val out = ByteArray(floats.remaining() * 2)
                var index = 0
                while (floats.hasRemaining()) {
                    val value = (floats.get() * 32767f).roundToInt().coerceIn(-32768, 32767)
                    out[index++] = (value and 0xFF).toByte()
                    out[index++] = ((value shr 8) and 0xFF).toByte()
                }
                if (index > 0) output.write(out, 0, index)
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                val out = ByteArray(buffer.remaining() * 2)
                var index = 0
                while (buffer.hasRemaining()) {
                    val value = ((buffer.get().toInt() and 0xFF) - 128) shl 8
                    out[index++] = (value and 0xFF).toByte()
                    out[index++] = ((value shr 8) and 0xFF).toByte()
                }
                if (index > 0) output.write(out, 0, index)
            }
            else -> {
                val length = buffer.remaining() / frameBytes * frameBytes
                if (length <= 0) return
                val bytes = ByteArray(length)
                buffer.get(bytes)
                output.write(bytes)
            }
        }
    }

    fun writeWav(pcm: PcmData, output: File) {
        val dataBytes = pcm.rawFile.length()
        val byteRate = pcm.sampleRate * pcm.channels * 2
        val blockAlign = pcm.channels * 2
        BufferedOutputStream(output.outputStream(), 1 shl 16).use { out ->
            fun ascii(text: String) = out.write(text.toByteArray(Charsets.US_ASCII))
            fun u32(value: Long) {
                out.write((value and 0xFF).toInt())
                out.write(((value shr 8) and 0xFF).toInt())
                out.write(((value shr 16) and 0xFF).toInt())
                out.write(((value shr 24) and 0xFF).toInt())
            }
            fun u16(value: Int) {
                out.write(value and 0xFF)
                out.write((value shr 8) and 0xFF)
            }
            ascii("RIFF"); u32(36L + dataBytes); ascii("WAVE")
            ascii("fmt "); u32(16); u16(1); u16(pcm.channels); u32(pcm.sampleRate.toLong())
            u32(byteRate.toLong()); u16(blockAlign); u16(16)
            ascii("data"); u32(dataBytes)
            FileInputStream(pcm.rawFile).use { input -> input.copyTo(out, 1 shl 16) }
        }
        pcm.rawFile.setLastModified(System.currentTimeMillis())
    }

    fun encodeAac(pcm: PcmData, output: File, bitrateKbps: Int, progress: (Float, String) -> Unit = { _, _ -> }) {
        val bitrate = bitrateKbps.coerceIn(64, 320) * 1000
        val encoder = try {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        } catch (error: Exception) {
            throw AudioException("This device has no AAC encoder.", error)
        }
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, pcm.sampleRate, pcm.channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            if (output.exists()) output.delete()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val frameBytes = pcm.frameBytes
            FileInputStream(pcm.rawFile).use { input ->
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var fedFrames = 0L
                var bytesRead = 0L
                val totalBytes = pcm.rawFile.length().coerceAtLeast(1)
                var lastProgress = 0f
                while (!outputDone) {
                    if (!inputDone) {
                        val index = encoder.dequeueInputBuffer(20_000)
                        if (index >= 0) {
                            val buffer = encoder.getInputBuffer(index)!!
                            buffer.clear()
                            val maxBytes = buffer.remaining() / frameBytes * frameBytes
                            val chunk = ByteArray(maxBytes)
                            val read = input.read(chunk, 0, maxBytes)
                            if (read <= 0) {
                                encoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val usable = read / frameBytes * frameBytes
                                buffer.put(chunk, 0, usable)
                                val pts = if (pcm.sampleRate > 0) fedFrames * 1_000_000L / pcm.sampleRate else 0L
                                encoder.queueInputBuffer(index, 0, usable, pts, 0)
                                fedFrames += usable / frameBytes
                                bytesRead += usable
                            }
                        }
                    }
                    while (true) {
                        val index = encoder.dequeueOutputBuffer(info, 0)
                        when {
                            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                trackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                            index >= 0 -> {
                                if (info.size > 0 && muxerStarted) {
                                    val buffer = encoder.getOutputBuffer(index)!!
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    muxer.writeSampleData(trackIndex, buffer, info)
                                }
                                encoder.releaseOutputBuffer(index, false)
                                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            }
                            else -> break
                        }
                    }
                    val fraction = bytesRead.toFloat() / totalBytes
                    if (fraction - lastProgress > 0.02f) {
                        lastProgress = fraction
                        progress(fraction * 0.92f, "Encoding AAC")
                    }
                }
            }
            if (!muxerStarted) throw AudioException("The AAC encoder produced no audio track.")
            progress(1f, "Audio ready")
        } catch (error: AudioException) {
            throw error
        } catch (error: Exception) {
            throw AudioException("The audio could not be encoded as AAC on this device.", error)
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            if (muxerStarted) {
                try { muxer?.stop() } catch (_: Exception) {}
            }
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun readUInt32Le(raf: RandomAccessFile): Long {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return (bytes[0].toLong() and 0xFF) or ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or ((bytes[3].toLong() and 0xFF) shl 24)
    }

    private fun readUInt32Be(raf: RandomAccessFile): Long {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return ((bytes[0].toLong() and 0xFF) shl 24) or ((bytes[1].toLong() and 0xFF) shl 16) or
            ((bytes[2].toLong() and 0xFF) shl 8) or (bytes[3].toLong() and 0xFF)
    }

    private fun extendedToInt(bytes: ByteArray, offset: Int): Int {
        if (offset + 10 > bytes.size) return 0
        val negative = bytes[offset].toInt() and 0x80 != 0
        val exponent = (((bytes[offset].toInt() and 0x7F) shl 8) or (bytes[offset + 1].toInt() and 0xFF)) - 16383
        var mantissa = 0.0
        for (i in 2..9) mantissa = mantissa * 256.0 + (bytes[offset + i].toInt() and 0xFF)
        val value = mantissa * Math.pow(2.0, (exponent - 63).toDouble())
        val rounded = value.roundToInt()
        return if (negative) -rounded else rounded
    }
}