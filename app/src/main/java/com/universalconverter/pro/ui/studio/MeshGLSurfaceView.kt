package com.universalconverter.pro.ui.studio

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.universalconverter.pro.core.mesh.Mesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.tan
import kotlin.math.max
import kotlin.math.sin

enum class StudioRenderMode { CLAY, SURFACE, WIRE }

class MeshGLSurfaceView(context: Context) : GLSurfaceView(context) {

    private val renderer = StudioRenderer()
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            renderer.zoomBy(detector.scaleFactor)
            return true
        }
    })

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setScene(mesh: Mesh?, mode: StudioRenderMode, grid: Boolean, autoRotate: Boolean) {
        queueEvent {
            renderer.setScene(mesh, mode, grid, autoRotate)
        }
    }

    fun setMode(mode: StudioRenderMode) {
        queueEvent { renderer.setMode(mode) }
    }

    fun setGrid(visible: Boolean) {
        queueEvent { renderer.gridVisible = visible }
    }

    fun setAutoRotate(rotate: Boolean) {
        queueEvent { renderer.autoRotate = rotate }
    }

    fun resetCamera() {
        queueEvent { renderer.resetCamera() }
    }

    fun captureFrame(callback: (Bitmap?) -> Unit) {
        queueEvent {
            val width = this.width
            val height = this.height
            if (width <= 0 || height <= 0) {
                post { callback(null) }
                return@queueEvent
            }
            renderer.renderSingleFrame()
            val buffer = IntBuffer.allocate(width * height)
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val row = IntArray(width)
            for (y in 0 until height) {
                val source = (height - 1 - y) * width
                for (x in 0 until width) {
                    val pixel = buffer.get(source + x)
                    val r = pixel and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = (pixel shr 16) and 0xFF
                    val a = (pixel shr 24) and 0xFF
                    row[x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
                bitmap.setPixels(row, 0, width, 0, y, width, 1)
            }
            post { callback(bitmap) }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    renderer.rotate(dx, dy)
                }
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private var lastX = 0f
    private var lastY = 0f

    private class StudioRenderer : GLSurfaceView.Renderer {

        @Volatile private var mesh: Mesh? = null
        @Volatile private var mode = StudioRenderMode.CLAY
        @Volatile var gridVisible = true
        @Volatile var autoRotate = true
        @Volatile private var azimuth = 0.58f
        @Volatile private var elevation = 0.32f
        @Volatile private var zoom = 1f
        @Volatile private var centerX = 0f
        @Volatile private var centerY = 0f
        @Volatile private var centerZ = 0f
        @Volatile private var radius = 1f
        @Volatile private var floorY = -1f

        private var width = 0
        private var height = 0
        private var programMesh = 0
        private var programBg = 0
        private var programLine = 0
        private var programShadow = 0
        private var meshVaoReady = false
        private var positionVbo = 0
        private var normalVbo = 0
        private var colorVbo = 0
        private var indexIbo = 0
        private var indexCount = 0
        private var edgeIbo = 0
        private var edgeIndexCount = 0
        private var edgeIpboValid = false
        private var gridVbo = 0
        private var gridVertexCount = 0
        private var shadowVbo = 0
        private var bgVbo = 0
        private var lastTime = 0L
        private var hasColors = false

        private val projection = FloatArray(16)
        private val view = FloatArray(16)
        private val model = FloatArray(16)
        private val mvp = FloatArray(16)
        private val vp = FloatArray(16)
        private val floorMvp = FloatArray(16)
        private val camera = FloatArray(3)

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES30.glClearColor(0.08f, 0.073f, 0.063f, 1f)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthFunc(GLES30.GL_LEQUAL)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            programMesh = buildProgram(VERTEX_MESH, FRAGMENT_MESH)
            programBg = buildProgram(VERTEX_BG, FRAGMENT_BG)
            programLine = buildProgram(VERTEX_LINE, FRAGMENT_LINE)
            programShadow = buildProgram(VERTEX_SHADOW, FRAGMENT_SHADOW)
            createStaticBuffers()
            meshVaoReady = false
            uploadMesh()
            lastTime = System.nanoTime()
        }

        override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
            width = w
            height = h
            GLES30.glViewport(0, 0, w, h)
        }

        override fun onDrawFrame(gl: GL10?) {
            if (programMesh == 0 || programBg == 0) return
            if (width == 0 || height == 0) return
            val now = System.nanoTime()
            val delta = ((now - lastTime) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
            lastTime = now
            if (autoRotate) azimuth += delta * 0.35f
            renderFrame()
        }

        fun renderSingleFrame() {
            renderFrame()
        }

        private fun renderFrame() {
            GLES30.glViewport(0, 0, width, height)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            computeCamera()
            drawBackground()
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(false)
            if (gridVisible) {
                drawShadow()
                drawGrid()
            }
            GLES30.glDepthMask(true)
            drawMesh()
        }

        private fun computeCamera() {
            val aspect = width.toFloat() / height.toFloat()
            val fovDegrees = 35f
            Matrix.perspectiveM(projection, 0, fovDegrees, aspect, 0.05f, 30f)
            val distance = (StudioFraming.fitDistance(1f, fovDegrees, aspect) * zoom).coerceIn(1.2f, 30f)
            val cosE = cos(elevation.toDouble()).toFloat()
            val eyeX = sin(azimuth.toDouble()).toFloat() * cosE * distance
            val eyeY = sin(elevation.toDouble()).toFloat() * distance
            val eyeZ = cos(azimuth.toDouble()).toFloat() * cosE * distance
            camera[0] = eyeX; camera[1] = eyeY; camera[2] = eyeZ
            Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, 0f, 0f, 0f, 0f, 1f, 0f)
            Matrix.setIdentityM(model, 0)
            val scale = 1f / max(radius, 1e-5f)
            Matrix.scaleM(model, 0, scale, scale, scale)
            Matrix.translateM(model, 0, -centerX, -centerY, -centerZ)
            Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
            Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
            val floor = FloatArray(16)
            Matrix.setIdentityM(floor, 0)
            Matrix.translateM(floor, 0, 0f, floorY, 0f)
            Matrix.multiplyMM(floorMvp, 0, vp, 0, floor, 0)
        }

        private fun drawBackground() {
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(false)
            GLES30.glUseProgram(programBg)
            GLES30.glUniform2f(GLES30.glGetUniformLocation(programBg, "uResolution"), width.toFloat(), height.toFloat())
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bgVbo)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
            GLES30.glDisableVertexAttribArray(0)
            GLES30.glDepthMask(true)
        }

        private fun drawShadow() {
            GLES30.glUseProgram(programShadow)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(programShadow, "uMvp"), 1, false, floorMvp, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, shadowVbo)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 20, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 20, 12)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
            GLES30.glDisableVertexAttribArray(0)
            GLES30.glDisableVertexAttribArray(1)
        }

        private fun drawGrid() {
            GLES30.glUseProgram(programLine)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(programLine, "uMvp"), 1, false, floorMvp, 0)
            GLES30.glUniform4f(GLES30.glGetUniformLocation(programLine, "uColor"), 0.78f, 0.73f, 0.64f, 0.14f)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gridVbo)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
            GLES30.glDrawArrays(GLES30.GL_LINES, 0, gridVertexCount)
            GLES30.glDisableVertexAttribArray(0)
        }

        private fun drawMesh() {
            val mesh = mesh ?: return
            if (!meshVaoReady || indexCount == 0) return
            GLES30.glUseProgram(programMesh)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(programMesh, "uMvp"), 1, false, mvp, 0)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(programMesh, "uModel"), 1, false, model, 0)
            GLES30.glUniform3f(GLES30.glGetUniformLocation(programMesh, "uCamera"), camera[0], camera[1], camera[2])
            GLES30.glUniform3f(GLES30.glGetUniformLocation(programMesh, "uLight"), -0.42f, 0.78f, 0.74f)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(programMesh, "uUseColor"), if (hasColors && mode == StudioRenderMode.SURFACE) 1 else 0)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(programMesh, "uMode"), if (mode == StudioRenderMode.WIRE) 2 else 0)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(programMesh, "uFlat"), if (mesh.triangleCount < 1500) 1 else 0)
            GLES30.glUniform3f(GLES30.glGetUniformLocation(programMesh, "uClay"), 0.78f, 0.62f, 0.49f)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, positionVbo)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, normalVbo)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 0, 0)
            if (hasColors && colorVbo != 0) {
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, colorVbo)
                GLES30.glEnableVertexAttribArray(2)
                GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, 16, 0)
            } else {
                GLES30.glDisableVertexAttribArray(2)
            }
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexIbo)
            if (mode == StudioRenderMode.WIRE) {
                GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
                GLES30.glPolygonOffset(1.2f, 2f)
            }
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glDisableVertexAttribArray(0)
            GLES30.glDisableVertexAttribArray(1)
            GLES30.glDisableVertexAttribArray(2)
            if (mode == StudioRenderMode.WIRE && edgeIpboValid && edgeIndexCount > 0) {
                GLES30.glUseProgram(programLine)
                GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(programLine, "uMvp"), 1, false, mvp, 0)
                GLES30.glUniform4f(GLES30.glGetUniformLocation(programLine, "uColor"), 0.94f, 0.50f, 0.31f, 0.8f)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, positionVbo)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, edgeIbo)
                GLES30.glDrawElements(GLES30.GL_LINES, edgeIndexCount, GLES30.GL_UNSIGNED_INT, 0)
                GLES30.glDisableVertexAttribArray(0)
            }
            @Suppress("UNUSED_VARIABLE") val unused = mesh
        }

        fun setScene(newMesh: Mesh?, newMode: StudioRenderMode, grid: Boolean, auto: Boolean) {
            val changed = mesh !== newMesh
            mesh = newMesh
            mode = newMode
            gridVisible = grid
            autoRotate = auto
            if (changed) {
                updateBounds(newMesh)
                resetCamera()
                uploadMesh()
            }
        }

        fun setMode(newMode: StudioRenderMode) {
            mode = newMode
            if (newMode == StudioRenderMode.WIRE) ensureEdges()
        }

        private fun updateBounds(mesh: Mesh?) {
            if (mesh == null) {
                centerX = 0f; centerY = 0f; centerZ = 0f; radius = 1f
                return
            }
            val bounds = mesh.bounds()
            centerX = bounds.centerX
            centerY = bounds.centerY
            centerZ = bounds.centerZ
            radius = max(bounds.diagonal * 0.5f, 1e-4f)
            floorY = ((bounds.minY - centerY) / radius).coerceIn(-1.5f, 1.5f) - 0.012f
        }

        fun rotate(dx: Float, dy: Float) {
            azimuth += dx * 0.0065f
            elevation = (elevation + dy * 0.005f).coerceIn(0.06f, 1.25f)
        }

        fun zoomBy(factor: Float) {
            zoom = (zoom / factor).coerceIn(0.55f, 2.2f)
        }

        fun resetCamera() {
            azimuth = 0.58f
            elevation = 0.32f
            zoom = 1f
        }

        private fun uploadMesh() {
            releaseMeshBuffers()
            val mesh = mesh ?: return
            val positions = mesh.positions
            val normals = mesh.normals ?: mesh.computeNormals()
            val colors = mesh.colors
            hasColors = colors != null
            positionVbo = createFloatBuffer(positions)
            normalVbo = createFloatBuffer(normals)
            if (colors != null) colorVbo = createFloatBuffer(colors) else colorVbo = 0
            indexIbo = createIntBuffer(mesh.indices)
            indexCount = mesh.indices.size
            meshVaoReady = true
            edgeIpboValid = false
            edgeIbo = 0
            edgeIndexCount = 0
            if (mode == StudioRenderMode.WIRE) ensureEdges()
        }

        private fun ensureEdges() {
            if (edgeIpboValid || mesh == null) return
            val mesh = mesh!!
            if (mesh.triangleCount > 420_000) return
            val indices = mesh.indices
            val edges = IntArray(indices.size * 2)
            var pointer = 0
            var i = 0
            while (i < indices.size) {
                val a = indices[i]
                val b = indices[i + 1]
                val c = indices[i + 2]
                edges[pointer++] = a; edges[pointer++] = b
                edges[pointer++] = b; edges[pointer++] = c
                edges[pointer++] = c; edges[pointer++] = a
                i += 3
            }
            edgeIbo = createIntBuffer(edges)
            edgeIndexCount = edges.size
            edgeIpboValid = true
        }

        private fun createFloatBuffer(data: FloatArray): Int {
            val buffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(data)
            buffer.position(0)
            val ids = IntArray(1)
            GLES30.glGenBuffers(1, ids, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, buffer, GLES30.GL_STATIC_DRAW)
            return ids[0]
        }

        private fun createIntBuffer(data: IntArray): Int {
            val buffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(data)
            buffer.position(0)
            val ids = IntArray(1)
            GLES30.glGenBuffers(1, ids, 0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[0])
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, data.size * 4, buffer, GLES30.GL_STATIC_DRAW)
            return ids[0]
        }

        private fun releaseMeshBuffers() {
            if (positionVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(positionVbo), 0)
            if (normalVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(normalVbo), 0)
            if (colorVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(colorVbo), 0)
            if (indexIbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(indexIbo), 0)
            if (edgeIbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(edgeIbo), 0)
            positionVbo = 0; normalVbo = 0; colorVbo = 0; indexIbo = 0; edgeIbo = 0
            indexCount = 0; edgeIndexCount = 0; meshVaoReady = false; edgeIpboValid = false
        }

        private fun createStaticBuffers() {
            val bg = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
            bgVbo = createFloatBuffer(bg)

            val gridLines = ArrayList<Float>(1024)
            val extent = 4f
            val step = 0.25f
            var v = -extent
            while (v <= extent + 0.001f) {
                gridLines += -extent; gridLines += 0f; gridLines += v
                gridLines += extent; gridLines += 0f; gridLines += v
                gridLines += v; gridLines += 0f; gridLines += -extent
                gridLines += v; gridLines += 0f; gridLines += extent
                v += step
            }
            gridVbo = createFloatBuffer(gridLines.toFloatArray())
            gridVertexCount = gridLines.size / 3

            val shadow = floatArrayOf(
                -1.5f, 0f, -1.5f, 0f, 0f,
                1.5f, 0f, -1.5f, 1f, 0f,
                1.5f, 0f, 1.5f, 1f, 1f,
                -1.5f, 0f, -1.5f, 0f, 0f,
                1.5f, 0f, 1.5f, 1f, 1f,
                -1.5f, 0f, 1.5f, 0f, 1f,
            )
            shadowVbo = createFloatBuffer(shadow)
        }

        private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
            val vertex = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
            val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
            if (vertex == 0 || fragment == 0) return 0
            val program = GLES30.glCreateProgram()
            GLES30.glAttachShader(program, vertex)
            GLES30.glAttachShader(program, fragment)
            GLES30.glLinkProgram(program)
            val status = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e("MeshStudio", "Program link failed: ${GLES30.glGetProgramInfoLog(program)}")
                GLES30.glDeleteProgram(program)
                return 0
            }
            GLES30.glDeleteShader(vertex)
            GLES30.glDeleteShader(fragment)
            return program
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val status = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e("MeshStudio", "Shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}")
                GLES30.glDeleteShader(shader)
                return 0
            }
            return shader
        }

        companion object {
            private const val VERTEX_BG = """#version 300 es
            layout(location=0) in vec2 aPos;
            out vec2 vUv;
            void main() {
                vUv = aPos * 0.5 + 0.5;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
            """

            private const val FRAGMENT_BG = """#version 300 es
            precision highp float;
            uniform vec2 uResolution;
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                vec3 bottom = vec3(0.052, 0.047, 0.041);
                vec3 top = vec3(0.13, 0.115, 0.10);
                vec3 color = mix(bottom, top, smoothstep(0.0, 1.0, vUv.y));
                float aspect = uResolution.x / uResolution.y;
                vec2 p = vec2(vUv.x * aspect, vUv.y);
                float d = distance(p, vec2(0.5 * aspect, 0.63));
                float glow = smoothstep(0.8, 0.0, d);
                color += vec3(0.24, 0.13, 0.07) * glow * 0.35;
                float vignette = 1.0 - 0.35 * length(vUv - 0.5);
                fragColor = vec4(color * vignette, 1.0);
            }
            """

            private const val VERTEX_LINE = """#version 300 es
            layout(location=0) in vec3 aPos;
            uniform mat4 uMvp;
            void main() { gl_Position = uMvp * vec4(aPos, 1.0); }
            """

            private const val FRAGMENT_LINE = """#version 300 es
            precision mediump float;
            uniform vec4 uColor;
            out vec4 fragColor;
            void main() { fragColor = uColor; }
            """

            private const val VERTEX_SHADOW = """#version 300 es
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aUv;
            uniform mat4 uMvp;
            out vec2 vUv;
            void main() {
                vUv = aUv;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
            """

            private const val FRAGMENT_SHADOW = """#version 300 es
            precision mediump float;
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                float d = length(vUv * 2.0 - 1.0);
                float alpha = smoothstep(1.0, 0.05, d) * 0.62;
                fragColor = vec4(0.01, 0.008, 0.006, alpha);
            }
            """

            private const val VERTEX_MESH = """#version 300 es
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec3 aNormal;
            layout(location=2) in vec3 aColor;
            uniform mat4 uMvp;
            uniform mat4 uModel;
            out vec3 vWorld;
            out vec3 vNormal;
            out vec3 vColor;
            void main() {
                vec4 world = uModel * vec4(aPos, 1.0);
                vWorld = world.xyz;
                vNormal = mat3(uModel) * aNormal;
                vColor = aColor;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
            """

            private const val FRAGMENT_MESH = """#version 300 es
            precision highp float;
            uniform vec3 uCamera;
            uniform vec3 uLight;
            uniform vec3 uClay;
            uniform int uUseColor;
            uniform int uMode;
            uniform int uFlat;
            in vec3 vWorld;
            in vec3 vNormal;
            in vec3 vColor;
            out vec4 fragColor;
            void main() {
                vec3 N = uFlat == 1 ? normalize(cross(dFdx(vWorld), dFdy(vWorld))) : normalize(vNormal);
                vec3 V = normalize(uCamera - vWorld);
                if (dot(N, V) < 0.0) N = -N;
                float diffuse = max(dot(N, uLight), 0.0);
                vec3 fillDir = normalize(vec3(0.65, -0.1, 0.45));
                float fill = max(dot(N, fillDir), 0.0) * 0.22;
                float facing = max(dot(N, V), 0.0);
                float rim = pow(1.0 - facing, 3.0) * 0.55;
                vec3 halfDir = normalize(uLight + V);
                float spec = pow(max(dot(N, halfDir), 0.0), 42.0) * 0.35;
                vec3 base = uUseColor == 1 ? vColor : uClay;
                if (uMode == 2) base *= 0.42;
                float ambient = 0.25 + (N.y * 0.5 + 0.5) * 0.08;
                vec3 color = base * (ambient + diffuse * 0.9 + fill);
                color += vec3(1.0, 0.55, 0.32) * rim * 0.22;
                color += vec3(spec);
                fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
            }
            """
        }
    }
}