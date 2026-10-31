package com.universalconverter.pro.core.mesh

import com.universalconverter.pro.core.io.ByteList
import com.universalconverter.pro.core.io.FloatList
import com.universalconverter.pro.core.io.IntList
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object GltfCodec {

    private const val MAX_VERTICES = 12_000_000
    private const val MAX_INDICES = 36_000_000
    private const val MAX_FILE_BYTES = 400L * 1024L * 1024L

    private data class BufferView(
        val buffer: Int,
        val offset: Int,
        val length: Int,
        val stride: Int?,
    )

    private data class Accessor(
        val bufferView: Int?,
        val byteOffset: Int,
        val componentType: Int,
        val count: Int,
        val type: String,
        val normalized: Boolean,
        val hasSparse: Boolean,
    )

    private data class PrimitiveData(
        val positions: FloatArray,
        val normals: FloatArray?,
        val uvs: FloatArray?,
        val colors: FloatArray?,
        val indices: IntArray,
        val material: Int?,
    )

    fun read(file: File, progress: (Float) -> Unit = {}): MeshLoadResult {
        if (file.length() > MAX_FILE_BYTES) {
            throw MeshFormatException("This 3D file is larger than ${MAX_FILE_BYTES / 1024 / 1024} MB.")
        }
        val bytes = file.readBytes()
        val jsonBytes: ByteArray
        var binChunk: ByteArray? = null
        if (bytes.size >= 12 && bytes[0] == 'g'.code.toByte() && bytes[1] == 'l'.code.toByte() &&
            bytes[2] == 'T'.code.toByte() && bytes[3] == 'F'.code.toByte()
        ) {
            val header = ByteBuffer.wrap(bytes, 0, 12).order(ByteOrder.LITTLE_ENDIAN)
            val version = header.getInt(4)
            if (version != 2) throw MeshFormatException("Only glTF 2.0 binary files are supported.")
            val totalLength = header.getInt(8).toLong() and 0xFFFFFFFFL
            if (totalLength > bytes.size) throw MeshFormatException("This GLB is truncated.")
            var offset = 12
            var jsonFound: ByteArray? = null
            while (offset + 8 <= totalLength) {
                val chunkLength = (bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 3].toInt() and 0xFF) shl 24)
                val chunkType = (bytes[offset + 4].toInt() and 0xFF) or
                    ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 7].toInt() and 0xFF) shl 24)
                val start = offset + 8
                if (start + chunkLength > bytes.size) throw MeshFormatException("This GLB has a corrupt chunk.")
                when (chunkType) {
                    0x4E4F534A -> jsonFound = bytes.copyOfRange(start, start + chunkLength)
                    0x004E4942 -> binChunk = bytes.copyOfRange(start, start + chunkLength)
                }
                offset += 8 + chunkLength
            }
            jsonBytes = jsonFound ?: throw MeshFormatException("This GLB has no JSON chunk.")
        } else {
            jsonBytes = bytes
        }

        val json = try {
            JSONObject(String(jsonBytes, StandardCharsets.UTF_8).trimEnd('\u0000', ' ', '\n', '\r', '\t'))
        } catch (e: Exception) {
            throw MeshFormatException("The glTF JSON could not be parsed.", e)
        }
        val asset = json.optJSONObject("asset")
        val version = asset?.optString("version").orEmpty()
        if (version.isNotEmpty() && !version.startsWith("2")) {
            throw MeshFormatException("glTF $version is not supported. Re-export the model as glTF 2.0.")
        }
        val required = json.optJSONArray("extensionsRequired")
        if (required != null) {
            for (i in 0 until required.length()) {
                val extension = required.optString(i)
                if (extension.contains("draco", true) || extension.contains("meshopt", true)) {
                    throw MeshFormatException("This glTF uses mesh compression ($extension), which is not supported. Re-export without compression.")
                }
            }
        }

        val warnings = mutableListOf<String>()
        val buffers = parseBuffers(json, binChunk)
        val bufferViews = parseBufferViews(json, buffers)
        val accessors = parseAccessors(json)
        val materials = json.optJSONArray("materials")
        val meshes = json.optJSONArray("meshes") ?: throw MeshFormatException("This glTF has no meshes.")
        val nodes = json.optJSONArray("nodes")
        val scenes = json.optJSONArray("scenes")

        val positions = FloatList(1 shl 16)
        val normals = FloatList(1 shl 16)
        val uvs = FloatList(0)
        val colors = FloatList(0)
        val indices = IntList(1 shl 16)
        var allHaveNormals = true
        var anyHaveUvs = false
        var anyHaveColors = false
        var texturesDropped = false
        var skippedPrimitives = 0
        var modelName: String? = null
        var progressSteps = 0

        fun appendPrimitive(primitive: PrimitiveData, transform: Mat4, nodeName: String?) {
            if (modelName == null) modelName = nodeName
            val tint = materialColor(materials, primitive.material)
            if (primitive.material != null && materials != null) {
                val material = materials.optJSONObject(primitive.material)
                val pbr = material?.optJSONObject("pbrMetallicRoughness")
                if (pbr?.optJSONObject("baseColorTexture") != null || material?.optJSONObject("normalTexture") != null) {
                    texturesDropped = true
                }
            }
            val count = primitive.positions.size / 3
            if (positions.size / 3 + count > MAX_VERTICES) {
                throw MeshFormatException("This glTF has more than $MAX_VERTICES vertices.")
            }
            val offset = positions.size / 3
            val transformed = if (!transform.isIdentity()) {
                val temp = FloatArray(3)
                val nm = transform.normalMatrix()
                val newPositions = FloatArray(primitive.positions.size)
                for (v in 0 until count) {
                    transform.transformPoint(
                        primitive.positions[v * 3], primitive.positions[v * 3 + 1], primitive.positions[v * 3 + 2], temp,
                    )
                    newPositions[v * 3] = temp[0]
                    newPositions[v * 3 + 1] = temp[1]
                    newPositions[v * 3 + 2] = temp[2]
                }
                val newNormals = primitive.normals?.let { old ->
                    FloatArray(old.size).also { out ->
                        for (v in 0 until count) {
                            val x = nm.m[0] * old[v * 3] + nm.m[4] * old[v * 3 + 1] + nm.m[8] * old[v * 3 + 2]
                            val y = nm.m[1] * old[v * 3] + nm.m[5] * old[v * 3 + 1] + nm.m[9] * old[v * 3 + 2]
                            val z = nm.m[2] * old[v * 3] + nm.m[6] * old[v * 3 + 1] + nm.m[10] * old[v * 3 + 2]
                            val n = normalize3(x, y, z)
                            out[v * 3] = n[0]; out[v * 3 + 1] = n[1]; out[v * 3 + 2] = n[2]
                        }
                    }
                }
                val newIndices = if (transform.isMirrored()) {
                    val flipped = primitive.indices.copyOf()
                    var k = 0
                    while (k + 2 < flipped.size) {
                        val swap = flipped[k + 1]
                        flipped[k + 1] = flipped[k + 2]
                        flipped[k + 2] = swap
                        k += 3
                    }
                    flipped
                } else {
                    primitive.indices
                }
                PrimitiveData(newPositions, newNormals, primitive.uvs, primitive.colors, newIndices, primitive.material)
            } else {
                primitive
            }
            positions.add(transformed.positions)
            if (allHaveNormals) {
                if (transformed.normals != null && transformed.normals.size == transformed.positions.size) {
                    normals.add(transformed.normals)
                } else {
                    allHaveNormals = false
                }
            }
            if (transformed.uvs != null && transformed.uvs.size == count * 2) {
                anyHaveUvs = true
                uvs.add(transformed.uvs)
            } else {
                repeat(count * 2) { uvs.add(0f) }
            }
            if (transformed.colors != null && transformed.colors.size == count * 4) {
                anyHaveColors = true
                for (v in 0 until count) {
                    colors.add(transformed.colors[v * 4] * tint[0])
                    colors.add(transformed.colors[v * 4 + 1] * tint[1])
                    colors.add(transformed.colors[v * 4 + 2] * tint[2])
                    colors.add(transformed.colors[v * 4 + 3] * tint[3])
                }
            } else {
                for (v in 0 until count) {
                    colors.add(tint[0]); colors.add(tint[1]); colors.add(tint[2]); colors.add(tint[3])
                }
            }
            if (tint[0] < 0.999f || tint[1] < 0.999f || tint[2] < 0.999f || tint[3] < 0.999f) {
                anyHaveColors = true
            }
            var i = 0
            while (i < transformed.indices.size) {
                indices.add(transformed.indices[i] + offset)
                indices.add(transformed.indices[i + 1] + offset)
                indices.add(transformed.indices[i + 2] + offset)
                i += 3
                if (indices.size > MAX_INDICES) throw MeshFormatException("This glTF has too many triangles.")
            }
        }

        fun parseMesh(meshIndex: Int, transform: Mat4, nodeName: String?) {
            val mesh = meshes.optJSONObject(meshIndex)
                ?: throw MeshFormatException("A glTF node points at a missing mesh.")
            if (modelName == null) modelName = mesh.optString("name").takeIf { it.isNotBlank() } ?: nodeName
            val primitives = mesh.optJSONArray("primitives") ?: return
            for (p in 0 until primitives.length()) {
                val primitive = primitives.optJSONObject(p) ?: continue
                val mode = primitive.optInt("mode", 4)
                if (mode != 4 && mode != 5 && mode != 6) {
                    skippedPrimitives++
                    continue
                }
                val data = parsePrimitive(primitive, buffers, bufferViews, accessors) ?: continue
                appendPrimitive(data, transform, nodeName)
                progressSteps++
                progress(minOf(0.9f, progressSteps / 12f))
            }
        }

        val visited = HashSet<Int>()
        fun visitNode(index: Int, parent: Mat4, depth: Int) {
            if (depth > 64 || !visited.add(index)) return
            val node = nodes?.optJSONObject(index) ?: return
            val local = nodeTransform(node)
            val world = parent * local
            val nodeName = node.optString("name").takeIf { it.isNotBlank() }
            if (node.has("mesh")) {
                parseMesh(node.optInt("mesh"), world, nodeName)
            }
            val children = node.optJSONArray("children")
            if (children != null) {
                for (c in 0 until children.length()) visitNode(children.optInt(c), world, depth + 1)
            }
        }

        val sceneIndex = if (json.has("scene")) json.optInt("scene", 0) else 0
        val scene = scenes?.optJSONObject(sceneIndex)
        val rootNodes = scene?.optJSONArray("nodes")
        if (rootNodes != null) {
            for (i in 0 until rootNodes.length()) visitNode(rootNodes.optInt(i), Mat4.identity(), 0)
        } else if (nodes != null) {
            val childIndices = HashSet<Int>()
            for (i in 0 until nodes.length()) {
                val children = nodes.optJSONObject(i)?.optJSONArray("children") ?: continue
                for (c in 0 until children.length()) childIndices.add(children.optInt(c))
            }
            for (i in 0 until nodes.length()) if (i !in childIndices) visitNode(i, Mat4.identity(), 0)
            if (visited.isEmpty()) {
                for (i in 0 until nodes.length()) visitNode(i, Mat4.identity(), 0)
            }
        } else {
            for (m in 0 until meshes.length()) parseMesh(m, Mat4.identity(), null)
        }

        if (indices.size == 0) throw MeshFormatException("No triangle geometry was found in this glTF.")
        if (skippedPrimitives > 0) {
            warnings += "$skippedPrimitives point/line primitive(s) were skipped; only triangles are supported."
        }
        if (texturesDropped) {
            warnings += "Textures were not carried over. Surface color and geometry were preserved."
        }
        if (anyHaveColors) {
            if (colors.size != positions.size / 3 * 4) {
                // Should not happen, but never hand an inconsistent array to Mesh.
                colors.trimTo(0)
            }
        }
        progress(0.97f)
        val mesh = Mesh(
            positions = positions.toArray(),
            indices = indices.toArray(),
            normals = if (allHaveNormals && normals.size == positions.size) normals.toArray() else null,
            uvs = if (anyHaveUvs && uvs.size == positions.size / 3 * 2) uvs.toArray() else null,
            colors = if (anyHaveColors && colors.size == positions.size / 3 * 4) colors.toArray() else null,
            name = sanitizeMeshName(modelName ?: file.nameWithoutExtension),
        )
        if (mesh.normals == null) mesh.ensureNormals()
        progress(1f)
        return MeshLoadResult(mesh, warnings)
    }

    private fun parseBuffers(json: JSONObject, binChunk: ByteArray?): List<ByteArray> {
        val array = json.optJSONArray("buffers") ?: throw MeshFormatException("This glTF has no buffers.")
        val result = ArrayList<ByteArray>(array.length())
        for (i in 0 until array.length()) {
            val buffer = array.optJSONObject(i) ?: throw MeshFormatException("Invalid glTF buffer.")
            val uri = buffer.optString("uri", "")
            val bytes = when {
                uri.isBlank() -> binChunk ?: throw MeshFormatException("This glTF is missing its binary buffer.")
                uri.startsWith("data:", true) -> {
                    val comma = uri.indexOf(',')
                    if (comma < 0) throw MeshFormatException("Invalid data URI in this glTF.")
                    try {
                        Base64.getDecoder().decode(uri.substring(comma + 1))
                    } catch (e: Exception) {
                        throw MeshFormatException("The embedded glTF buffer is corrupt.", e)
                    }
                }
                else -> throw MeshFormatException(
                    "This glTF references an external file (${uri.substringBefore('?')}). " +
                        "Open a self-contained .gltf or a .glb instead."
                )
            }
            result += bytes
        }
        return result
    }

    private fun parseBufferViews(json: JSONObject, buffers: List<ByteArray>): List<BufferView> {
        val array = json.optJSONArray("bufferViews") ?: return emptyList()
        val views = ArrayList<BufferView>(array.length())
        for (i in 0 until array.length()) {
            val view = array.optJSONObject(i) ?: throw MeshFormatException("Invalid glTF buffer view.")
            val bufferIndex = view.optInt("buffer", 0)
            val buffer = buffers.getOrNull(bufferIndex)
                ?: throw MeshFormatException("A glTF buffer view points at a missing buffer.")
            val offset = view.optInt("byteOffset", 0)
            val length = view.optInt("byteLength", 0)
            if (offset < 0 || length <= 0 || offset.toLong() + length > buffer.size) {
                throw MeshFormatException("A glTF buffer view is out of bounds.")
            }
            val stride = if (view.has("byteStride")) view.optInt("byteStride", 0).takeIf { it > 0 } else null
            views += BufferView(bufferIndex, offset, length, stride)
        }
        return views
    }

    private fun parseAccessors(json: JSONObject): List<Accessor> {
        val array = json.optJSONArray("accessors") ?: return emptyList()
        val result = ArrayList<Accessor>(array.length())
        for (i in 0 until array.length()) {
            val accessor = array.optJSONObject(i) ?: throw MeshFormatException("Invalid glTF accessor.")
            val type = accessor.optString("type")
            val count = accessor.optInt("count", 0)
            if (count < 0 || count > MAX_VERTICES * 4) throw MeshFormatException("Unsupported glTF accessor size.")
            result += Accessor(
                bufferView = if (accessor.has("bufferView")) accessor.optInt("bufferView") else null,
                byteOffset = accessor.optInt("byteOffset", 0),
                componentType = accessor.optInt("componentType", 0),
                count = count,
                type = type,
                normalized = accessor.optBoolean("normalized", false),
                hasSparse = accessor.has("sparse"),
            )
        }
        return result
    }

    private fun parsePrimitive(
        primitive: JSONObject,
        buffers: List<ByteArray>,
        views: List<BufferView>,
        accessors: List<Accessor>,
    ): PrimitiveData? {
        val attributes = primitive.optJSONObject("attributes") ?: return null
        if (!attributes.has("POSITION")) return null
        val positionAccessor = accessors.getOrNull(attributes.optInt("POSITION"))
            ?: throw MeshFormatException("A glTF primitive has no valid position accessor.")
        val positions = readFloats(positionAccessor, buffers, views, expectedComponents = 3)
        val vertexCount = positionAccessor.count
        if (vertexCount == 0) return null
        val normals = if (attributes.has("NORMAL")) {
            val accessor = accessors.getOrNull(attributes.optInt("NORMAL"))
            accessor?.let { readFloats(it, buffers, views, 3).also { normalsArray -> normalizeNormals(normalsArray) } }
        } else null
        val uvs = if (attributes.has("TEXCOORD_0")) {
            accessors.getOrNull(attributes.optInt("TEXCOORD_0"))?.let { readFloats(it, buffers, views, 2) }
        } else null
        val colors = if (attributes.has("COLOR_0")) {
            accessors.getOrNull(attributes.optInt("COLOR_0"))?.let { accessor ->
                val components = if (accessor.type == "VEC4") 4 else 3
                val values = readFloats(accessor, buffers, views, components)
                val rgba = FloatArray(vertexCount * 4)
                for (v in 0 until vertexCount) {
                    rgba[v * 4] = values[v * components]
                    rgba[v * 4 + 1] = values[v * components + 1]
                    rgba[v * 4 + 2] = values[v * components + 2]
                    rgba[v * 4 + 3] = if (components == 4) values[v * components + 3] else 1f
                }
                rgba
            }
        } else null

        val rawIndices: IntArray = if (primitive.has("indices")) {
            val accessor = accessors.getOrNull(primitive.optInt("indices"))
                ?: throw MeshFormatException("A glTF primitive has invalid indices.")
            readIndices(accessor, buffers, views)
        } else {
            IntArray(vertexCount) { it }
        }
        val mode = primitive.optInt("mode", 4)
        val triangles = IntList(rawIndices.size)
        when (mode) {
            4 -> {
                var i = 0
                while (i + 2 < rawIndices.size) {
                    triangles.add(rawIndices[i]); triangles.add(rawIndices[i + 1]); triangles.add(rawIndices[i + 2])
                    i += 3
                }
            }
            5 -> {
                var i = 0
                while (i + 2 < rawIndices.size) {
                    if (i % 2 == 0) {
                        triangles.add(rawIndices[i]); triangles.add(rawIndices[i + 1]); triangles.add(rawIndices[i + 2])
                    } else {
                        triangles.add(rawIndices[i + 1]); triangles.add(rawIndices[i]); triangles.add(rawIndices[i + 2])
                    }
                    i++
                }
            }
            6 -> {
                var i = 1
                while (i + 1 < rawIndices.size) {
                    triangles.add(rawIndices[0]); triangles.add(rawIndices[i]); triangles.add(rawIndices[i + 1])
                    i++
                }
            }
            else -> return null
        }
        if (triangles.size == 0) return null
        return PrimitiveData(
            positions = positions,
            normals = normals?.takeIf { it.size == vertexCount * 3 },
            uvs = uvs?.takeIf { it.size == vertexCount * 2 },
            colors = colors,
            indices = triangles.toArray(),
            material = if (primitive.has("material")) primitive.optInt("material") else null,
        )
    }

    private fun readFloats(
        accessor: Accessor,
        buffers: List<ByteArray>,
        views: List<BufferView>,
        expectedComponents: Int,
    ): FloatArray {
        val components = componentsOf(accessor.type)
        if (components != expectedComponents) {
            throw MeshFormatException("A glTF accessor has the wrong shape for its attribute.")
        }
        val size = componentSize(accessor.componentType)
        val view = accessor.bufferView?.let { views.getOrNull(it) }
        if (view == null) {
            if (accessor.hasSparse) {
                throw MeshFormatException("Sparse glTF geometry is not supported.")
            }
            return FloatArray(accessor.count * components)
        }
        val stride = view.stride ?: (size * components)
        val required = accessor.byteOffset.toLong() + (accessor.count.toLong() - 1) * stride + size * components
        if (required > view.length) throw MeshFormatException("A glTF accessor reads past its buffer view.")
        val buffer = buffers.getOrNull(view.buffer) ?: throw MeshFormatException("A glTF accessor reads a missing buffer.")
        val slice = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        val base = view.offset + accessor.byteOffset
        val out = FloatArray(accessor.count * components)
        var index = 0
        for (i in 0 until accessor.count) {
            val start = base + i * stride
            for (c in 0 until components) {
                val offset = start + c * size
                out[index++] = when (accessor.componentType) {
                    5126 -> slice.getFloat(offset)
                    5121 -> {
                        val value = slice.get(offset).toInt() and 0xFF
                        if (accessor.normalized) value / 255f else value.toFloat()
                    }
                    5123 -> {
                        val value = slice.getShort(offset).toInt() and 0xFFFF
                        if (accessor.normalized) value / 65535f else value.toFloat()
                    }
                    5120 -> {
                        val value = slice.get(offset).toInt()
                        if (accessor.normalized) (value / 127f).coerceIn(-1f, 1f) else value.toFloat()
                    }
                    5122 -> {
                        val value = slice.getShort(offset).toInt()
                        if (accessor.normalized) (value / 32767f).coerceIn(-1f, 1f) else value.toFloat()
                    }
                    else -> throw MeshFormatException("Unsupported glTF accessor component type.")
                }
            }
        }
        return out
    }

    private fun readIndices(
        accessor: Accessor,
        buffers: List<ByteArray>,
        views: List<BufferView>,
    ): IntArray {
        if (accessor.type != "SCALAR") throw MeshFormatException("A glTF index accessor is not scalar.")
        val size = componentSize(accessor.componentType)
        val view = accessor.bufferView?.let { views.getOrNull(it) }
            ?: throw MeshFormatException("A glTF index accessor has no buffer.")
        val stride = view.stride ?: size
        val required = accessor.byteOffset.toLong() + (accessor.count.toLong() - 1) * stride + size
        if (required > view.length) throw MeshFormatException("A glTF index accessor reads past its buffer view.")
        val buffer = buffers.getOrNull(view.buffer) ?: throw MeshFormatException("A glTF index buffer is missing.")
        val slice = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        val base = view.offset + accessor.byteOffset
        val out = IntArray(accessor.count)
        for (i in 0 until accessor.count) {
            val offset = base + i * stride
            out[i] = when (accessor.componentType) {
                5121 -> slice.get(offset).toInt() and 0xFF
                5123 -> slice.getShort(offset).toInt() and 0xFFFF
                5125 -> slice.getInt(offset)
                else -> throw MeshFormatException("Unsupported glTF index type.")
            }
        }
        return out
    }

    private fun componentsOf(type: String): Int = when (type) {
        "SCALAR" -> 1
        "VEC2" -> 2
        "VEC3" -> 3
        "VEC4" -> 4
        "MAT2" -> 4
        "MAT3" -> 9
        "MAT4" -> 16
        else -> throw MeshFormatException("Unsupported glTF accessor type: $type")
    }

    private fun componentSize(componentType: Int): Int = when (componentType) {
        5120, 5121 -> 1
        5122, 5123 -> 2
        5125, 5126 -> 4
        else -> throw MeshFormatException("Unsupported glTF component type: $componentType")
    }

    private fun normalizeNormals(normals: FloatArray) {
        var i = 0
        while (i < normals.size) {
            val x = normals[i]
            val y = normals[i + 1]
            val z = normals[i + 2]
            val len = sqrt(x * x + y * y + z * z)
            if (len > 1e-10f) {
                normals[i] = x / len; normals[i + 1] = y / len; normals[i + 2] = z / len
            } else {
                normals[i] = 0f; normals[i + 1] = 1f; normals[i + 2] = 0f
            }
            i += 3
        }
    }

    private fun materialColor(materials: JSONArray?, index: Int?): FloatArray {
        if (materials == null || index == null) return floatArrayOf(1f, 1f, 1f, 1f)
        val material = materials.optJSONObject(index) ?: return floatArrayOf(1f, 1f, 1f, 1f)
        val pbr = material.optJSONObject("pbrMetallicRoughness")
        val factor = pbr?.optJSONArray("baseColorFactor") ?: return floatArrayOf(1f, 1f, 1f, 1f)
        return floatArrayOf(
            factor.optDouble(0, 1.0).toFloat(),
            factor.optDouble(1, 1.0).toFloat(),
            factor.optDouble(2, 1.0).toFloat(),
            factor.optDouble(3, 1.0).toFloat(),
        )
    }

    private fun nodeTransform(node: JSONObject): Mat4 {
        val matrix = node.optJSONArray("matrix")
        if (matrix != null && matrix.length() >= 16) {
            val values = FloatArray(16) { matrix.optDouble(it, if (it % 5 == 0) 1.0 else 0.0).toFloat() }
            return Mat4(values)
        }
        val t = node.optJSONArray("translation")
        val r = node.optJSONArray("rotation")
        val s = node.optJSONArray("scale")
        return Mat4.trs(
            tx = t?.optDouble(0, 0.0)?.toFloat() ?: 0f,
            ty = t?.optDouble(1, 0.0)?.toFloat() ?: 0f,
            tz = t?.optDouble(2, 0.0)?.toFloat() ?: 0f,
            qx = r?.optDouble(0, 0.0)?.toFloat() ?: 0f,
            qy = r?.optDouble(1, 0.0)?.toFloat() ?: 0f,
            qz = r?.optDouble(2, 0.0)?.toFloat() ?: 0f,
            qw = r?.optDouble(3, 1.0)?.toFloat() ?: 1f,
            sx = s?.optDouble(0, 1.0)?.toFloat() ?: 1f,
            sy = s?.optDouble(1, 1.0)?.toFloat() ?: 1f,
            sz = s?.optDouble(2, 1.0)?.toFloat() ?: 1f,
        )
    }

    fun writeBinary(mesh: Mesh, file: File, progress: (Float) -> Unit = {}) {
        val payload = buildPayload(mesh, progress)
        val json = payload.json.toString().toByteArray(StandardCharsets.UTF_8)
        val jsonPadded = pad(json, 0x20, 12)
        val binPadded = pad(payload.binary.toByteArray(), 0x00, 8)
        val totalLength = 12 + 8 + jsonPadded.size + 8 + binPadded.size
        val output = ByteList(totalLength)
        output.writeU32LE(0x46546C67)
        output.writeU32LE(2)
        output.writeU32LE(totalLength)
        output.writeU32LE(jsonPadded.size)
        output.writeU32LE(0x4E4F534A)
        output.writeBytes(jsonPadded)
        output.writeU32LE(binPadded.size)
        output.writeU32LE(0x004E4942)
        output.writeBytes(binPadded)
        file.writeBytes(output.toByteArray())
        progress(1f)
    }

    fun writeJson(mesh: Mesh, file: File, progress: (Float) -> Unit = {}) {
        val payload = buildPayload(mesh, progress)
        val encoded = Base64.getEncoder().encodeToString(payload.binary.toByteArray())
        payload.json.getJSONArray("buffers").getJSONObject(0)
            .put("uri", "data:application/octet-stream;base64,$encoded")
        file.writeText(payload.json.toString(), Charsets.UTF_8)
        progress(1f)
    }

    private data class Payload(val json: JSONObject, val binary: ByteList)

    private fun buildPayload(mesh: Mesh, progress: (Float) -> Unit): Payload {
        mesh.sanitized()
        val vertexCount = mesh.vertexCount
        val triangleCount = mesh.triangleCount
        val normals = mesh.normals ?: mesh.computeNormals()
        val useShortIndices = vertexCount <= 65535
        val indexPathSize = if (useShortIndices) 2 else 4
        val binary = ByteList(vertexCount * 28 + triangleCount * 3 * indexPathSize + 64)

        val positionsOffset = 0
        for (v in 0 until vertexCount) {
            binary.writeFloatLE(mesh.positions[v * 3])
            binary.writeFloatLE(mesh.positions[v * 3 + 1])
            binary.writeFloatLE(mesh.positions[v * 3 + 2])
        }
        progress(0.3f)
        val normalsOffset = binary.size
        for (v in 0 until vertexCount) {
            binary.writeFloatLE(normals[v * 3])
            binary.writeFloatLE(normals[v * 3 + 1])
            binary.writeFloatLE(normals[v * 3 + 2])
        }
        progress(0.6f)
        var colorsOffset = -1
        val colors = mesh.colors
        if (colors != null) {
            while (binary.size % 4 != 0) binary.writeByte(0)
            colorsOffset = binary.size
            for (v in 0 until vertexCount) {
                binary.writeByte((colors[v * 4] * 255f).roundToInt().coerceIn(0, 255))
                binary.writeByte((colors[v * 4 + 1] * 255f).roundToInt().coerceIn(0, 255))
                binary.writeByte((colors[v * 4 + 2] * 255f).roundToInt().coerceIn(0, 255))
                binary.writeByte((colors[v * 4 + 3] * 255f).roundToInt().coerceIn(0, 255))
            }
        }
        while (binary.size % 4 != 0) binary.writeByte(0)
        val indicesOffset = binary.size
        var i = 0
        while (i < mesh.indices.size) {
            if (useShortIndices) {
                binary.writeU16LE(mesh.indices[i])
                binary.writeU16LE(mesh.indices[i + 1])
                binary.writeU16LE(mesh.indices[i + 2])
            } else {
                binary.writeU32LE(mesh.indices[i])
                binary.writeU32LE(mesh.indices[i + 1])
                binary.writeU32LE(mesh.indices[i + 2])
            }
            i += 3
        }
        while (binary.size % 4 != 0) binary.writeByte(0)
        progress(0.8f)

        val bounds = mesh.bounds()
        val json = JSONObject().apply {
            put("asset", JSONObject().put("version", "2.0").put("generator", "Universal Converter"))
            put("scene", 0)
            put("scenes", JSONArray().put(JSONObject().put("nodes", JSONArray().put(0))))
            put("nodes", JSONArray().put(JSONObject().put("mesh", 0).put("name", sanitizeMeshName(mesh.name))))
            val attributes = JSONObject()
                .put("POSITION", 0)
                .put("NORMAL", 1)
            val accessors = JSONArray().apply {
                put(JSONObject().apply {
                    put("bufferView", 0)
                    put("componentType", 5126)
                    put("count", vertexCount)
                    put("type", "VEC3")
                    put("min", JSONArray().put(bounds.minX.toDouble()).put(bounds.minY.toDouble()).put(bounds.minZ.toDouble()))
                    put("max", JSONArray().put(bounds.maxX.toDouble()).put(bounds.maxY.toDouble()).put(bounds.maxZ.toDouble()))
                })
                put(JSONObject().put("bufferView", 1).put("componentType", 5126).put("count", vertexCount).put("type", "VEC3"))
            }
            val views = JSONArray().apply {
                put(JSONObject().put("buffer", 0).put("byteOffset", positionsOffset).put("byteLength", vertexCount * 12).put("target", 34962))
                put(JSONObject().put("buffer", 0).put("byteOffset", normalsOffset).put("byteLength", vertexCount * 12).put("target", 34962))
            }
            if (colors != null) {
                attributes.put("COLOR_0", 2)
                accessors.put(JSONObject().apply {
                    put("bufferView", 2)
                    put("componentType", 5121)
                    put("normalized", true)
                    put("count", vertexCount)
                    put("type", "VEC4")
                })
                views.put(JSONObject().put("buffer", 0).put("byteOffset", colorsOffset).put("byteLength", vertexCount * 4).put("target", 34962))
            }
            val indicesAccessorIndex = accessors.length()
            accessors.put(JSONObject().apply {
                put("bufferView", views.length())
                put("componentType", if (useShortIndices) 5123 else 5125)
                put("count", mesh.indices.size)
                put("type", "SCALAR")
                put("min", JSONArray().put(0))
                put("max", JSONArray().put(vertexCount - 1))
            })
            views.put(
                JSONObject().put("buffer", 0).put("byteOffset", indicesOffset)
                    .put("byteLength", mesh.indices.size * indexPathSize).put("target", 34963)
            )
            val primitive = JSONObject().apply {
                put("attributes", attributes)
                put("indices", indicesAccessorIndex)
                put("material", 0)
                put("mode", 4)
            }
            put("meshes", JSONArray().put(JSONObject().put("name", sanitizeMeshName(mesh.name)).put("primitives", JSONArray().put(primitive))))
            put("materials", JSONArray().put(JSONObject().apply {
                put("name", "Surface")
                put("pbrMetallicRoughness", JSONObject().apply {
                    put("baseColorFactor", JSONArray().put(1).put(1).put(1).put(1))
                    put("metallicFactor", 0)
                    put("roughnessFactor", 0.72)
                })
            }))
            put("buffers", JSONArray().put(JSONObject().put("byteLength", binary.size)))
            put("bufferViews", views)
            put("accessors", accessors)
        }
        return Payload(json, binary)
    }

    private fun pad(bytes: ByteArray, value: Int, alignment: Int): ByteArray {
        val remainder = bytes.size % alignment
        if (remainder == 0) return bytes
        val padding = alignment - remainder
        val out = ByteArray(bytes.size + padding) { (value and 0xFF).toByte() }
        bytes.copyInto(out)
        return out
    }
}