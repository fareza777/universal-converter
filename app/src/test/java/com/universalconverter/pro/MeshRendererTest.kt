package com.universalconverter.pro

import com.universalconverter.pro.core.mesh.Mesh
import com.universalconverter.pro.core.mesh.MeshRenderOptions
import com.universalconverter.pro.core.mesh.MeshRenderer
import com.universalconverter.pro.core.mesh.MeshTurntable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MeshRendererTest {

    private fun cube(): Mesh {
        val positions = floatArrayOf(
            -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f,
            -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
        )
        val indices = intArrayOf(
            4, 5, 6, 4, 6, 7, 1, 0, 3, 1, 3, 2, 0, 4, 7, 0, 7, 3,
            5, 1, 2, 5, 2, 6, 7, 6, 2, 7, 2, 3, 0, 1, 5, 0, 5, 4,
        )
        return Mesh(positions, indices, name = "cube")
    }

    @Test
    fun rendersSilhouetteAndWritesTurntable() {
        val size = 240
        val pixels = MeshRenderer.render(cube(), MeshRenderOptions(size, size))
        assertEquals(size * size, pixels.size)
        val center = pixels[size / 2 * size + size / 2]
        val corner = pixels[2 * size + 2]
        assertTrue("Cube should cover the middle of the frame", center != corner)
        assertEquals(0xFF, center ushr 24)
        // Write a PPM so the render can be inspected outside the JVM test.
        val dir = File("build/tmp/render-test").also { it.mkdirs() }
        File(dir, "cube.ppm").outputStream().buffered().use { out ->
            out.write("P6\n$size $size\n255\n".toByteArray())
            for (pixel in pixels) {
                out.write((pixel shr 16) and 0xFF)
                out.write((pixel shr 8) and 0xFF)
                out.write(pixel and 0xFF)
            }
        }
        val gif = File(dir, "cube-turntable.gif")
        MeshTurntable.exportGif(cube(), gif, frames = 8, size = 160, fps = 12)
        assertTrue(gif.length() > 1000)
        val header = gif.inputStream().use { input -> ByteArray(6).also { input.read(it) } }
        assertEquals("GIF89a", String(header, Charsets.US_ASCII))
    }
}