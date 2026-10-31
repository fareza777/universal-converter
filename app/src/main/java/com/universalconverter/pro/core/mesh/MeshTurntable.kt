package com.universalconverter.pro.core.mesh

import com.universalconverter.pro.core.image.GifEncoder
import java.io.File

object MeshTurntable {

    fun exportGif(
        mesh: Mesh,
        file: File,
        frames: Int = 28,
        size: Int = 480,
        fps: Int = 12,
        colors: Int = 256,
        progress: (Float) -> Unit = {},
    ) {
        val frameCount = frames.coerceIn(8, 120)
        val width = size.coerceIn(160, 720)
        val delay = (100f / fps.coerceIn(6, 30)).toInt().coerceIn(2, 100)
        GifEncoder(file, width, width, loopForever = true).use { encoder ->
            for (frame in 0 until frameCount) {
                val angle = frame.toFloat() / frameCount * 360f
                val pixels = MeshRenderer.render(
                    mesh,
                    MeshRenderOptions(width = width, height = width, azimuthDegrees = angle, elevationDegrees = 18f, supersample = 1),
                )
                encoder.addFrame(pixels, delay, colors, dither = true)
                progress((frame + 1).toFloat() / frameCount)
            }
        }
    }

    fun renderStill(mesh: Mesh, size: Int = 768, azimuth: Float = 34f, elevation: Float = 18f): IntArray {
        return MeshRenderer.render(
            mesh,
            MeshRenderOptions(width = size, height = size, azimuthDegrees = azimuth, elevationDegrees = elevation),
        )
    }
}