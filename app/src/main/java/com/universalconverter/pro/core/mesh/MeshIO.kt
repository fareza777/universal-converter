package com.universalconverter.pro.core.mesh

import java.io.File

class MeshFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class MeshLoadResult(
    val mesh: Mesh,
    val warnings: List<String> = emptyList(),
)

data class MeshWriteOptions(
    val binary: Boolean = true,
)

object MeshIO {

    val readableIds = setOf("obj", "stl", "ply", "off", "glb", "gltf")
    val writableIds = readableIds

    fun read(file: File, formatId: String, progress: (Float) -> Unit = {}): MeshLoadResult {
        return when (formatId.lowercase()) {
            "obj" -> ObjCodec.read(file, progress)
            "stl" -> StlCodec.read(file, progress)
            "ply" -> PlyCodec.read(file, progress)
            "off" -> OffCodec.read(file, progress)
            "glb", "gltf" -> GltfCodec.read(file, progress)
            else -> throw MeshFormatException("$formatId is not a supported 3D format.")
        }
    }

    fun write(
        mesh: Mesh,
        file: File,
        formatId: String,
        options: MeshWriteOptions = MeshWriteOptions(),
        progress: (Float) -> Unit = {},
    ) {
        when (formatId.lowercase()) {
            "obj" -> ObjCodec.write(mesh, file, progress)
            "stl" -> StlCodec.write(mesh, file, options.binary, progress)
            "ply" -> PlyCodec.write(mesh, file, options.binary, progress)
            "off" -> OffCodec.write(mesh, file, progress)
            "glb" -> GltfCodec.writeBinary(mesh, file, progress)
            "gltf" -> GltfCodec.writeJson(mesh, file, progress)
            else -> throw MeshFormatException("$formatId is not a supported 3D output.")
        }
    }
}

internal fun sanitizeMeshName(name: String): String {
    val cleaned = name.replace('\n', ' ').replace('\r', ' ').trim()
    return cleaned.ifEmpty { "model" }.take(120)
}

internal fun computeProgress(bytes: Long, total: Long): Float {
    if (total <= 0L) return 0f
    return (bytes.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
}