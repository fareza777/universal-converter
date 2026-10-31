package com.universalconverter.pro.core.mesh

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Bounds(
    val minX: Float, val minY: Float, val minZ: Float,
    val maxX: Float, val maxY: Float, val maxZ: Float,
) {
    val sizeX: Float get() = maxX - minX
    val sizeY: Float get() = maxY - minY
    val sizeZ: Float get() = maxZ - minZ
    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerY: Float get() = (minY + maxY) * 0.5f
    val centerZ: Float get() = (minZ + maxZ) * 0.5f
    val diagonal: Float get() = sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ)

    fun scaled(by: Float) = Bounds(
        minX * by, minY * by, minZ * by,
        maxX * by, maxY * by, maxZ * by,
    )

    companion object {
        val Empty = Bounds(0f, 0f, 0f, 0f, 0f, 0f)
    }
}

class Mesh(
    val positions: FloatArray,
    val indices: IntArray,
    var normals: FloatArray? = null,
    var uvs: FloatArray? = null,
    /** Linear RGBA, 4 values per vertex. */
    var colors: FloatArray? = null,
    var name: String = "model",
) {
    init {
        require(positions.size % 3 == 0) { "Vertex positions must be xyz triples" }
        require(indices.isNotEmpty()) { "A mesh needs at least one triangle" }
        require(indices.size % 3 == 0) { "Triangle indices must be a multiple of three" }
        val vertexCount = positions.size / 3
        for (index in indices) {
            require(index in 0 until vertexCount) { "Index $index is outside the vertex array" }
        }
        require(normals == null || normals!!.size == positions.size) { "Normal count does not match vertices" }
        require(uvs == null || uvs!!.size == vertexCount * 2) { "UV count does not match vertices" }
        require(colors == null || colors!!.size == vertexCount * 4) { "Color count does not match vertices" }
    }

    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    fun bounds(): Bounds {
        if (positions.isEmpty()) return Bounds.Empty
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < positions.size) {
            val x = positions[i]
            val y = positions[i + 1]
            val z = positions[i + 2]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
            i += 3
        }
        return Bounds(minX, minY, minZ, maxX, maxY, maxZ)
    }

    fun computeNormals(): FloatArray {
        val out = FloatArray(positions.size)
        var i = 0
        while (i < indices.size) {
            val a = indices[i] * 3
            val b = indices[i + 1] * 3
            val c = indices[i + 2] * 3
            val ax = positions[a]
            val ay = positions[a + 1]
            val az = positions[a + 2]
            val abx = positions[b] - ax
            val aby = positions[b + 1] - ay
            val abz = positions[b + 2] - az
            val acx = positions[c] - ax
            val acy = positions[c + 1] - ay
            val acz = positions[c + 2] - az
            var nx = aby * acz - abz * acy
            var ny = abz * acx - abx * acz
            var nz = abx * acy - aby * acx
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 1e-20f) {
                nx /= len
                ny /= len
                nz /= len
                // Area weighting by the unnormalised cross product length would be more
                // faithful, but unit face normals keep large triangles from dominating.
                out[a] += nx
                out[a + 1] += ny
                out[a + 2] += nz
                out[b] += nx
                out[b + 1] += ny
                out[b + 2] += nz
                out[c] += nx
                out[c + 1] += ny
                out[c + 2] += nz
            }
            i += 3
        }
        var v = 0
        while (v < out.size) {
            var x = out[v]
            var y = out[v + 1]
            var z = out[v + 2]
            val len = sqrt(x * x + y * y + z * z)
            if (len < 1e-20f) {
                x = 0f
                y = 1f
                z = 0f
            } else {
                x /= len
                y /= len
                z /= len
            }
            out[v] = x
            out[v + 1] = y
            out[v + 2] = z
            v += 3
        }
        return out
    }

    fun ensureNormals() {
        if (normals == null) normals = computeNormals()
    }

    fun transformed(transform: Mat4, newName: String = name): Mesh {
        val count = vertexCount
        val newPositions = FloatArray(positions.size)
        val tmp = FloatArray(3)
        var i = 0
        var v = 0
        while (i < positions.size) {
            transform.transformPoint(positions[i], positions[i + 1], positions[i + 2], tmp)
            newPositions[v] = tmp[0]
            newPositions[v + 1] = tmp[1]
            newPositions[v + 2] = tmp[2]
            i += 3
            v += 3
        }
        val nm = transform.normalMatrix()
        val newNormals = normals?.let { old ->
            val result = FloatArray(old.size)
            var j = 0
            while (j < old.size) {
                val x = nm.m[0] * old[j] + nm.m[4] * old[j + 1] + nm.m[8] * old[j + 2]
                val y = nm.m[1] * old[j] + nm.m[5] * old[j + 1] + nm.m[9] * old[j + 2]
                val z = nm.m[2] * old[j] + nm.m[6] * old[j + 1] + nm.m[10] * old[j + 2]
                val n = normalize3(x, y, z)
                result[j] = n[0]
                result[j + 1] = n[1]
                result[j + 2] = n[2]
                j += 3
            }
            result
        }
        val newIndices = if (transform.isMirrored()) {
            val fixed = indices.copyOf()
            var k = 0
            while (k < fixed.size) {
                val swap = fixed[k + 1]
                fixed[k + 1] = fixed[k + 2]
                fixed[k + 2] = swap
                k += 3
            }
            fixed
        } else {
            indices.copyOf()
        }
        return Mesh(
            positions = newPositions,
            indices = newIndices,
            normals = newNormals,
            uvs = uvs?.copyOf(),
            colors = colors?.copyOf(),
            name = newName,
        )
    }

    fun triangleCentroid(triangle: Int): FloatArray {
        val base = triangle * 3
        val a = indices[base] * 3
        val b = indices[base + 1] * 3
        val c = indices[base + 2] * 3
        return floatArrayOf(
            (positions[a] + positions[b] + positions[c]) / 3f,
            (positions[a + 1] + positions[b + 1] + positions[c + 1]) / 3f,
            (positions[a + 2] + positions[b + 2] + positions[c + 2]) / 3f,
        )
    }

    fun sanitized(): Mesh {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < positions.size) {
            minX = min(minX, positions[i])
            minY = min(minY, positions[i + 1])
            minZ = min(minZ, positions[i + 2])
            maxX = max(maxX, positions[i])
            maxY = max(maxY, positions[i + 1])
            maxZ = max(maxZ, positions[i + 2])
            i += 3
        }
        require(abs(minX) < 1e9f && abs(maxX) < 1e9f) { "Model coordinates are out of range" }
        return this
    }
}