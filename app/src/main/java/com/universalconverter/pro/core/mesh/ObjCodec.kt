package com.universalconverter.pro.core.mesh

import com.universalconverter.pro.core.io.CountingInputStream
import com.universalconverter.pro.core.io.FloatList
import com.universalconverter.pro.core.io.IntList
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import kotlin.math.min

object ObjCodec {

    private const val MAX_VERTICES = 12_000_000
    private const val MAX_INDICES = 36_000_000

    fun read(file: File, progress: (Float) -> Unit = {}): MeshLoadResult {
        val sourcePositions = FloatList(1 shl 16)
        val sourceNormals = FloatList(1 shl 14)
        val sourceUvs = FloatList(1 shl 14)
        val sourceColors = FloatList(0)
        var hasVertexColors = false

        val finalPositions = FloatList(1 shl 16)
        val finalSourcePosition = IntList(1 shl 16)
        val finalSourceNormal = IntList(1 shl 16)
        val finalSourceUv = IntList(1 shl 16)
        val indices = IntList(1 shl 16)
        val cornerToVertex = HashMap<Long, Int>(1 shl 17)
        val warnings = mutableListOf<String>()
        var allCornersHaveNormals = true
        var anyCornerHasUv = false
        var skipped = 0
        var objectName: String? = null

        val totalBytes = file.length()
        val counting = CountingInputStream(BufferedInputStream(file.inputStream(), 1 shl 16))
        BufferedReader(InputStreamReader(counting, Charsets.UTF_8), 1 shl 16).use { reader ->
            var lineNumber = 0
            while (true) {
                val raw = reader.readLine() ?: break
                lineNumber++
                if (lineNumber % 25_000 == 0) {
                    progress(computeProgress(counting.count, totalBytes) * 0.85f)
                }
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val tokens = line.split(' ', '\t').filter { it.isNotEmpty() }
                if (tokens.isEmpty()) continue
                when (tokens[0]) {
                    "v" -> {
                        if (tokens.size >= 4) {
                            val x = tokens[1].toFloatOrNull()
                            val y = tokens[2].toFloatOrNull()
                            val z = tokens[3].toFloatOrNull()
                            if (x == null || y == null || z == null) {
                                skipped++
                            } else {
                                sourcePositions.add(x)
                                sourcePositions.add(y)
                                sourcePositions.add(z)
                                if (tokens.size >= 7) {
                                    val r = tokens[4].toFloatOrNull()
                                    val g = tokens[5].toFloatOrNull()
                                    val b = tokens[6].toFloatOrNull()
                                    if (r != null && g != null && b != null) {
                                        if (!hasVertexColors) {
                                            // Fill earlier vertices with white.
                                            while (sourceColors.size < (sourcePositions.size / 3 - 1) * 4) {
                                                sourceColors.add(1f)
                                            }
                                            hasVertexColors = true
                                        }
                                        val a = if (tokens.size >= 8) tokens[7].toFloatOrNull() ?: 1f else 1f
                                        sourceColors.add(r.coerceIn(0f, 1f))
                                        sourceColors.add(g.coerceIn(0f, 1f))
                                        sourceColors.add(b.coerceIn(0f, 1f))
                                        sourceColors.add(a.coerceIn(0f, 1f))
                                    } else if (hasVertexColors) {
                                        sourceColors.add(1f)
                                        sourceColors.add(1f)
                                        sourceColors.add(1f)
                                        sourceColors.add(1f)
                                    }
                                } else if (hasVertexColors) {
                                    sourceColors.add(1f)
                                    sourceColors.add(1f)
                                    sourceColors.add(1f)
                                    sourceColors.add(1f)
                                }
                            }
                        } else {
                            skipped++
                        }
                    }
                    "vn" -> {
                        if (tokens.size >= 4) {
                            val x = tokens[1].toFloatOrNull()
                            val y = tokens[2].toFloatOrNull()
                            val z = tokens[3].toFloatOrNull()
                            if (x == null || y == null || z == null) skipped++ else {
                                sourceNormals.add(x); sourceNormals.add(y); sourceNormals.add(z)
                            }
                        }
                    }
                    "vt" -> {
                        if (tokens.size >= 2) {
                            val u = tokens[1].toFloatOrNull()
                            val v = if (tokens.size >= 3) tokens[2].toFloatOrNull() ?: 0f else 0f
                            if (u == null) skipped++ else {
                                sourceUvs.add(u); sourceUvs.add(v)
                            }
                        }
                    }
                    "o", "g" -> {
                        if (tokens.size >= 2 && objectName == null) objectName = tokens[1]
                    }
                    "f" -> {
                        if (tokens.size < 4) {
                            skipped++
                            continue
                        }
                        val corners = ArrayList<Int>(tokens.size - 1)
                        val cornerNormals = ArrayList<Int>(tokens.size - 1)
                        val cornerUvs = ArrayList<Int>(tokens.size - 1)
                        var invalid = false
                        for (i in 1 until tokens.size) {
                            val parts = tokens[i].split('/')
                            val p = parts.getOrNull(0)?.toIntOrNull() ?: run { invalid = true; -1 }
                            if (p == -1) break
                            val positionIndex = resolveIndex(p, sourcePositions.size / 3)
                            if (positionIndex < 0) { invalid = true; break }
                            val uvIndex = if (parts.size >= 2 && parts[1].isNotEmpty()) {
                                resolveIndex(parts[1].toIntOrNull() ?: 0, sourceUvs.size / 2)
                            } else -1
                            val normalIndex = if (parts.size >= 3 && parts[2].isNotEmpty()) {
                                resolveIndex(parts[2].toIntOrNull() ?: 0, sourceNormals.size / 3)
                            } else -1
                            if ((parts.size >= 2 && parts[1].isNotEmpty() && uvIndex < 0) ||
                                (parts.size >= 3 && parts[2].isNotEmpty() && normalIndex < 0)
                            ) {
                                invalid = true
                                break
                            }
                            corners.add(positionIndex)
                            cornerNormals.add(normalIndex)
                            cornerUvs.add(uvIndex)
                        }
                        if (invalid || corners.size < 3) {
                            skipped++
                            continue
                        }
                        val triangle = IntArray(corners.size)
                        for (i in corners.indices) {
                            val p = corners[i]
                            val n = cornerNormals[i]
                            val uv = cornerUvs[i]
                            if (n < 0) allCornersHaveNormals = false
                            if (uv >= 0) anyCornerHasUv = true
                            val key = (p.toLong() shl 32) or ((n + 1).toLong() and 0xFFFFFFFFL)
                            var vertex = cornerToVertex[key]
                            if (vertex == null) {
                                vertex = finalPositions.size / 3
                                if (vertex >= MAX_VERTICES) throw MeshFormatException("This OBJ has more than $MAX_VERTICES vertices.")
                                finalPositions.add(sourcePositions.data[p * 3])
                                finalPositions.add(sourcePositions.data[p * 3 + 1])
                                finalPositions.add(sourcePositions.data[p * 3 + 2])
                                finalSourcePosition.add(p)
                                finalSourceNormal.add(n)
                                finalSourceUv.add(uv)
                                cornerToVertex[key] = vertex
                            }
                            triangle[i] = vertex
                        }
                        for (i in 1 until triangle.size - 1) {
                            indices.add(triangle[0])
                            indices.add(triangle[i])
                            indices.add(triangle[i + 1])
                            if (indices.size > MAX_INDICES) {
                                throw MeshFormatException("This OBJ has more than ${MAX_INDICES / 3} triangles.")
                            }
                        }
                    }
                }
            }
        }

        if (indices.size == 0) {
            throw MeshFormatException("No triangle faces were found in this OBJ.")
        }

        val finalCount = finalPositions.size / 3
        val normals = if (allCornersHaveNormals && sourceNormals.size / 3 > 0) {
            FloatArray(finalCount * 3).also { out ->
                for (i in 0 until finalCount) {
                    val source = finalSourceNormal.data[i]
                    if (source >= 0) {
                        out[i * 3] = sourceNormals.data[source * 3]
                        out[i * 3 + 1] = sourceNormals.data[source * 3 + 1]
                        out[i * 3 + 2] = sourceNormals.data[source * 3 + 2]
                    } else {
                        out[i * 3 + 1] = 1f
                    }
                }
            }
        } else null

        val uvs = if (anyCornerHasUv) {
            FloatArray(finalCount * 2).also { out ->
                for (i in 0 until finalCount) {
                    val source = finalSourceUv.data[i]
                    if (source >= 0 && source * 2 + 1 < sourceUvs.size) {
                        out[i * 2] = sourceUvs.data[source * 2]
                        out[i * 2 + 1] = sourceUvs.data[source * 2 + 1]
                    }
                }
            }
        } else null

        val colors = if (hasVertexColors && sourceColors.size >= sourcePositions.size / 3 * 4) {
            FloatArray(finalCount * 4).also { out ->
                for (i in 0 until finalCount) {
                    val source = finalSourcePosition.data[i]
                    out[i * 4] = sourceColors.data[source * 4]
                    out[i * 4 + 1] = sourceColors.data[source * 4 + 1]
                    out[i * 4 + 2] = sourceColors.data[source * 4 + 2]
                    out[i * 4 + 3] = sourceColors.data[source * 4 + 3]
                }
            }
        } else null

        if (skipped > 0) {
            warnings += "$skipped malformed OBJ lines or faces were skipped."
        }
        progress(1f)
        val mesh = Mesh(
            positions = finalPositions.toArray(),
            indices = indices.toArray(),
            normals = normals,
            uvs = uvs,
            colors = colors,
            name = objectName ?: sanitizeMeshName(file.nameWithoutExtension),
        )
        if (mesh.normals == null) mesh.ensureNormals()
        return MeshLoadResult(mesh, warnings)
    }

