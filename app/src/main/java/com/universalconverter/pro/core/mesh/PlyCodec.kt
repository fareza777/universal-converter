package com.universalconverter.pro.core.mesh

import com.universalconverter.pro.core.io.FloatList
import com.universalconverter.pro.core.io.IntList
import com.universalconverter.pro.core.io.LittleEndianOutput
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.util.StringTokenizer

object PlyCodec {

    private const val MAX_VERTICES = 12_000_000
    private const val MAX_INDICES = 36_000_000

    private data class Property(
        val name: String,
        val type: String,
        val listCountType: String? = null,
        val listItemType: String? = null,
    ) {
        val isList: Boolean get() = listCountType != null
    }

    private data class Element(
        val name: String,
        val count: Int,
        val properties: MutableList<Property> = mutableListOf(),
    )

    private data class Header(
        val format: String,
        val elements: List<Element>,
        val bodyOffset: Long,
    )

    fun read(file: File, progress: (Float) -> Unit = {}): MeshLoadResult {
        val header = readHeader(file)
        val warnings = mutableListOf<String>()
        val positions = FloatList(1 shl 16)
        val normals = FloatList(0)
        val uvs = FloatList(0)
        val colors = FloatList(0)
        val indices = IntList(1 shl 16)
        var vertexCount = 0
        var hasNormals = false
        var hasUvs = false
        var hasColors = false
        val bodyLength = file.length() - header.bodyOffset
        val binary = header.format.startsWith("binary")

        if (binary) {
            val order = if (header.format.contains("big_endian")) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
            FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
                val buffer = channel.map(FileChannel.MapMode.READ_ONLY, header.bodyOffset, bodyLength)
                buffer.order(order)
                var vertexElement: Element? = null
                var faceElement: Element? = null
                for (element in header.elements) {
                    when (element.name) {
                        "vertex" -> {
                            vertexElement = element
                            parseBinaryVertices(
                                buffer, element, positions, normals, uvs, colors,
                                onCount = { vertexCount = it },
                                onFlags = { hN, hU, hC -> hasNormals = hN; hasUvs = hU; hasColors = hC },
                                progress = progress,
                                totalRows = element.count,
                            )
                        }
                        "face" -> {
                            faceElement = element
                            parseBinaryFaces(buffer, element, indices, progress, bodyLength)
                        }
                        else -> skipBinaryElement(buffer, element)
                    }
                }
                if (vertexElement == null) throw MeshFormatException("This PLY has no vertex element.")
                if (faceElement == null) throw MeshFormatException("This PLY has no face element.")
            }
        } else {
            val input = file.inputStream()
            input.skipNBytes(header.bodyOffset)
            val asciiReader = BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16)
            asciiReader.use { r ->
                for (element in header.elements) {
                    when (element.name) {
                        "vertex" -> parseAsciiVertices(
                            r, element, positions, normals, uvs, colors,
                            onCount = { vertexCount = it },
                            onFlags = { hN, hU, hC -> hasNormals = hN; hasUvs = hU; hasColors = hC },
                            progress = progress,
                        )
                        "face" -> parseAsciiFaces(r, element, indices, progress)
                        else -> repeat(element.count) { r.readLine() }
                    }
                }
            }
        }

        if (positions.size == 0) throw MeshFormatException("This PLY has no vertices.")
        if (indices.size == 0) throw MeshFormatException("This PLY has no polygon faces.")
        val mesh = Mesh(
            positions = positions.toArray(),
            indices = indices.toArray(),
            normals = if (hasNormals) normals.toArray() else null,
            uvs = if (hasUvs) uvs.toArray() else null,
            colors = if (hasColors) colors.toArray() else null,
            name = sanitizeMeshName(file.nameWithoutExtension),
        )
        if (mesh.normals == null) mesh.ensureNormals()
        if (mesh.colors != null && mesh.colors!!.size != mesh.vertexCount * 4) {
            warnings += "Incomplete vertex colors were ignored."
            mesh.colors = null
        }
        progress(1f)
        return MeshLoadResult(mesh, warnings)
    }

    private fun readHeader(file: File): Header {
        RandomAccessFile(file, "r").use { raf ->
            val lines = ArrayList<String>(32)
            while (true) {
                val line = raf.readLine() ?: throw MeshFormatException("This PLY has an incomplete header.")
                lines += line.trim()
                if (lines.size > 2000) throw MeshFormatException("This PLY header is unreasonable.")
                if (line.trim() == "end_header") break
            }
            val bodyOffset = raf.filePointer
            if (lines.firstOrNull()?.lowercase() != "ply") throw MeshFormatException("This is not a PLY file.")
            var format = ""
            val elements = mutableListOf<Element>()
            for (line in lines.drop(1)) {
                if (line.isEmpty() || line.startsWith("comment") || line.startsWith("obj_info")) continue
                val tokenizer = StringTokenizer(line)
                when (tokenizer.nextToken()) {
                    "format" -> {
                        format = tokenizer.nextToken().lowercase()
                        if (format != "ascii" && format != "binary_little_endian" && format != "binary_big_endian") {
                            throw MeshFormatException("Unsupported PLY format: $format")
                        }
                    }
                    "element" -> {
                        val name = tokenizer.nextToken()
                        val count = tokenizer.nextToken().toIntOrNull()
                            ?: throw MeshFormatException("Invalid PLY element count.")
                        if (count < 0 || count > MAX_VERTICES * 4) throw MeshFormatException("Unsupported PLY element size.")
                        elements += Element(name, count)
                    }
                    "property" -> {
                        if (elements.isEmpty()) throw MeshFormatException("PLY property outside an element.")
                        if (!tokenizer.hasMoreTokens()) throw MeshFormatException("Invalid PLY property.")
                        val first = tokenizer.nextToken()
                        if (first == "list") {
                            if (!tokenizer.hasMoreTokens()) throw MeshFormatException("Invalid PLY list property.")
                            val countType = tokenizer.nextToken()
                            val itemType = tokenizer.nextToken()
                            val name = tokenizer.nextToken()
                            elements.last().properties += Property(name, itemType, countType, itemType)
                        } else {
                            val name = tokenizer.nextToken()
                            elements.last().properties += Property(name, first)
                        }
                    }
                }
            }
            if (format.isEmpty()) throw MeshFormatException("This PLY does not declare a format.")
            return Header(format, elements, bodyOffset)
        }
    }

    private fun parseBinaryVertices(
        buffer: ByteBuffer,
        element: Element,
        positions: FloatList,
        normals: FloatList,
        uvs: FloatList,
        colors: FloatList,
        onCount: (Int) -> Unit,
        onFlags: (Boolean, Boolean, Boolean) -> Unit,
        progress: (Float) -> Unit,
        totalRows: Int,
    ) {
        if (element.count > MAX_VERTICES) throw MeshFormatException("This PLY has too many vertices.")
        val names = element.properties.map { it.name.lowercase() }
        val hasN = listOf("nx", "ny", "nz").all { it in names }
        val hasU = ("u" in names && "v" in names) || ("s" in names && "t" in names) ||
            ("texture_u" in names && "texture_v" in names)
        val hasC = listOf("red", "green", "blue").all { it in names } ||
            listOf("diffuse_red", "diffuse_green", "diffuse_blue").all { it in names } ||
            listOf("r", "g", "b").all { it in names }
        var x = 0f
        var y = 0f
        var z = 0f
        var nx = 0f
        var ny = 0f
        var nz = 0f
        var u = 0f
        var v = 0f
        var r = 1f
        var g = 1f
        var b = 1f
        var a = 1f
        for (row in 0 until element.count) {
            x = 0f; y = 0f; z = 0f; nx = 0f; ny = 1f; nz = 0f; u = 0f; v = 0f; r = 1f; g = 1f; b = 1f; a = 1f
            for (prop in element.properties) {
                if (prop.isList) {
                    val count = readInt(buffer, prop.listCountType!!)
                    repeat(count) { readNumber(buffer, prop.listItemType!!) }
                    continue
                }
                val value = readNumber(buffer, prop.type)
                when (prop.name.lowercase()) {
                    "x" -> x = value.toFloat()
                    "y" -> y = value.toFloat()
                    "z" -> z = value.toFloat()
                    "nx" -> nx = value.toFloat()
                    "ny" -> ny = value.toFloat()
                    "nz" -> nz = value.toFloat()
                    "u", "s", "texture_u" -> u = value.toFloat()
                    "v", "t", "texture_v" -> v = value.toFloat()
                    "red", "diffuse_red", "r" -> r = colorChannel(value, prop.type)
                    "green", "diffuse_green", "g" -> g = colorChannel(value, prop.type)
                    "blue", "diffuse_blue", "b" -> b = colorChannel(value, prop.type)
                    "alpha", "diffuse_alpha", "a" -> a = colorChannel(value, prop.type)
                }
            }
            positions.add(x); positions.add(y); positions.add(z)
            if (hasN) { normals.add(nx); normals.add(ny); normals.add(nz) }
            if (hasU) { uvs.add(u); uvs.add(v) }
            if (hasC) { colors.add(r); colors.add(g); colors.add(b); colors.add(a) }
            if (row % 200_000 == 0) progress(row.toFloat() / totalRows * 0.5f)
        }
        onCount(element.count)
        onFlags(hasN, hasU, hasC)
    }

    private fun parseBinaryFaces(
        buffer: ByteBuffer,
        element: Element,
        indices: IntList,
        progress: (Float) -> Unit,
        bodyLength: Long,
    ) {
        val listProp = element.properties.firstOrNull {
            it.isList && (it.name == "vertex_indices" || it.name == "vertex_index")
        } ?: throw MeshFormatException("This PLY face element has no vertex index list.")
        for (row in 0 until element.count) {
            for (prop in element.properties) {
                if (prop.isList) {
                    val count = readInt(buffer, prop.listCountType!!)
                    if (prop === listProp) {
                        if (count < 3) {
                            repeat(count) { readNumber(buffer, prop.listItemType!!) }
                            continue
                        }
                        val first = readInt(buffer, prop.listItemType!!)
                        val second = readInt(buffer, prop.listItemType!!)
                        var previous = second
                        for (i in 2 until count) {
                            val current = readInt(buffer, prop.listItemType!!)
                            addTriangle(indices, first, second, current)
                            previous = current
                        }
                    } else {
                        repeat(count) { readNumber(buffer, prop.listItemType!!) }
                    }
                } else {
                    readNumber(buffer, prop.type)
                }
            }
            if (row % 100_000 == 0) progress(0.5f + row.toFloat() / element.count * 0.5f)
        }
    }

    private fun parseAsciiVertices(
        reader: BufferedReader,
        element: Element,
        positions: FloatList,
        normals: FloatList,
        uvs: FloatList,
        colors: FloatList,
        onCount: (Int) -> Unit,
        onFlags: (Boolean, Boolean, Boolean) -> Unit,
        progress: (Float) -> Unit,
    ) {
        if (element.count > MAX_VERTICES) throw MeshFormatException("This PLY has too many vertices.")
        val names = element.properties.map { it.name.lowercase() }
        val hasN = listOf("nx", "ny", "nz").all { it in names }
        val hasU = ("u" in names && "v" in names) || ("s" in names && "t" in names) ||
            ("texture_u" in names && "texture_v" in names)
        val hasC = listOf("red", "green", "blue").all { it in names } ||
            listOf("diffuse_red", "diffuse_green", "diffuse_blue").all { it in names } ||
            listOf("r", "g", "b").all { it in names }
        for (row in 0 until element.count) {
            val line = reader.readLine() ?: throw MeshFormatException("This PLY ended before all vertices were read.")
            val tokens = tokenize(line)
            var x = 0f; var y = 0f; var z = 0f
            var nx = 0f; var ny = 1f; var nz = 0f
            var u = 0f; var v = 0f
            var r = 1f; var g = 1f; var b = 1f; var a = 1f
            var t = 0
            for (prop in element.properties) {
                if (prop.isList) {
                    val count = tokens.getOrNull(t++)?.toIntOrNull() ?: break
                    t += count
                    continue
                }
                val token = tokens.getOrNull(t++) ?: continue
                val value = token.toDoubleOrNull() ?: continue
                when (prop.name.lowercase()) {
                    "x" -> x = value.toFloat()
                    "y" -> y = value.toFloat()
                    "z" -> z = value.toFloat()
                    "nx" -> nx = value.toFloat()
                    "ny" -> ny = value.toFloat()
                    "nz" -> nz = value.toFloat()
                    "u", "s", "texture_u" -> u = value.toFloat()
                    "v", "t", "texture_v" -> v = value.toFloat()
                    "red", "diffuse_red", "r" -> r = colorChannel(value, prop.type)
                    "green", "diffuse_green", "g" -> g = colorChannel(value, prop.type)
                    "blue", "diffuse_blue", "b" -> b = colorChannel(value, prop.type)
                    "alpha", "diffuse_alpha", "a" -> a = colorChannel(value, prop.type)
                }
            }
            positions.add(x); positions.add(y); positions.add(z)
            if (hasN) { normals.add(nx); normals.add(ny); normals.add(nz) }
            if (hasU) { uvs.add(u); uvs.add(v) }
            if (hasC) { colors.add(r); colors.add(g); colors.add(b); colors.add(a) }
            if (row % 100_000 == 0) progress(row.toFloat() / element.count * 0.5f)
        }
        onCount(element.count)
        onFlags(hasN, hasU, hasC)
    }

    private fun parseAsciiFaces(
        reader: BufferedReader,
        element: Element,
        indices: IntList,
        progress: (Float) -> Unit,
    ) {
        for (row in 0 until element.count) {
            val line = reader.readLine() ?: throw MeshFormatException("This PLY ended before all faces were read.")
            val tokens = tokenize(line)
            var t = 0
            for (prop in element.properties) {
                if (prop.isList) {
                    val count = tokens.getOrNull(t++)?.toIntOrNull() ?: break
                    if (prop.name == "vertex_indices" || prop.name == "vertex_index") {
                        if (count >= 3 && t + count <= tokens.size) {
                            val first = tokens[t].toIntOrNull() ?: break
                            val second = tokens[t + 1].toIntOrNull() ?: break
                            for (i in 2 until count) {
                                val third = tokens[t + i].toIntOrNull() ?: break
                                addTriangle(indices, first, second, third)
                            }
                        }
                    }
                    t += count
                } else {
                    t++
                }
            }
            if (row % 100_000 == 0) progress(0.5f + row.toFloat() / element.count * 0.5f)
        }
    }

    private fun skipBinaryElement(buffer: ByteBuffer, element: Element) {
        repeat(element.count) {
            for (prop in element.properties) {
                if (prop.isList) {
                    val count = readInt(buffer, prop.listCountType!!)
                    repeat(count) { readNumber(buffer, prop.listItemType!!) }
                } else {
                    readNumber(buffer, prop.type)
                }
            }
        }
    }

    private fun readInt(buffer: ByteBuffer, type: String): Int = when (type.lowercase()) {
        "char", "int8" -> buffer.get().toInt()
        "uchar", "uint8" -> buffer.get().toInt() and 0xFF
        "short", "int16" -> buffer.short.toInt()
        "ushort", "uint16" -> buffer.short.toInt() and 0xFFFF
        "int", "int32" -> buffer.int
        "uint", "uint32" -> buffer.int
        else -> readNumber(buffer, type).toInt()
    }

    private fun readNumber(buffer: ByteBuffer, type: String): Double = when (type.lowercase()) {
        "char", "int8" -> buffer.get().toDouble()
        "uchar", "uint8" -> (buffer.get().toInt() and 0xFF).toDouble()
        "short", "int16" -> buffer.short.toDouble()
        "ushort", "uint16" -> (buffer.short.toInt() and 0xFFFF).toDouble()
        "int", "int32" -> buffer.int.toDouble()
        "uint", "uint32" -> (buffer.int.toLong() and 0xFFFFFFFFL).toDouble()
        "float", "float32" -> buffer.float.toDouble()
        "double", "float64" -> buffer.double
        else -> throw MeshFormatException("Unsupported PLY property type: $type")
    }

    private fun colorChannel(value: Double, type: String): Float {
        val integerType = type.lowercase() !in setOf("float", "float32", "double", "float64")
        return if (integerType) (value / 255.0).toFloat().coerceIn(0f, 1f)
        else if (value > 1.0) (value / 255.0).toFloat().coerceIn(0f, 1f)
        else value.toFloat().coerceIn(0f, 1f)
    }

    private fun tokenize(line: String): List<String> {
        if (line.isBlank()) return emptyList()
        val out = ArrayList<String>(12)
        val tokenizer = StringTokenizer(line)
        while (tokenizer.hasMoreTokens()) out += tokenizer.nextToken()
        return out
    }

    private fun addTriangle(indices: IntList, a: Int, b: Int, c: Int) {
        if (a < 0 || b < 0 || c < 0) return
        indices.add(a); indices.add(b); indices.add(c)
        if (indices.size > MAX_INDICES) throw MeshFormatException("This PLY has too many faces.")
    }

    fun write(mesh: Mesh, file: File, binary: Boolean, progress: (Float) -> Unit = {}) {
        val normals = mesh.normals ?: mesh.computeNormals()
        val uvs = mesh.uvs
        val colors = mesh.colors
        val header = buildString {
            append("ply\n")
            append(if (binary) "format binary_little_endian 1.0\n" else "format ascii 1.0\n")
            append("comment Created with Universal Converter\n")
            append("element vertex ${mesh.vertexCount}\n")
            append("property float x\nproperty float y\nproperty float z\n")
            append("property float nx\nproperty float ny\nproperty float nz\n")
            if (uvs != null) append("property float u\nproperty float v\n")
            if (colors != null) {
                append("property uchar red\nproperty uchar green\nproperty uchar blue\nproperty uchar alpha\n")
            }
            append("element face ${mesh.triangleCount}\n")
            append("property list uchar int vertex_indices\n")
            append("end_header\n")
        }
        if (binary) {
            file.outputStream().buffered(1 shl 16).use { stream ->
                stream.write(header.toByteArray(StandardCharsets.US_ASCII))
                val buffer = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
                fun flushIfNeeded() {
                    if (buffer.remaining() < 256) {
                        stream.write(buffer.array(), 0, buffer.position())
                        buffer.clear()
                    }
                }
                fun putFloat(value: Float) {
                    flushIfNeeded(); buffer.putFloat(value)
                }
                fun putByte(value: Int) {
                    flushIfNeeded(); buffer.put((value and 0xFF).toByte())
                }
                for (v in 0 until mesh.vertexCount) {
                    putFloat(mesh.positions[v * 3]); putFloat(mesh.positions[v * 3 + 1]); putFloat(mesh.positions[v * 3 + 2])
                    putFloat(normals[v * 3]); putFloat(normals[v * 3 + 1]); putFloat(normals[v * 3 + 2])
                    if (uvs != null) { putFloat(uvs[v * 2]); putFloat(uvs[v * 2 + 1]) }
                    if (colors != null) {
                        putByte((colors[v * 4] * 255f).toInt().coerceIn(0, 255))
                        putByte((colors[v * 4 + 1] * 255f).toInt().coerceIn(0, 255))
                        putByte((colors[v * 4 + 2] * 255f).toInt().coerceIn(0, 255))
                        putByte((colors[v * 4 + 3] * 255f).toInt().coerceIn(0, 255))
                    }
                    if (v % 100_000 == 0) progress(v.toFloat() / mesh.vertexCount * 0.5f)
                }
                val indices = mesh.indices
                var i = 0
                while (i < indices.size) {
                    flushIfNeeded()
                    buffer.put(3)
                    buffer.putInt(indices[i]); buffer.putInt(indices[i + 1]); buffer.putInt(indices[i + 2])
                    i += 3
                    if (i % 300_000 == 0) progress(0.5f + i.toFloat() / indices.size * 0.5f)
                }
                stream.write(buffer.array(), 0, buffer.position())
            }
        } else {
            file.bufferedWriter(Charsets.US_ASCII, 1 shl 16).use { writer ->
                writer.write(header)
                for (v in 0 until mesh.vertexCount) {
                    writer.write(mesh.positions[v * 3].toString())
                    writer.write(" "); writer.write(mesh.positions[v * 3 + 1].toString())
                    writer.write(" "); writer.write(mesh.positions[v * 3 + 2].toString())
                    writer.write(" "); writer.write(normals[v * 3].toString())
                    writer.write(" "); writer.write(normals[v * 3 + 1].toString())
                    writer.write(" "); writer.write(normals[v * 3 + 2].toString())
                    if (uvs != null) {
                        writer.write(" "); writer.write(uvs[v * 2].toString())
                        writer.write(" "); writer.write(uvs[v * 2 + 1].toString())
                    }
                    if (colors != null) {
                        writer.write(" "); writer.write((colors[v * 4] * 255f).toInt().coerceIn(0, 255).toString())
                        writer.write(" "); writer.write((colors[v * 4 + 1] * 255f).toInt().coerceIn(0, 255).toString())
                        writer.write(" "); writer.write((colors[v * 4 + 2] * 255f).toInt().coerceIn(0, 255).toString())
                        writer.write(" "); writer.write((colors[v * 4 + 3] * 255f).toInt().coerceIn(0, 255).toString())
                    }
                    writer.write("\n")
                    if (v % 100_000 == 0) progress(v.toFloat() / mesh.vertexCount * 0.5f)
                }
                val indices = mesh.indices
                var i = 0
                while (i < indices.size) {
                    writer.write("3 ${indices[i]} ${indices[i + 1]} ${indices[i + 2]}\n")
                    i += 3
                    if (i % 300_000 == 0) progress(0.5f + i.toFloat() / indices.size * 0.5f)
                }
            }
        }
        progress(1f)
    }
}