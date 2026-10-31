package com.universalconverter.pro

import com.universalconverter.pro.core.mesh.Mesh
import com.universalconverter.pro.core.mesh.MeshAnalyzer
import com.universalconverter.pro.core.mesh.MeshIO
import com.universalconverter.pro.core.mesh.MeshWriteOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class MeshCodecTest {

    private fun cube(): Mesh {
        val positions = floatArrayOf(
            -0.5f, -0.5f, -0.5f,
            0.5f, -0.5f, -0.5f,
            0.5f, 0.5f, -0.5f,
            -0.5f, 0.5f, -0.5f,
            -0.5f, -0.5f, 0.5f,
            0.5f, -0.5f, 0.5f,
            0.5f, 0.5f, 0.5f,
            -0.5f, 0.5f, 0.5f,
        )
        val indices = intArrayOf(
            4, 5, 6, 4, 6, 7,
            1, 0, 3, 1, 3, 2,
            0, 4, 7, 0, 7, 3,
            5, 1, 2, 5, 2, 6,
            7, 6, 2, 7, 2, 3,
            0, 1, 5, 0, 5, 4,
        )
        return Mesh(positions, indices, colors = null, name = "cube")
    }

    private fun tempFile(suffix: String): File {
        val file = File.createTempFile("uc-test-", ".$suffix")
        file.deleteOnExit()
        return file
    }

    @Test
    fun cubeIsWatertightAndPrintReady() {
        val health = MeshAnalyzer.analyze(cube())
        assertTrue(health.topologyChecked)
        assertTrue("Boundary edges: ${health.boundaryEdges}", health.watertight)
        assertTrue("Non-manifold: ${health.nonManifoldEdges}", health.manifold)
        assertEquals(0, health.degenerateFaces)
        assertEquals(1, health.shellCount)
        assertEquals(1.0, health.volume, 0.001)
        assertTrue(health.printReady)
    }

    @Test
    fun openCubeIsNotPrintReady() {
        val cube = cube()
        val open = Mesh(cube.positions, cube.indices.copyOf(cube.indices.size - 6), name = "open")
        val health = MeshAnalyzer.analyze(open)
        assertTrue(health.boundaryEdges > 0)
        assertTrue(!health.watertight)
        assertTrue(!health.printReady)
    }

    @Test
    fun everyMeshFormatRoundTrips() {
        val formats = listOf("obj", "stl", "ply", "off", "glb", "gltf")
        for (format in formats) {
            for (binary in listOf(true, false)) {
                if (format !in setOf("stl", "ply") && !binary) continue
                val target = tempFile(if (format == "tgz") "tgz" else format)
                MeshIO.write(cube(), target, format, MeshWriteOptions(binary = binary))
                val loaded = MeshIO.read(target, format)
                assertTrue("$format should have triangles", loaded.mesh.triangleCount >= 12)
                val bounds = loaded.mesh.bounds()
                assertEquals("$format minX", -0.5f, bounds.minX, 0.001f)
                assertEquals("$format maxY", 0.5f, bounds.maxY, 0.001f)
                assertNotNull("$format normals", loaded.mesh.normals)
                val health = MeshAnalyzer.analyze(loaded.mesh)
                assertTrue("$format should stay watertight (boundary=${health.boundaryEdges})", health.watertight)
            }
        }
    }

    @Test
    fun glbEmbedsGeometryAndMaterialColor() {
        val colored = cube().also { cube ->
            cube.colors = FloatArray(cube.vertexCount * 4) { i ->
                if (i % 4 == 3) 1f else if (i % 4 == 0) 0.9f else 0.2f
            }
        }
        val file = tempFile("glb")
        MeshIO.write(colored, file, "glb")
        val loaded = MeshIO.read(file, "glb").mesh
        assertNotNull(loaded.colors)
        assertTrue(Math.abs(loaded.colors!![0] - 0.9f) < 0.01f)
    }
}