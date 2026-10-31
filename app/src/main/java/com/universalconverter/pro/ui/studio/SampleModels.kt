package com.universalconverter.pro.ui.studio

import com.universalconverter.pro.core.mesh.Mesh
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object SampleModels {

    fun torus(majorSegments: Int = 96, minorSegments: Int = 32, majorRadius: Float = 0.34f, minorRadius: Float = 0.125f): Mesh {
        val vertices = majorSegments * minorSegments
        val positions = FloatArray(vertices * 3)
        val normals = FloatArray(vertices * 3)
        for (i in 0 until majorSegments) {
            val u = (2.0 * PI * i / majorSegments).toFloat()
            val cosU = cos(u)
            val sinU = sin(u)
            for (j in 0 until minorSegments) {
                val v = (2.0 * PI * j / minorSegments).toFloat()
                val cosV = cos(v)
                val sinV = sin(v)
                val index = (i * minorSegments + j) * 3
                positions[index] = (majorRadius + minorRadius * cosV) * cosU
                positions[index + 1] = minorRadius * sinV
                positions[index + 2] = (majorRadius + minorRadius * cosV) * sinU
                normals[index] = cosV * cosU
                normals[index + 1] = sinV
                normals[index + 2] = cosV * sinU
            }
        }
        val indices = IntArray(majorSegments * minorSegments * 6)
        var pointer = 0
        for (i in 0 until majorSegments) {
            val nextI = (i + 1) % majorSegments
            for (j in 0 until minorSegments) {
                val nextJ = (j + 1) % minorSegments
                val a = i * minorSegments + j
                val b = i * minorSegments + nextJ
                val c = nextI * minorSegments + nextJ
                val d = nextI * minorSegments + j
                indices[pointer++] = a; indices[pointer++] = b; indices[pointer++] = c
                indices[pointer++] = a; indices[pointer++] = c; indices[pointer++] = d
            }
        }
        return Mesh(positions, indices, normals = normals, name = "Torus study")
    }
}