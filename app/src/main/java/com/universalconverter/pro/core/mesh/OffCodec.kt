package com.universalconverter.pro.core.mesh

import com.universalconverter.pro.core.io.FloatList
import com.universalconverter.pro.core.io.IntList
import java.io.BufferedReader
import java.io.File
import java.util.StringTokenizer

object OffCodec {

    private const val MAX_VERTICES = 12_000_000
    private const val MAX_INDICES = 36_000_000

    fun read(file: File, progress: (Float) -> Unit = {}): MeshLoadResult {
        file.bufferedReader(Charsets.UTF_8, 1 shl 16).use { reader ->
            val lines = LineCursor(reader)
            val headerLine = lines.nextNonEmpty() ?: throw MeshFormatException("This OFF file is empty.")
            val headerTokens = tokenize(headerLine)
            var header = headerTokens.firstOrNull()?.uppercase()
                ?: throw MeshFormatException("This OFF file has no header.")
            if (header !in setOf("OFF", "COFF", "NOFF", "CNOFF")) {
                throw MeshFormatException("Unsupported OFF header: $header")
            }
            val hasColors = header == "COFF" || header == "CNOFF"
            val hasNormals = header == "NOFF" || header == "CNOFF"

            var countTokens = headerTokens.drop(1)
            if (countTokens.size < 3) {
                val countLine = lines.nextNonEmpty() ?: throw MeshFormatException("This OFF file has no counts.")
                countTokens = tokenize(countLine)
            }
            if (countTokens.size < 3) throw MeshFormatException("This OFF file has invalid counts.")
            val vertexCount = countTokens[0].toIntOrNull() ?: throw MeshFormatException("Invalid OFF vertex count.")
            val faceCount = countTokens[1].toIntOrNull() ?: throw MeshFormatException("Invalid OFF face count.")
            if (vertexCount < 1 || vertexCount > MAX_VERTICES) throw MeshFormatException("Unsupported OFF vertex count.")
            if (faceCount < 0) throw MeshFormatException("Invalid OFF face count.")

            val positions = FloatList(vertexCount * 3)
            val colors = FloatList(if (hasColors) vertexCount * 4 else 0)
            val normals = FloatList(if (hasNormals) vertexCount * 3 else 0)
            var colorCountDetected = 0
            var normalsComplete = hasNormals
            var colorsComplete = hasColors

            for (v in 0 until vertexCount) {
                val line = lines.nextNonEmpty() ?: throw MeshFormatException("OFF ended before all vertices were read.")
                val tokens = tokenize(line)
                if (tokens.size < 3) throw MeshFormatException("Malformed OFF vertex at index $v.")
                positions.add(tokens[0].toFloatOrNull() ?: throw MeshFormatException("Invalid OFF coordinate."))
                positions.add(tokens[1].toFloatOrNull() ?: throw MeshFormatException("Invalid OFF coordinate."))
                positions.add(tokens[2].toFloatOrNull() ?: throw MeshFormatException("Invalid OFF coordinate."))
                var index = 3
                if (hasColors) {
                    if (colorCountDetected == 0) {
                        colorCountDetected = when {
                            hasNormals && tokens.size - index >= 7 -> 4
                            hasNormals && tokens.size - index >= 6 -> 3
                            tokens.size - index >= 4 -> 4
                            tokens.size - index >= 3 -> 3
                            else -> 0
                        }
                    }
                    if (colorCountDetected > 0 && tokens.size >= index + colorCountDetected) {
                        val r = colorChannel(tokens[index])
                        val g = colorChannel(tokens[index + 1])
                        val b = colorChannel(tokens[index + 2])
                        val a = if (colorCountDetected >= 4) colorChannel(tokens[index + 3]) else 1f
                        colors.add(r); colors.add(g); colors.add(b); colors.add(a)
                        index += colorCountDetected
                    } else {
                        colorsComplete = false
                        repeat(4) { colors.add(1f) }
                    }
                }
                if (hasNormals) {
                    if (tokens.size >= index + 3) {
                        normals.add(tokens[index].toFloatOrNull() ?: 0f)
                        normals.add(tokens[index + 1].toFloatOrNull() ?: 1f)
                        normals.add(tokens[index + 2].toFloatOrNull() ?: 0f)
                    } else {
                        normalsComplete = false
                        normals.add(0f); normals.add(1f); normals.add(0f)
                    }
                }
                if (v % 100_000 == 0) progress(v.toFloat() / vertexCount * 0.5f)
            }

            val indices = IntList(faceCount * 3)
            for (f in 0 until faceCount) {
                val line = lines.nextNonEmpty() ?: break
                val tokens = tokenize(line)
                val count = tokens.firstOrNull()?.toIntOrNull() ?: continue
                if (count < 3 || tokens.size < count + 1) continue
                val first = tokens[1].toIntOrNull() ?: continue
                val second = tokens[2].toIntOrNull() ?: continue
                for (i in 2 until count) {
                    val third = tokens[i + 1].toIntOrNull() ?: continue
                    if (first in 0 until vertexCount && second in 0 until vertexCount && third in 0 until vertexCount) {
                        indices.add(first); indices.add(second); indices.add(third)
                        if (indices.size > MAX_INDICES) throw MeshFormatException("This OFF has too many faces.")
                    }
                }
                if (f % 100_000 == 0) progress(0.5f + f.toFloat() / faceCount * 0.5f)
            }

            if (indices.size == 0) throw MeshFormatException("This OFF file contains no faces.")
            val mesh = Mesh(
                positions = positions.toArray(),
                indices = indices.toArray(),
                normals = if (normalsComplete) normals.toArray() else null,
                colors = if (colorsComplete) colors.toArray() else null,
                name = sanitizeMeshName(file.nameWithoutExtension),
            )
            if (mesh.normals == null) mesh.ensureNormals()
            progress(1f)
            return MeshLoadResult(mesh)
        }
    }

