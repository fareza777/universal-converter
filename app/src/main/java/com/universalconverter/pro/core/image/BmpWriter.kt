package com.universalconverter.pro.core.image

import com.universalconverter.pro.core.io.ByteList
import java.io.File

object BmpWriter {

    fun write(image: PixelImage, file: File) {
        val channels = if (image.hasTransparency()) 4 else 3
        val headerSize = 54
        val rowBytes = image.width * channels
        val stride = (rowBytes + 3) and 3.inv()
        val pixelBytes = stride * image.height
        val out = ByteList(headerSize + pixelBytes)
        out.writeAscii("BM")
        out.writeU32LE(headerSize + pixelBytes)
        out.writeU32LE(0)
        out.writeU32LE(headerSize)
        out.writeU32LE(40)
        out.writeU32LE(image.width)
        out.writeU32LE(image.height)
        out.writeU16LE(1)
        out.writeU16LE(channels * 8)
        out.writeU32LE(0)
        out.writeU32LE(pixelBytes)
        out.writeU32LE(2835)
        out.writeU32LE(2835)
        out.writeU32LE(0)
        out.writeU32LE(0)
        for (y in image.height - 1 downTo 0) {
            var written = 0
            val row = y * image.width
            for (x in 0 until image.width) {
                val pixel = image.argb[row + x]
                out.writeByte(pixel and 0xFF)
                out.writeByte((pixel shr 8) and 0xFF)
                out.writeByte((pixel shr 16) and 0xFF)
                written += 3
                if (channels == 4) {
                    out.writeByte((pixel ushr 24) and 0xFF)
                    written++
                }
            }
            repeat(stride - written) { out.writeByte(0) }
        }
        file.writeBytes(out.toByteArray())
    }
}