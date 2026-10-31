package com.universalconverter.pro.core.image

class PixelImage(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(width > 0 && height > 0)
        require(argb.size == width * height)
    }

    fun hasTransparency(): Boolean {
        for (pixel in argb) if ((pixel ushr 24) != 0xFF) return true
        return false
    }

    fun oriented(orientation: Int): PixelImage {
        if (orientation <= 1) return this
        val newWidth = if (orientation in setOf(5, 6, 7, 8)) height else width
        val newHeight = if (orientation in setOf(5, 6, 7, 8)) width else height
        val out = IntArray(newWidth * newHeight)
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                val srcX: Int
                val srcY: Int
                when (orientation) {
                    2 -> { srcX = width - 1 - x; srcY = y }
                    3 -> { srcX = width - 1 - x; srcY = height - 1 - y }
                    4 -> { srcX = x; srcY = height - 1 - y }
                    5 -> { srcX = y; srcY = x }
                    6 -> { srcX = y; srcY = height - 1 - x }
                    7 -> { srcX = width - 1 - y; srcY = height - 1 - x }
                    8 -> { srcX = width - 1 - y; srcY = x }
                    else -> { srcX = x; srcY = y }
                }
                out[y * newWidth + x] = argb[srcY * width + srcX]
            }
        }
        return PixelImage(newWidth, newHeight, out)
    }
}