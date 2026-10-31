package com.universalconverter.pro.core.io

import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class LittleEndianOutput(file: File, bufferSize: Int = 64 * 1024) : AutoCloseable {
    private val stream: OutputStream = BufferedOutputStream(file.outputStream(), bufferSize)

    fun writeU8(value: Int) {
        stream.write(value and 0xFF)
    }

    fun writeU16(value: Int) {
        stream.write(value and 0xFF)
        stream.write((value ushr 8) and 0xFF)
    }

    fun writeU32(value: Int) {
        stream.write(value and 0xFF)
        stream.write((value ushr 8) and 0xFF)
        stream.write((value ushr 16) and 0xFF)
        stream.write((value ushr 24) and 0xFF)
    }

    fun writeFloat(value: Float) {
        writeU32(value.toRawBits())
    }

    fun write(bytes: ByteArray) {
        stream.write(bytes)
    }

    fun flush() {
        stream.flush()
    }

    override fun close() {
        stream.close()
    }
}

class FileSliceReader(file: File, offset: Long, length: Long = file.length() - offset) : AutoCloseable {
    private val channel: FileChannel = RandomAccessFile(file, "r").channel
    private val buffer: ByteBuffer

    init {
        require(offset >= 0 && length >= 0) { "Invalid file slice" }
        buffer = channel.map(FileChannel.MapMode.READ_ONLY, offset, length)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
    }

    val bufferLittleEndian: ByteBuffer get() = buffer

    fun order(order: ByteOrder) {
        buffer.order(order)
    }

    override fun close() {
        channel.close()
    }
}

fun ByteBuffer.readUInt32Le(offset: Int): Long =
    (get(offset).toLong() and 0xFF) or
        ((get(offset + 1).toLong() and 0xFF) shl 8) or
        ((get(offset + 2).toLong() and 0xFF) shl 16) or
        ((get(offset + 3).toLong() and 0xFF) shl 24)

fun ByteBuffer.getAscii(offset: Int, length: Int): String {
    val bytes = ByteArray(length)
    for (i in 0 until length) bytes[i] = get(offset + i)
    return String(bytes, Charsets.US_ASCII)
}

fun ByteBuffer.getBytes(offset: Int, length: Int): ByteArray {
    val bytes = ByteArray(length)
    for (i in 0 until length) bytes[i] = get(offset + i)
    return bytes
}