    private fun resolveIndex(raw: Int, count: Int): Int {
        return if (raw > 0) {
            raw - 1
        } else if (raw < 0) {
            count + raw
        } else {
            -1
        }
    }

    fun write(mesh: Mesh, file: File, progress: (Float) -> Unit = {}) {
        val name = sanitizeMeshName(mesh.name)
        val normals = mesh.normals ?: mesh.computeNormals()
        val hasUvs = mesh.uvs != null
        val colors = mesh.colors
        BufferedWriter(file.writer(Charsets.UTF_8), 1 shl 16).use { writer ->
            writer.write("# Universal Converter\n")
            writer.write("# ${mesh.vertexCount} vertices, ${mesh.triangleCount} triangles\n")
            writer.write("o $name\n")
            var lastProgress = 0
            for (v in 0 until mesh.vertexCount) {
                writer.write("v ")
                writer.write(mesh.positions[v * 3].toString())
                writer.write(" ")
                writer.write(mesh.positions[v * 3 + 1].toString())
                writer.write(" ")
                writer.write(mesh.positions[v * 3 + 2].toString())
                if (colors != null) {
                    writer.write(" ")
                    writer.write(colors[v * 4].toString())
                    writer.write(" ")
                    writer.write(colors[v * 4 + 1].toString())
                    writer.write(" ")
                    writer.write(colors[v * 4 + 2].toString())
                }
                writer.write("\n")
                if (v - lastProgress > 50_000) {
                    progress(v.toFloat() / mesh.vertexCount * 0.6f)
                    lastProgress = v
                }
            }
            for (v in 0 until mesh.vertexCount) {
                writer.write("vn ")
                writer.write(normals[v * 3].toString())
                writer.write(" ")
                writer.write(normals[v * 3 + 1].toString())
                writer.write(" ")
                writer.write(normals[v * 3 + 2].toString())
                writer.write("\n")
            }
            if (hasUvs) {
                val uvs = mesh.uvs!!
                for (v in 0 until mesh.vertexCount) {
                    writer.write("vt ")
                    writer.write(uvs[v * 2].toString())
                    writer.write(" ")
                    writer.write(uvs[v * 2 + 1].toString())
                    writer.write("\n")
                }
            }
            writer.write("s 1\n")
            val indices = mesh.indices
            var i = 0
            var tri = 0
            while (i < indices.size) {
                val a = indices[i] + 1
                val b = indices[i + 1] + 1
                val c = indices[i + 2] + 1
                if (hasUvs) {
                    writer.write("f $a/$a/$a $b/$b/$b $c/$c/$c\n")
                } else {
                    writer.write("f $a//$a $b//$b $c//$c\n")
                }
                i += 3
                tri++
                if (tri % 100_000 == 0) progress(0.6f + tri.toFloat() / mesh.triangleCount * 0.4f)
            }
        }
        progress(1f)
    }
}