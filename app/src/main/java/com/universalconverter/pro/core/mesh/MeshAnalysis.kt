package com.universalconverter.pro.core.mesh

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class MeshHealth(
    val triangleCount: Int,
    val vertexCount: Int,
    val bounds: Bounds,
    val surfaceArea: Double,
    val volume: Double,
    val shellCount: Int,
    val boundaryEdges: Int,
    val nonManifoldEdges: Int,
    val degenerateFaces: Int,
    val invertedFacePercent: Float,
    val watertight: Boolean,
    val manifold: Boolean,
    val topologyChecked: Boolean,
    val printReady: Boolean,
    val score: Int,
    val issues: List<String>,
) {
    val hasInvertedNormals: Boolean get() = invertedFacePercent >= 35f
    val isClosed: Boolean get() = watertight && manifold
}

object MeshAnalyzer {

    fun analyze(mesh: Mesh, maxTopologyEdges: Int = 4_000_000): MeshHealth {
        val bounds = mesh.bounds()
        val diagonal = max(bounds.diagonal, 1e-6f)
        val areaEpsilon = diagonal.toDouble() * diagonal * 1e-9
        var surfaceArea = 0.0
        var signedVolume = 0.0
        var degenerate = 0
        var invertedFaces = 0
        var normalsPresent = mesh.normals != null
        val normals = mesh.normals

        val positions = mesh.positions
        val indices = mesh.indices
        var t = 0
        while (t < indices.size) {
            val ia = indices[t] * 3
            val ib = indices[t + 1] * 3
            val ic = indices[t + 2] * 3
            val ax = positions[ia].toDouble()
            val ay = positions[ia + 1].toDouble()
            val az = positions[ia + 2].toDouble()
            val bx = positions[ib].toDouble()
            val by = positions[ib + 1].toDouble()
            val bz = positions[ib + 2].toDouble()
            val cx = positions[ic].toDouble()
            val cy = positions[ic + 1].toDouble()
            val cz = positions[ic + 2].toDouble()
            val abx = bx - ax
            val aby = by - ay
            val abz = bz - az
            val acx = cx - ax
            val acy = cy - ay
            val acz = cz - az
            val nx = aby * acz - abz * acy
            val ny = abz * acx - abx * acz
            val nz = abx * acy - aby * acx
            val area = 0.5 * sqrt(nx * nx + ny * ny + nz * nz)
            surfaceArea += area
            if (area <= areaEpsilon) degenerate++
            signedVolume += (ax * (by * cz - bz * cy) -
                ay * (bx * cz - bz * cx) +
                az * (bx * cy - by * cx)) / 6.0
            if (normalsPresent && normals != null && area > areaEpsilon) {
                val inv = 1.0 / (2.0 * area)
                val fnx = nx * inv
                val fny = ny * inv
                val fnz = nz * inv
                var dotSum = 0.0
                dotSum += fnx * normals[ia] + fny * normals[ia + 1] + fnz * normals[ia + 2]
                dotSum += fnx * normals[ib] + fny * normals[ib + 1] + fnz * normals[ib + 2]
                dotSum += fnx * normals[ic] + fny * normals[ic + 1] + fnz * normals[ic + 2]
                if (dotSum < -0.6) invertedFaces++
            }
            t += 3
        }

        val tolerance = max(1e-6f, diagonal * 1.5e-6f)
        val welder = PositionWelder(tolerance)
        val welded = IntArray(mesh.vertexCount)
        var v = 0
        while (v < mesh.vertexCount) {
            welded[v] = welder.intern(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])
            v++
        }

        var topologyChecked = true
        var boundary = 0
        var nonManifold = 0
        var shells = 0
        if (mesh.triangleCount * 3L <= maxTopologyEdges.toLong() * 2L) {
            val edgeCounts = HashMap<Long, Int>(mesh.triangleCount * 2)
            t = 0
            while (t < indices.size) {
                val a = welded[indices[t]]
                val b = welded[indices[t + 1]]
                val c = welded[indices[t + 2]]
                if (a != b && b != c && a != c) {
                    addEdge(edgeCounts, a, b)
                    addEdge(edgeCounts, b, c)
                    addEdge(edgeCounts, c, a)
                }
                t += 3
                if (edgeCounts.size > maxTopologyEdges) {
                    topologyChecked = false
                    break
                }
            }
            if (topologyChecked) {
                for (count in edgeCounts.values) {
                    if (count == 1) boundary++
                    if (count > 2) nonManifold++
                }
                shells = countShells(welded, indices, welder.size)
            }
        } else {
            topologyChecked = false
        }

