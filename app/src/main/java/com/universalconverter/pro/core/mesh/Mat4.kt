package com.universalconverter.pro.core.mesh

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Column-major 4x4 matrix, matching glTF and OpenGL. */
class Mat4(val m: FloatArray = identityArray()) {

    companion object {
        fun identityArray() = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )

        fun identity() = Mat4()

        fun translation(x: Float, y: Float, z: Float) = Mat4(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                x, y, z, 1f,
            )
        )

        fun scale(x: Float, y: Float, z: Float) = Mat4(
            floatArrayOf(
                x, 0f, 0f, 0f,
                0f, y, 0f, 0f,
                0f, 0f, z, 0f,
                0f, 0f, 0f, 1f,
            )
        )

        fun rotationX(radians: Float): Mat4 {
            val c = cos(radians)
            val s = sin(radians)
            return Mat4(
                floatArrayOf(
                    1f, 0f, 0f, 0f,
                    0f, c, s, 0f,
                    0f, -s, c, 0f,
                    0f, 0f, 0f, 1f,
                )
            )
        }

        fun rotationY(radians: Float): Mat4 {
            val c = cos(radians)
            val s = sin(radians)
            return Mat4(
                floatArrayOf(
                    c, 0f, -s, 0f,
                    0f, 1f, 0f, 0f,
                    s, 0f, c, 0f,
                    0f, 0f, 0f, 1f,
                )
            )
        }

        fun rotationZ(radians: Float): Mat4 {
            val c = cos(radians)
            val s = sin(radians)
            return Mat4(
                floatArrayOf(
                    c, s, 0f, 0f,
                    -s, c, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f,
                )
            )
        }

        fun trs(
            tx: Float, ty: Float, tz: Float,
            qx: Float, qy: Float, qz: Float, qw: Float,
            sx: Float, sy: Float, sz: Float,
        ): Mat4 {
            val x2 = qx + qx
            val y2 = qy + qy
            val z2 = qz + qz
            val xx = qx * x2
            val xy = qx * y2
            val xz = qx * z2
            val yy = qy * y2
            val yz = qy * z2
            val zz = qz * z2
            val wx = qw * x2
            val wy = qw * y2
            val wz = qw * z2
            return Mat4(
                floatArrayOf(
                    (1f - (yy + zz)) * sx, (xy + wz) * sx, (xz - wy) * sx, 0f,
                    (xy - wz) * sy, (1f - (xx + zz)) * sy, (yz + wx) * sy, 0f,
                    (xz + wy) * sz, (yz - wx) * sz, (1f - (xx + yy)) * sz, 0f,
                    tx, ty, tz, 1f,
                )
            )
        }
    }

    operator fun times(other: Mat4): Mat4 {
        val a = m
        val b = other.m
        val r = FloatArray(16)
        for (col in 0..3) {
            val bc = col * 4
            for (row in 0..3) {
                r[bc + row] =
                    a[row] * b[bc] +
                        a[4 + row] * b[bc + 1] +
                        a[8 + row] * b[bc + 2] +
                        a[12 + row] * b[bc + 3]
            }
        }
        return Mat4(r)
    }

    fun transformPoint(x: Float, y: Float, z: Float, out: FloatArray) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12]
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13]
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14]
    }

    fun transformDirection(x: Float, y: Float, z: Float, out: FloatArray) {
        out[0] = m[0] * x + m[4] * y + m[8] * z
        out[1] = m[1] * x + m[5] * y + m[9] * z
        out[2] = m[2] * x + m[6] * y + m[10] * z
    }

    fun determinant3(): Float {
        return m[0] * (m[5] * m[10] - m[9] * m[6]) -
            m[4] * (m[1] * m[10] - m[9] * m[2]) +
            m[8] * (m[1] * m[6] - m[5] * m[2])
    }

    fun normalMatrix(): Mat4 {
        val a00 = m[0]
        val a01 = m[4]
        val a02 = m[8]
        val a10 = m[1]
        val a11 = m[5]
        val a12 = m[9]
        val a20 = m[2]
        val a21 = m[6]
        val a22 = m[10]
        val b01 = a22 * a11 - a12 * a21
        val b11 = -a22 * a10 + a12 * a20
        val b21 = a21 * a10 - a11 * a20
        var det = a00 * b01 + a01 * b11 + a02 * b21
        if (kotlin.math.abs(det) < 1e-20f) det = 1f
        val invDet = 1f / det
        val out = FloatArray(16)
        // inverse of the upper 3x3, then transpose (stored column-major)
        out[0] = b01 * invDet
        out[1] = (-a22 * a01 + a02 * a21) * invDet
        out[2] = (a12 * a01 - a02 * a11) * invDet
        out[3] = 0f
        out[4] = b11 * invDet
        out[5] = (a22 * a00 - a02 * a20) * invDet
        out[6] = (-a12 * a00 + a02 * a10) * invDet
        out[7] = 0f
        out[8] = b21 * invDet
        out[9] = (-a21 * a00 + a01 * a20) * invDet
        out[10] = (a11 * a00 - a01 * a10) * invDet
        out[11] = 0f
        out[12] = 0f
        out[13] = 0f
        out[14] = 0f
        out[15] = 1f
        // The block above is inverse; transpose it for the normal matrix.
        return Mat4(
            floatArrayOf(
                out[0], out[4], out[8], 0f,
                out[1], out[5], out[9], 0f,
                out[2], out[6], out[10], 0f,
                0f, 0f, 0f, 1f,
            )
        )
    }

    fun isMirrored(): Boolean = determinant3() < 0f

    fun isIdentity(): Boolean {
        val identity = identityArray()
        for (i in 0 until 16) {
            if (kotlin.math.abs(m[i] - identity[i]) > 1e-7f) return false
        }
        return true
    }
}

internal fun normalize3(x: Float, y: Float, z: Float): FloatArray {
    var len = sqrt(x * x + y * y + z * z)
    if (len < 1e-20f) len = 1f
    return floatArrayOf(x / len, y / len, z / len)
}