package com.universalconverter.pro.core.mesh

import com.universalconverter.pro.core.io.ByteList
import com.universalconverter.pro.core.io.CountingInputStream
import com.universalconverter.pro.core.io.FloatList
import com.universalconverter.pro.core.io.IntList
import com.universalconverter.pro.core.io.LittleEndianOutput
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.util.StringTokenizer

object StlCodec {

    private const val MAX_TRIANGLES = 12_000_000

    fun read(file: File, progress: (Float) -> Unit = {}): MeshLoadResult {
        val length = file.length()
        val binary = isBinary(file, length)
        return if (binary) readBinary(file, length, progress) else readAscii(file, length, progress)
    }

    private fun isBinary(file: File, length: Long): Boolean {
        if (length < 84) return false
        try {
            file.inputStream().use { input ->
                val header = ByteArray(84)
                var read = 0
                while (read < 84) {
                    val n = input.read(header, read, 84 - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < 84) return false
                val count = (header[80].toLong() and 0xFF) or
                    ((header[81].toLong() and 0xFF) shl 8) or
                    ((header[82].toLong() and 0xFF) shl 16) or
                    ((header[83].toLong() and 0xFF) shl 24)
                if (count in 1..MAX_TRIANGLES.toLong() && 84L + count * 50L == length) return true
                val start = String(header, 0, minOf(read, 80), StandardCharsets.ISO_8859_1).trimStart()
                return !start.startsWith("solid")
            }
        } catch (_: Exception) {
            return false
        }
    }

    private fun readBinary(file: File, length: Long, progress: (Float) -> Unit): MeshLoadResult {
        val warnings = mutableListOf<String>()
        FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
            val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, length)
            mapped.order(ByteOrder.LITTLE_ENDIAN)
            var headerEnd = 0
            while (headerEnd < 80 && mapped.get(headerEnd) != 0.toByte()) headerEnd++
            val headerName = String(ByteArray(headerEnd) { mapped.get(it) }, StandardCharsets.ISO_8859_1).trim()
            val count = mapped.getInt(80)
            if (count <= 0) throw MeshFormatException("This STL contains no triangles.")
            if (count > MAX_TRIANGLES) throw MeshFormatException("This STL has more than $MAX_TRIANGLES triangles.")
            val expected = 84L + count.toLong() * 50L
            if (expected > length) throw MeshFormatException("The STL is truncated or its triangle count is corrupt.")
            val welder = PositionWelder(1e-5f)
            val positions = FloatList((count * 3).coerceAtMost(1 shl 24))
            val indices = IntList(count * 3)
            var offset = 84
            for (t in 0 until count) {
                // Skip the facet normal, weld the three vertices.
                offset += 12
                val triangle = IntArray(3)
                for (v in 0..2) {
                    val x = mapped.getFloat(offset)
                    val y = mapped.getFloat(offset + 4)
                    val z = mapped.getFloat(offset + 8)
                    offset += 12
                    triangle[v] = welder.intern(x, y, z)
                    if (triangle[v] * 3 >= positions.size) {
                        val base = triangle[v] * 3
                        while (positions.size < base) positions.add(0f)
                        positions.add(x); positions.add(y); positions.add(z)
                    }
                }
                offset += 2
                if (triangle[0] != triangle[1] && triangle[1] != triangle[2] && triangle[0] != triangle[2]) {
                    indices.add(triangle[0]); indices.add(triangle[1]); indices.add(triangle[2])
                }
                if (t % 100_000 == 0) progress(t.toFloat() / count)
            }
            progress(1f)
            if (indices.size == 0) throw MeshFormatException("This STL contains no usable triangles.")
            val mesh = Mesh(
                positions = positions.toArray(),
                indices = indices.toArray(),
                name = headerName.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: sanitizeMeshName(file.nameWithoutExtension),
            )
            mesh.ensureNormals()
            if (headerName.isNotEmpty() && headerName.any { it.code < 32 }) {
                warnings += "The STL header contained binary data and was cleaned up."
            }
            return MeshLoadResult(mesh, warnings)
        }
    }

    private fun readAscii(file: File, length: Long, progress: (Float) -> Unit): MeshLoadResult {
        val welder = PositionWelder(1e-5f)
        val positions = FloatList(1 shl 16)
        val indices = IntList(1 shl 16)
        val warnings = mutableListOf<String>()
        var solidName: String? = null
        var triangle = IntArray(3)
        var vertexInTriangle = 0
        var triangleCount = 0
        val counting = CountingInputStream(BufferedInputStream(file.inputStream(), 1 shl 16))
        BufferedReader(InputStreamReader(counting, StandardCharsets.UTF_8), 1 shl 16).use { reader ->
            var lineNumber = 0
            while (true) {
                val line = reader.readLine() ?: break
                lineNumber++
                if (lineNumber % 25_000 == 0) progress(computeProgress(counting.count, length) * 0.9f)
                val tokenizer = StringTokenizer(line.trim())
                if (!tokenizer.hasMoreTokens()) continue
                when (tokenizer.nextToken().lowercase()) {
                    "solid" -> if (tokenizer.hasMoreTokens() && solidName == null) solidName = tokenizer.nextToken()
                    "vertex" -> {
                        if (!tokenizer.hasMoreTokens()) {
                            warnings += "Skipped an empty vertex on line $lineNumber."
                            vertexInTriangle = 0
                            continue
                        }
                        val x = tokenizer.nextToken().toFloatOrNull()
                        val y = if (tokenizer.hasMoreTokens()) tokenizer.nextToken().toFloatOrNull() else null
                        val z = if (tokenizer.hasMoreTokens()) tokenizer.nextToken().toFloatOrNull() else null
                        if (x == null || y == null || z == null) {
                            warnings += "Skipped a malformed vertex on line $lineNumber."
                            vertexInTriangle = 0
                            continue
                        }
                        val index = welder.intern(x, y, z)
                        if (index * 3 >= positions.size) {
                            val base = index * 3
                            while (positions.size < base) positions.add(0f)
                            positions.add(x); positions.add(y); positions.add(z)
                        }
                        if (vertexInTriangle < 3) triangle[vertexInTriangle] = index
                        vertexInTriangle++
                        if (vertexInTriangle == 3) {
                            if (triangle[0] != triangle[1] && triangle[1] != triangle[2] && triangle[0] != triangle[2]) {
                                indices.add(triangle[0]); indices.add(triangle[1]); indices.add(triangle[2])
                                triangleCount++
                                if (triangleCount > MAX_TRIANGLES) {
                                    throw MeshFormatException("This STL has more than $MAX_TRIANGLES triangles.")
                                }
                            }
                            vertexInTriangle = 0
                            triangle = IntArray(3)
                        }
                    }
                }
            }
        }
        progress(1f)
        if (indices.size == 0) throw MeshFormatException("No STL facets were found.")
        val mesh = Mesh(
            positions = positions.toArray(),
            indices = indices.toArray(),
            name = solidName?.takeIf { it.isNotBlank() } ?: sanitizeMeshName(file.nameWithoutExtension),
        )
        mesh.ensureNormals()
        val uniqueWarnings = warnings.take(3).toMutableList()
        if (warnings.size > 3) uniqueWarnings += "${warnings.size - 3} more malformed lines were skipped."
        return MeshLoadResult(mesh, uniqueWarnings)
    }

    fun write(mesh: Mesh, file: File, binary: Boolean, progress: (Float) -> Unit = {}) {
        if (binary) writeBinary(mesh, file, progress) else writeAscii(mesh, file, progress)
    }

    private fun writeBinary(mesh: Mesh, file: File, progress: (Float) -> Unit) {
        LittleEndianOutput(file).use { out ->
            val header = ByteArray(80)
            val text = "Universal Converter - ${sanitizeMeshName(mesh.name)}"
                .toByteArray(StandardCharsets.US_ASCII)
            text.copyInto(header, 0, 0, minOf(header.size, text.size))
            out.write(header)
            out.writeU32(mesh.triangleCount)
            val positions = mesh.positions
            val indices = mesh.indices
            val tmp = FloatArray(3)
            var i = 0
            var tri = 0
            while (i < indices.size) {
                val a = indices[i] * 3
                val b = indices[i + 1] * 3
                val c = indices[i + 2] * 3
                faceNormal(positions, a, b, c, tmp)
                out.writeFloat(tmp[0]); out.writeFloat(tmp[1]); out.writeFloat(tmp[2])
                out.writeFloat(positions[a]); out.writeFloat(positions[a + 1]); out.writeFloat(positions[a + 2])
                out.writeFloat(positions[b]); out.writeFloat(positions[b + 1]); out.writeFloat(positions[b + 2])
                out.writeFloat(positions[c]); out.writeFloat(positions[c + 1]); out.writeFloat(positions[c + 2])
                out.writeU16(0)
                i += 3
                tri++
                if (tri % 100_000 == 0) progress(tri.toFloat() / mesh.triangleCount)
            }
        }
        progress(1f)
    }

    private fun writeAscii(mesh: Mesh, file: File, progress: (Float) -> Unit) {
        val name = sanitizeMeshName(mesh.name).replace(Regex("[^A-Za-z0-9_.-]"), "_")
        file.bufferedWriter(Charsets.US_ASCII, 1 shl 16).use { writer ->
            writer.write("solid $name\n")
            val positions = mesh.positions
            val indices = mesh.indices
            val tmp = FloatArray(3)
            var i = 0
            var tri = 0
            while (i < indices.size) {
                val a = indices[i] * 3
                val b = indices[i + 1] * 3
                val c = indices[i + 2] * 3
                faceNormal(positions, a, b, c, tmp)
                writer.write("  facet normal ${tmp[0]} ${tmp[1]} ${tmp[2]}\n")
                writer.write("    outer loop\n")
                writer.write("      vertex ${positions[a]} ${positions[a + 1]} ${positions[a + 2]}\n")
                writer.write("      vertex ${positions[b]} ${positions[b + 1]} ${positions[b + 2]}\n")
                writer.write("      vertex ${positions[c]} ${positions[c + 1]} ${positions[c + 2]}\n")
                writer.write("    endloop\n")
                writer.write("  endfacet\n")
                i += 3
                tri++
                if (tri % 50_000 == 0) progress(tri.toFloat() / mesh.triangleCount)
            }
            writer.write("endsolid $name\n")
        }
        progress(1f)
    }

    private fun faceNormal(positions: FloatArray, a: Int, b: Int, c: Int, out: FloatArray) {
        val abx = positions[b] - positions[a]
        val aby = positions[b + 1] - positions[a + 1]
        val abz = positions[b + 2] - positions[a + 2]
        val acx = positions[c] - positions[a]
        val acy = positions[c + 1] - positions[a + 1]
        val acz = positions[c + 2] - positions[a + 2]
        var nx = aby * acz - abz * acy
        var ny = abz * acx - abx * acz
        var nz = abx * acy - aby * acx
        val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
        if (len > 1e-20f) {
            nx /= len; ny /= len; nz /= len
        } else {
            nx = 0f; ny = 1f; nz = 0f
        }
        out[0] = nx; out[1] = ny; out[2] = nz
    }
}