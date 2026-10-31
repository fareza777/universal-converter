package com.universalconverter.pro.core.mesh

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Software clay renderer used for fast thumbnails, previews and offline turntable GIFs.
 * It deliberately avoids Android graphics so it can run in plain JVM tests.
 */
data class MeshRenderOptions(
    val width: Int = 480,
    val height: Int = 480,
    val azimuthDegrees: Float = 34f,
    val elevationDegrees: Float = 18f,
    val supersample: Int = 2,
    val showShadow: Boolean = true,
)

object MeshRenderer {

    private const val STRIDE = 9
    private const val NEAR = 0.03f
    private const val FOV_DEGREES = 32f

    fun render(mesh: Mesh, options: MeshRenderOptions): IntArray {
        require(options.width > 0 && options.height > 0)
        val ss = options.supersample.coerceIn(1, 3)
        val w = options.width * ss
        val h = options.height * ss
        val normals = mesh.normals ?: mesh.computeNormals()
        val bounds = mesh.bounds()
        val radius = max(bounds.diagonal * 0.5f, 1e-4f)
        val centerX = bounds.centerX
        val centerY = bounds.centerY
        val centerZ = bounds.centerZ
        val fov = Math.toRadians(FOV_DEGREES.toDouble()).toFloat()
        val distance = radius / sin(fov * 0.5f) * 1.28f
        val rotation = Mat4.rotationX(Math.toRadians(options.elevationDegrees.toDouble()).toFloat()) *
            Mat4.rotationY(Math.toRadians(options.azimuthDegrees.toDouble()).toFloat())
        val normalMatrix = rotation.normalMatrix()

        val color = IntArray(w * h)
        background(color, w, h, options.showShadow)
        val depth = FloatArray(w * h) { 0f }
        val aspect = w.toFloat() / h.toFloat()
        val f = 1f / tan(fov * 0.5f)
        val light = normalize3(-0.5f, 0.75f, 0.95f)
        val fill = normalize3(0.7f, -0.15f, 0.45f)
        val view = floatArrayOf(0f, 0f, 1f)
        val half = normalize3(light[0] + view[0], light[1] + view[1], light[2] + view[2])
        val clay = floatArrayOf(0.78f, 0.61f, 0.48f)
        val flatShading = mesh.triangleCount < 1500
        val rimColor = floatArrayOf(1.0f, 0.52f, 0.30f)

        val poly = FloatArray(4 * STRIDE)
        val source = FloatArray(3 * STRIDE)
        val screen = FloatArray(4 * 3)
        val positions = mesh.positions
        val indices = mesh.indices
        val uvs = mesh.uvs
        val colors = mesh.colors
        val temp = FloatArray(3)
        var triangle = 0
        while (triangle < indices.size) {
            var corner = 0
            while (corner < 3) {
                val vertex = indices[triangle + corner]
                val base = vertex * 3
                rotation.transformDirection(positions[base] - centerX, positions[base + 1] - centerY, positions[base + 2] - centerZ, temp)
                val p = corner * STRIDE
                source[p] = temp[0]
                source[p + 1] = temp[1]
                source[p + 2] = temp[2] - distance

                normalMatrix.transformDirection(normals[base], normals[base + 1], normals[base + 2], temp)
                val len = sqrt(temp[0] * temp[0] + temp[1] * temp[1] + temp[2] * temp[2])
                val inv = if (len > 1e-10f) 1f / len else 0f
                source[p + 3] = temp[0] * inv
                source[p + 4] = temp[1] * inv
                source[p + 5] = temp[2] * inv
                if (colors != null) {
                    source[p + 6] = colors[vertex * 4]
                    source[p + 7] = colors[vertex * 4 + 1]
                    source[p + 8] = colors[vertex * 4 + 2]
                } else {
                    source[p + 6] = clay[0]
                    source[p + 7] = clay[1]
                    source[p + 8] = clay[2]
                }
                corner++
            }
            if (flatShading) {
                val ax = source[0]; val ay = source[1]; val az = source[2]
                val bx = source[STRIDE]; val by = source[STRIDE + 1]; val bz = source[STRIDE + 2]
                val cx = source[STRIDE * 2]; val cy = source[STRIDE * 2 + 1]; val cz = source[STRIDE * 2 + 2]
                val ux = bx - ax; val uy = by - ay; val uz = bz - az
                val vx = cx - ax; val vy = cy - ay; val vz = cz - az
                val nx = uy * vz - uz * vy
                val ny = uz * vx - ux * vz
                val nz = ux * vy - uy * vx
                val n = normalize3(nx, ny, nz)
                for (c in 0..2) {
                    source[c * STRIDE + 3] = n[0]
                    source[c * STRIDE + 4] = n[1]
                    source[c * STRIDE + 5] = n[2]
                }
            }
            val clipped = clipNear(source, poly)
            if (clipped >= 3) {
                var v = 0
                while (v < clipped) {
                    val p = v * STRIDE
                    val invZ = -1f / poly[p + 2]
                    val sx = (poly[p] * f / aspect * invZ * 0.5f + 0.5f) * w
                    val sy = (0.5f - poly[p + 1] * f * invZ * 0.5f) * h
                    screen[v * 3] = sx
                    screen[v * 3 + 1] = sy
                    screen[v * 3 + 2] = invZ
                    v++
                }
                v = 1
                while (v < clipped - 1) {
                    rasterize(
                        poly, screen, 0, v, v + 1, color, depth, w, h,
                        light, fill, half, view, rimColor,
                    )
                    v++
                }
            }
            triangle += 3
        }

        if (ss == 1) return color
        return downsample(color, w, h, options.width, options.height, ss)
    }