    private fun colorChannel(token: String): Float {
        val value = token.toFloatOrNull() ?: return 1f
        return if (token.contains('.')) value.coerceIn(0f, 1f) else (value / 255f).coerceIn(0f, 1f)
    }

    private fun tokenize(line: String): List<String> {
        val cleaned = line.substringBefore('#').trim()
        if (cleaned.isEmpty()) return emptyList()
        val out = ArrayList<String>(12)
        val tokenizer = StringTokenizer(cleaned)
        while (tokenizer.hasMoreTokens()) out += tokenizer.nextToken()
        return out
    }

    private class LineCursor(private val reader: BufferedReader) {
        fun nextNonEmpty(): String? {
            while (true) {
                val line = reader.readLine() ?: return null
                val stripped = line.substringBefore('#').trim()
                if (stripped.isNotEmpty()) return stripped
            }
        }
    }

    fun write(mesh: Mesh, file: File, progress: (Float) -> Unit = {}) {
        file.bufferedWriter(Charsets.UTF_8, 1 shl 16).use { writer ->
            writer.write("OFF\n")
            writer.write("${mesh.vertexCount} ${mesh.triangleCount} 0\n")
            for (v in 0 until mesh.vertexCount) {
                writer.write("${mesh.positions[v * 3]} ${mesh.positions[v * 3 + 1]} ${mesh.positions[v * 3 + 2]}\n")
                if (v % 100_000 == 0) progress(v.toFloat() / mesh.vertexCount * 0.6f)
            }
            val indices = mesh.indices
            var i = 0
            while (i < indices.size) {
                writer.write("3 ${indices[i]} ${indices[i + 1]} ${indices[i + 2]}\n")
                i += 3
                if (i % 300_000 == 0) progress(0.6f + i.toFloat() / indices.size * 0.4f)
            }
        }
        progress(1f)
    }
}