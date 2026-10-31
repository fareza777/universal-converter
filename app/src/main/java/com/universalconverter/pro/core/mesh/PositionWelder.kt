package com.universalconverter.pro.core.mesh

import kotlin.math.roundToLong

/**
 * Welds vertices by position so topology checks are not fooled by split normals/UV seams.
 * Open addressing keeps this usable for million-triangle meshes without boxing keys.
 */
class PositionWelder(private val tolerance: Float = 1e-5f) {

    private var coordinates = FloatArray(3 * 1024)
    private var count = 0
    private var table = IntArray(2048) { -1 }
    private var mask = table.size - 1
    private val effectiveTolerance = if (tolerance > 0f) tolerance else 1e-6f

    val size: Int get() = count

    fun intern(x: Float, y: Float, z: Float): Int {
        val qx = quantize(x)
        val qy = quantize(y)
        val qz = quantize(z)
        var slot = hash(qx, qy, qz).toInt() and mask
        while (true) {
            val existing = table[slot]
            if (existing < 0) {
                val added = count
                ensureCapacity()
                coordinates[added * 3] = x
                coordinates[added * 3 + 1] = y
                coordinates[added * 3 + 2] = z
                count++
                table[slot] = added
                if (count.toLong() * 10L > table.size.toLong() * 7L) grow()
                return added
            }
            val base = existing * 3
            if (quantize(coordinates[base]) == qx &&
                quantize(coordinates[base + 1]) == qy &&
                quantize(coordinates[base + 2]) == qz
            ) {
                return existing
            }
            slot = (slot + 1) and mask
        }
    }

    fun x(index: Int): Float = coordinates[index * 3]

    fun y(index: Int): Float = coordinates[index * 3 + 1]

    fun z(index: Int): Float = coordinates[index * 3 + 2]

    private fun quantize(value: Float): Long = (value / effectiveTolerance).roundToLong()

    private fun hash(x: Long, y: Long, z: Long): Long {
        var h = x * -0x61c8864680b583ebL
        h = h xor (y * -0x3c6ef372fe94f82bL)
        h = h xor (z * -0x5bd1e9955bd1e995L)
        h = h xor (h ushr 33)
        h *= -0xae502812aa7333L
        h = h xor (h ushr 29)
        return h
    }

    private fun ensureCapacity() {
        if (count * 3 + 3 <= coordinates.size) return
        var next = coordinates.size
        while (next < (count + 1) * 3) next *= 2
        coordinates = coordinates.copyOf(next)
    }

    private fun grow() {
        val old = table
        table = IntArray(old.size * 2) { -1 }
        mask = table.size - 1
        for (index in 0 until count) {
            val base = index * 3
            var slot = hash(
                quantize(coordinates[base]),
                quantize(coordinates[base + 1]),
                quantize(coordinates[base + 2]),
            ).toInt() and mask
            while (table[slot] >= 0) slot = (slot + 1) and mask
            table[slot] = index
        }
    }
}