    private fun clipNear(input: FloatArray, output: FloatArray): Int {
        var outCount = 0
        var previousIndex = 2
        for (currentIndex in 0..2) {
            val currentZ = input[currentIndex * STRIDE + 2]
            val previousZ = input[previousIndex * STRIDE + 2]
            val currentInside = currentZ <= -NEAR
            val previousInside = previousZ <= -NEAR
            if (currentInside != previousInside) {
                val t = (-NEAR - previousZ) / (currentZ - previousZ)
                interpolate(input, previousIndex, currentIndex, t, output, outCount++)
            }
            if (currentInside) {
                System.arraycopy(input, currentIndex * STRIDE, output, outCount * STRIDE, STRIDE)
                outCount++
            }
            previousIndex = currentIndex
        }
        return outCount.coerceAtMost(4)
    }

    private fun interpolate(input: FloatArray, a: Int, b: Int, t: Float, output: FloatArray, outIndex: Int) {
        val o = outIndex * STRIDE
        val pa = a * STRIDE
        val pb = b * STRIDE
        for (i in 0 until STRIDE) {
            output[o + i] = input[pa + i] + (input[pb + i] - input[pa + i]) * t
        }
    }

    private fun rasterize(
        poly: FloatArray,
        screen: FloatArray,
        ia: Int,
        ib: Int,
        ic: Int,
        color: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        light: FloatArray,
        fill: FloatArray,
        half: FloatArray,
        view: FloatArray,
        rimColor: FloatArray,
    ) {
        val ax = screen[ia * 3]
        val ay = screen[ia * 3 + 1]
        val bx = screen[ib * 3]
        val by = screen[ib * 3 + 1]
        val cx = screen[ic * 3]
        val cy = screen[ic * 3 + 1]
        val denom = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if (kotlin.math.abs(denom) < 1e-9f) return
        val minX = max(0, kotlin.math.floor(min(ax, min(bx, cx))).toInt())
        val maxX = min(width - 1, kotlin.math.ceil(max(ax, max(bx, cx))).toInt())
        val minY = max(0, kotlin.math.floor(min(ay, min(by, cy))).toInt())
        val maxY = min(height - 1, kotlin.math.ceil(max(ay, max(by, cy))).toInt())
        if (minX > maxX || minY > maxY) return
        val iza = screen[ia * 3 + 2]
        val izb = screen[ib * 3 + 2]
        val izc = screen[ic * 3 + 2]
        val aBase = ia * STRIDE
        val bBase = ib * STRIDE
        val cBase = ic * STRIDE
        val anx = poly[aBase + 3]
        val any = poly[aBase + 4]
        val anz = poly[aBase + 5]
        val bnx = poly[bBase + 3]
        val bny = poly[bBase + 4]
        val bnz = poly[bBase + 5]
        val cnx = poly[cBase + 3]
        val cny = poly[cBase + 4]
        val cnz = poly[cBase + 5]
        val ar = poly[aBase + 6]
        val ag = poly[aBase + 7]
        val ab = poly[aBase + 8]
        val br = poly[bBase + 6]
        val bg = poly[bBase + 7]
        val bb = poly[bBase + 8]
        val cr = poly[cBase + 6]
        val cg = poly[cBase + 7]
        val cb = poly[cBase + 8]
        val invDenom = 1f / denom
        var y = minY
        while (y <= maxY) {
            val py = y + 0.5f
            var x = minX
            while (x <= maxX) {
                val px = x + 0.5f
                var w0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) * invDenom
                var w1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) * invDenom
                var w2 = 1f - w0 - w1
                if (w0 >= -0.0001f && w1 >= -0.0001f && w2 >= -0.0001f) {
                    if (w0 < 0f) w0 = 0f
                    if (w1 < 0f) w1 = 0f
                    if (w2 < 0f) w2 = 0f
                    val z = w0 * iza + w1 * izb + w2 * izc
                    val offset = y * width + x
                    if (z > depth[offset]) {
                        val pw0 = w0 * iza
                        val pw1 = w1 * izb
                        val pw2 = w2 * izc
                        val sum = pw0 + pw1 + pw2
                        if (sum > 0f) {
                            val inv = 1f / sum
                            var nx = (anx * pw0 + bnx * pw1 + cnx * pw2) * inv
                            var ny = (any * pw0 + bny * pw1 + cny * pw2) * inv
                            var nz = (anz * pw0 + bnz * pw1 + cnz * pw2) * inv
                            val nlen = sqrt(nx * nx + ny * ny + nz * nz)
                            if (nlen > 1e-10f) {
                                nx /= nlen; ny /= nlen; nz /= nlen
                            } else {
                                nx = 0f; ny = 1f; nz = 0f
                            }
                            // Two-sided clay: light backfaces as if their normals face us.
                            if (nx * view[0] + ny * view[1] + nz * view[2] < 0f) {
                                nx = -nx; ny = -ny; nz = -nz
                            }
                            val diffuse = max(0f, nx * light[0] + ny * light[1] + nz * light[2])
                            val fillLight = max(0f, nx * fill[0] + ny * fill[1] + nz * fill[2])
                            val facing = max(0f, nx * view[0] + ny * view[1] + nz * view[2])
                            val rim = (1f - facing).pow(3f) * 0.5f
                            val specular = max(0f, nx * half[0] + ny * half[1] + nz * half[2]).pow(28f) * 0.3f
                            val ambient = 0.26f + (ny * 0.5f + 0.5f) * 0.06f
                            val baseR = (ar * pw0 + br * pw1 + cr * pw2) * inv
                            val baseG = (ag * pw0 + bg * pw1 + cg * pw2) * inv
                            val baseB = (ab * pw0 + bb * pw1 + cb * pw2) * inv
                            val lightAmount = ambient + diffuse * 0.82f + fillLight * 0.18f
                            var r = baseR * lightAmount + rimColor[0] * rim * 0.16f + specular
                            var g = baseG * lightAmount + rimColor[1] * rim * 0.16f + specular
                            var b = baseB * lightAmount + rimColor[2] * rim * 0.16f + specular
                            r = r.coerceIn(0f, 1f)
                            g = g.coerceIn(0f, 1f)
                            b = b.coerceIn(0f, 1f)
                            color[offset] = (0xFF shl 24) or
                                ((r * 255f + 0.5f).toInt() shl 16) or
                                ((g * 255f + 0.5f).toInt() shl 8) or
                                (b * 255f + 0.5f).toInt()
                            depth[offset] = z
                        }
                    }
                }
                x++
            }
            y++
        }
    }

    private fun background(pixels: IntArray, width: Int, height: Int, shadow: Boolean) {
        val topR = 31f
        val topG = 28f
        val topB = 24f
        val bottomR = 10f
        val bottomG = 9f
        val bottomB = 8f
        val glowX = width * 0.5f
        val glowY = height * 0.38f
        val glowRadius = max(width, height) * 0.62f
        val shadowCenterX = width * 0.5f
        val shadowCenterY = height * 0.735f
        val shadowRadiusX = width * 0.33f
        val shadowRadiusY = height * 0.075f
        var y = 0
        while (y < height) {
            val t = y.toFloat() / height
            val baseR = topR + (bottomR - topR) * t
            val baseG = topG + (bottomG - topG) * t
            val baseB = topB + (bottomB - topB) * t
            var x = 0
            while (x < width) {
                val dx = x - glowX
                val dy = (y - glowY) * 0.9f
                val glow = (1f - sqrt(dx * dx + dy * dy) / glowRadius).coerceIn(0f, 1f)
                val glowPow = glow * glow
                var r = baseR + glowPow * 27f
                var g = baseG + glowPow * 18f
                var b = baseB + glowPow * 9f
                val nx = (x.toFloat() / width - 0.5f) * 2f
                val ny = (y.toFloat() / height - 0.5f) * 2f
                val vignette = 1f - (nx * nx * 0.10f + ny * ny * 0.13f)
                r *= vignette
                g *= vignette
                b *= vignette
                if (shadow) {
                    val sdx = (x - shadowCenterX) / shadowRadiusX
                    val sdy = (y - shadowCenterY) / shadowRadiusY
                    val shadowValue = 1f - sqrt(sdx * sdx + sdy * sdy)
                    if (shadowValue > 0f) {
                        val alpha = (shadowValue * 0.52f).coerceAtMost(0.52f)
                        r *= 1f - alpha
                        g *= 1f - alpha
                        b *= 1f - alpha
                    }
                }
                pixels[y * width + x] = (0xFF shl 24) or
                    (r.toInt().coerceIn(0, 255) shl 16) or
                    (g.toInt().coerceIn(0, 255) shl 8) or
                    b.toInt().coerceIn(0, 255)
                x++
            }
            y++
        }
    }

    private fun downsample(input: IntArray, width: Int, height: Int, outWidth: Int, outHeight: Int, ss: Int): IntArray {
        val output = IntArray(outWidth * outHeight)
        val factor = ss * ss
        var y = 0
        while (y < outHeight) {
            var x = 0
            while (x < outWidth) {
                var r = 0
                var g = 0
                var b = 0
                var sy = 0
                while (sy < ss) {
                    var sx = 0
                    while (sx < ss) {
                        val pixel = input[(y * ss + sy) * width + (x * ss + sx)]
                        r += (pixel shr 16) and 0xFF
                        g += (pixel shr 8) and 0xFF
                        b += pixel and 0xFF
                        sx++
                    }
                    sy++
                }
                output[y * outWidth + x] = (0xFF shl 24) or
                    ((r / factor) shl 16) or
                    ((g / factor) shl 8) or
                    (b / factor)
                x++
            }
            y++
        }
        return output
    }
}