        val watertight = topologyChecked && boundary == 0
        val manifold = topologyChecked && nonManifold == 0
        val invertedPercent = if (normalsPresent && mesh.triangleCount > 0) {
            invertedFaces * 100f / mesh.triangleCount
        } else {
            0f
        }
        val volumeNegative = signedVolume < 0.0

        val issues = mutableListOf<String>()
        if (!topologyChecked) {
            issues += "This model is too dense for a full topology check."
        } else {
            if (boundary > 0) {
                issues += "$boundary open edge${if (boundary == 1) "" else "s"} — the surface is not watertight."
            }
            if (nonManifold > 0) {
                issues += "$nonManifold edge${if (nonManifold == 1) " is" else "s are"} shared by more than two faces."
            }
            if (watertight && manifold && shells > 1) {
                issues += "$shells separate closed shells — check that this is intentional."
            }
        }
        if (degenerate > 0) {
            issues += "$degenerate degenerate triangle${if (degenerate == 1) "" else "s"} with no measurable area."
        }
        if (normalsPresent && invertedPercent >= 35f) {
            issues += "Normals point inward on about ${invertedPercent.toInt()}% of faces."
        }
        if (volumeNegative) {
            issues += "Face winding is inside-out — the signed volume is negative."
        }
        if (bounds.sizeX <= 1e-6f || bounds.sizeY <= 1e-6f || bounds.sizeZ <= 1e-6f) {
            issues += "The bounding box is flat on one axis."
        }

        var score = 100
        if (!topologyChecked) score -= 15
        if (!watertight) score -= 30
        if (!manifold) score -= 25
        if (degenerate > 0) score -= minOf(10, degenerate)
        if (normalsPresent && invertedPercent >= 35f) score -= 15
        if (volumeNegative) score -= 10
        if (bounds.diagonal <= 0f) score -= 20
        score = score.coerceIn(0, 100)

        val printReady = topologyChecked && watertight && manifold &&
            degenerate == 0 && !(normalsPresent && invertedPercent >= 35f) && !volumeNegative &&
            abs(signedVolume) > 1e-9 && bounds.sizeX > 0f && bounds.sizeY > 0f && bounds.sizeZ > 0f

        return MeshHealth(
            triangleCount = mesh.triangleCount,
            vertexCount = mesh.vertexCount,
            bounds = bounds,
            surfaceArea = surfaceArea,
            volume = abs(signedVolume),
            shellCount = shells,
            boundaryEdges = boundary,
            nonManifoldEdges = nonManifold,
            degenerateFaces = degenerate,
            invertedFacePercent = invertedPercent,
            watertight = watertight,
            manifold = manifold,
            topologyChecked = topologyChecked,
            printReady = printReady,
            score = score,
            issues = issues,
        )
    }

    private fun addEdge(map: HashMap<Long, Int>, a: Int, b: Int) {
        val low = if (a < b) a else b
        val high = if (a < b) b else a
        val key = (low.toLong() shl 32) or (high.toLong() and 0xFFFFFFFFL)
        map[key] = (map[key] ?: 0) + 1
    }

    private fun countShells(welded: IntArray, indices: IntArray, vertexCount: Int): Int {
        if (vertexCount == 0) return 0
        val parent = IntArray(vertexCount) { it }
        fun find(node: Int): Int {
            var current = node
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }
        var i = 0
        while (i < indices.size) {
            val a = find(welded[indices[i]])
            val b = find(welded[indices[i + 1]])
            val c = find(welded[indices[i + 2]])
            if (a != b) parent[b] = a
            val rootA = find(a)
            val rootC = find(c)
            if (rootA != rootC) parent[rootC] = rootA
            i += 3
        }
        var shells = 0
        for (vertex in 0 until vertexCount) {
            if (find(vertex) == vertex) shells++
        }
        return shells
    }
}