package com.universalconverter.pro.core.cad

import java.io.BufferedReader
import java.io.File
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

class DxfException(message: String) : Exception(message)

data class DxfPath(
    val points: FloatArray,
    val closed: Boolean,
    val color: Int,
    val strokeWidth: Float = 0f,
    val filled: Boolean = false,
)

data class DxfText(
    val x: Float,
    val y: Float,
    val height: Float,
    val text: String,
    val rotationDegrees: Float,
    val color: Int,
)

data class DxfDrawing(
    val paths: List<DxfPath>,
    val texts: List<DxfText>,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val entityCount: Int,
    val warnings: List<String>,
) {
    val width: Float get() = (maxX - minX).coerceAtLeast(1f)
    val height: Float get() = (maxY - minY).coerceAtLeast(1f)
}

data class DxfBounds(
    val minX: Float = Float.POSITIVE_INFINITY,
    val minY: Float = Float.POSITIVE_INFINITY,
    val maxX: Float = Float.NEGATIVE_INFINITY,
    val maxY: Float = Float.NEGATIVE_INFINITY,
)

object DxfReader {

    private const val MAX_ENTITIES = 300_000

    fun read(file: File): DxfDrawing {
        val rawEntities = ArrayList<RawEntity>()
        val layerColors = HashMap<String, Int>()
        var section: String? = null
        var pendingSection = false
        var currentEntity: RawEntity? = null
        var currentLayerName: String? = null
        var currentLayerColor = 7
        var currentSectionLine = 0

        BufferedReader(file.reader(Charsets.UTF_8), 1 shl 16).use { reader ->
            var lineNumber = 0
            while (true) {
                val codeLine = reader.readLine() ?: break
                val valueLine = reader.readLine() ?: break
                lineNumber += 2
                val code = codeLine.trim().toIntOrNull() ?: continue
                val value = valueLine.trim()

                if (code == 0) {
                    currentEntity?.let {
                        rawEntities += it
                        if (rawEntities.size > MAX_ENTITIES) throw DxfException("This DXF has more than $MAX_ENTITIES entities.")
                    }
                    currentEntity = null
                    if (currentLayerName != null) {
                        layerColors[currentLayerName!!] = currentLayerColor
                        currentLayerName = null
                    }
                    when (value.uppercase()) {
                        "SECTION" -> { pendingSection = true; section = null }
                        "ENDSEC" -> { section = null; pendingSection = false }
                        "EOF" -> break
                        "LAYER" -> if (section == "TABLES") {
                            currentLayerName = ""
                            currentLayerColor = 7
                        }
                        "ENDBLK" -> Unit
                        else -> if (section == "ENTITIES") {
                            currentEntity = RawEntity(value.uppercase(), ArrayList(16))
                        }
                    }
                    continue
                }
                if (pendingSection && code == 2) {
                    section = value.uppercase()
                    pendingSection = false
                    currentSectionLine = lineNumber
                    continue
                }
                if (section == "TABLES" && currentLayerName != null) {
                    when (code) {
                        2 -> currentLayerName = value
                        62 -> currentLayerColor = value.toIntOrNull() ?: 7
                    }
                    continue
                }
                if (section == "ENTITIES" && currentEntity != null) {
                    currentEntity!!.fields += code to value
                    if (currentEntity!!.fields.size > 200_000) {
                        throw DxfException("A DXF entity near line $lineNumber has too many properties.")
                    }
                }
                if (section == null && pendingSection) currentSectionLine = lineNumber
            }
        }
        currentEntity?.let { rawEntities += it }
        if (rawEntities.isEmpty()) throw DxfException("This DXF contains no entities in its model space.")

        val paths = ArrayList<DxfPath>()
        val texts = ArrayList<DxfText>()
        val warnings = mutableListOf<String>()
        val skipped = HashMap<String, Int>()
        var splines = 0
        var inserts = 0
        var index = 0
        while (index < rawEntities.size) {
            val raw = rawEntities[index]
            val type = raw.type
            val layer = raw.first(8) ?: "0"
            val layerColor = layerColors[layer] ?: 7
            val colorIndex = raw.firstInt(62) ?: 256
            val trueColor = raw.firstInt(420)
            val color = resolveColor(colorIndex, trueColor, layerColor)
            val lineWeight = raw.firstInt(370) ?: 0
            val stroke = if (lineWeight > 0) lineWeight / 100f else 0f
            when (type) {
                "LINE" -> {
                    val x1 = raw.firstFloat(10)
                    val y1 = raw.firstFloat(20)
                    val x2 = raw.firstFloat(11)
                    val y2 = raw.firstFloat(21)
                    if (x1 != null && y1 != null && x2 != null && y2 != null) {
                        paths += DxfPath(floatArrayOf(x1, y1, x2, y2), false, color, stroke)
                    }
                }
                "LWPOLYLINE" -> {
                    val points = parseLightWeightPolyline(raw)
                    if (points.size >= 4) {
                        val closed = (raw.firstInt(70) ?: 0) and 1 != 0
                        paths += DxfPath(points, closed, color, stroke)
                    }
                }
                "POLYLINE" -> {
                    val vertices = ArrayList<Float>()
                    var closed = (raw.firstInt(70) ?: 0) and 1 != 0
                    var cursor = index + 1
                    var bulge = 0f
                    val nx = ArrayList<Float>()
                    while (cursor < rawEntities.size && rawEntities[cursor].type == "VERTEX") {
                        val vertex = rawEntities[cursor]
                        val x = vertex.firstFloat(10)
                        val y = vertex.firstFloat(20)
                        if (x != null && y != null) {
                            nx += x
                            nx += y
                            vertices += x
                            vertices += y
                            bulge = vertex.firstFloat(42) ?: 0f
                        }
                        cursor++
                    }
                    if (cursor < rawEntities.size && rawEntities[cursor].type == "SEQEND") cursor++
                    // Bulges are carried on the segment that starts at the vertex; approximate them.
                    val baked = ArrayList<Float>()
                    if (vertices.size >= 4) {
                        val simple = ArrayList<Pair<Float, Float>>()
                        for (p in vertices.indices step 2) simple += vertices[p] to vertices[p + 1]
                        val bulges = FloatArray(simple.size)
                        var vertexIndex = 0
                        var scan = index + 1
                        var i = 0
                        while (scan < rawEntities.size && rawEntities[scan].type == "VERTEX") {
                            bulges[i] = rawEntities[scan].firstFloat(42) ?: 0f
                            scan++
                            i++
                        }
                        appendPolyline(simple, bulges, closed, baked)
                        if (baked.size >= 4) paths += DxfPath(baked.toFloatArray(), closed, color, stroke)
                    }
                    index = cursor - 1
                }
                "CIRCLE" -> {
                    val cx = raw.firstFloat(10)
                    val cy = raw.firstFloat(20)
                    val r = raw.firstFloat(40)
                    if (cx != null && cy != null && r != null && r > 0f) {
                        paths += DxfPath(sampleCircle(cx, cy, r), true, color, stroke)
                    }
                }
                "ARC" -> {
                    val cx = raw.firstFloat(10)
                    val cy = raw.firstFloat(20)
                    val r = raw.firstFloat(40)
                    val start = raw.firstFloat(50)
                    val end = raw.firstFloat(51)
                    if (cx != null && cy != null && r != null && start != null && end != null && r > 0f) {
                        paths += DxfPath(sampleArc(cx, cy, r, start, end), false, color, stroke)
                    }
                }
                "ELLIPSE" -> {
                    val cx = raw.firstFloat(10)
                    val cy = raw.firstFloat(20)
                    val mx = raw.firstFloat(11)
                    val my = raw.firstFloat(21)
                    val ratio = raw.firstFloat(40) ?: 1f
                    val start = raw.firstFloat(41) ?: 0f
                    val end = raw.firstFloat(42) ?: (Math.PI * 2).toFloat()
                    if (cx != null && cy != null && mx != null && my != null) {
                        paths += DxfPath(sampleEllipse(cx, cy, mx, my, ratio, start, end), abs(end - start) >= Math.PI * 2 - 1e-4, color, stroke)
                    }
                }
                "TEXT", "MTEXT" -> {
                    val x = raw.firstFloat(10)
                    val y = raw.firstFloat(20)
                    val height = raw.firstFloat(40) ?: 2.5f
                    val value = raw.fields.filter { it.first == 1 || it.first == 3 }.joinToString("") { it.second }
                    val rotation = raw.firstFloat(50) ?: 0f
                    if (x != null && y != null && value.isNotBlank() && height > 0f) {
                        val cleaned = cleanText(value)
                        if (cleaned.isNotBlank()) texts += DxfText(x, y, height, cleaned, rotation, color)
                    }
                }
                "SPLINE" -> {
                    val xs = raw.allFloat(11).takeIf { it.isNotEmpty() } ?: raw.allFloat(10)
                    val ys = raw.allFloat(21).takeIf { it.isNotEmpty() } ?: raw.allFloat(20)
                    if (xs.size >= 2 && xs.size == ys.size) {
                        val points = FloatArray(xs.size * 2)
                        for (i in xs.indices) {
                            points[i * 2] = xs[i]
                            points[i * 2 + 1] = ys[i]
                        }
                        val closed = (raw.firstInt(70) ?: 0) and 1 != 0
                        paths += DxfPath(points, closed, color, stroke)
                        splines++
                    }
                }
                "SOLID", "TRACE" -> {
                    val a = raw.firstFloat(10)
                    val b = raw.firstFloat(20)
                    val c = raw.firstFloat(11)
                    val d = raw.firstFloat(21)
                    val e = raw.firstFloat(12)
                    val f = raw.firstFloat(22)
                    val g = raw.firstFloat(13)
                    val h = raw.firstFloat(23)
                    if (a != null && b != null && c != null && d != null) {
                        val ex = e ?: a
                        val fy = f ?: b
                        val gx = g ?: c
                        val hy = h ?: d
                        // SOLID stores corners in a Z order; use the conventional 1,2,4,3 sequence.
                        paths += DxfPath(floatArrayOf(a, b, c, d, gx, hy, ex, fy), true, color, stroke, filled = true)
                    }
                }
                "INSERT" -> inserts++
                "ATTRIB", "ATTDEF", "SEQEND", "VERTEX" -> Unit
                "HATCH", "DIMENSION", "LEADER", "MLINE", "MULTILEADER", "POINT" -> {
                    skipped[type] = (skipped[type] ?: 0) + 1
                }
                else -> skipped[type] = (skipped[type] ?: 0) + 1
            }
            index++
        }
        if (paths.isEmpty() && texts.isEmpty()) {
            throw DxfException("This DXF contains no drawable lines, curves or text. Block references and hatches are not supported.")
        }
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (path in paths) {
            var i = 0
            while (i < path.points.size) {
                minX = min(minX, path.points[i]); maxX = max(maxX, path.points[i])
                minY = min(minY, path.points[i + 1]); maxY = max(maxY, path.points[i + 1])
                i += 2
            }
        }
        for (text in texts) {
            val width = text.text.length * text.height * 0.62f
            minX = min(minX, text.x); maxX = max(maxX, text.x + width)
            minY = min(minY, text.y); maxY = max(maxY, text.y + text.height)
        }
        if (maxX - minX < 1e-4f) { minX -= 1f; maxX += 1f }
        if (maxY - minY < 1e-4f) { minY -= 1f; maxY += 1f }
        if (inserts > 0) warnings += "$inserts block insert(s) were not expanded; their geometry is not shown."
        if (splines > 0) warnings += "$splines spline(s) were approximated by their fit/control points."
        if (skipped.isNotEmpty()) {
            val names = skipped.entries.sortedByDescending { it.value }.take(3).joinToString { "${it.key} ×${it.value}" }
            warnings += "Unsupported entities skipped: $names."
        }
        return DxfDrawing(
            paths = paths,
            texts = texts,
            minX = minX,
            minY = minY,
            maxX = maxX,
            maxY = maxY,
            entityCount = rawEntities.size,
            warnings = warnings,
        )
    }

    private data class RawEntity(val type: String, val fields: MutableList<Pair<Int, String>>) {
        fun first(code: Int): String? = fields.firstOrNull { it.first == code }?.second

        fun all(code: Int): List<String> = fields.filter { it.first == code }.map { it.second }

        fun firstInt(code: Int): Int? = first(code)?.toIntOrNull()

        fun firstFloat(code: Int): Float? = first(code)?.toFloatOrNull()

        fun allFloat(code: Int): List<Float> = fields.filter { it.first == code }.mapNotNull { it.second.toFloatOrNull() }
    }

    private fun parseLightWeightPolyline(raw: RawEntity): FloatArray {
        val points = ArrayList<Pair<Float, Float>>()
        val bulges = ArrayList<Float>()
        var x: Float? = null
        var y: Float? = null
        var bulge = 0f
        for ((code, value) in raw.fields) {
            when (code) {
                10 -> {
                    if (x != null && y != null) {
                        points += x!! to y!!
                        bulges += bulge
                    }
                    x = value.toFloatOrNull()
                    y = null
                    bulge = 0f
                }
                20 -> y = value.toFloatOrNull()
                42 -> bulge = value.toFloatOrNull() ?: 0f
            }
        }
        if (x != null && y != null) {
            points += x!! to y!!
            bulges += bulge
        }
        if (points.size < 2) return FloatArray(0)
        val closed = (raw.firstInt(70) ?: 0) and 1 != 0
        val out = ArrayList<Float>()
        appendPolyline(points, bulges.toFloatArray(), closed, out)
        return out.toFloatArray()
    }

    private fun appendPolyline(
        points: List<Pair<Float, Float>>,
        bulges: FloatArray,
        closed: Boolean,
        out: MutableList<Float>,
    ) {
        if (points.isEmpty()) return
        val segmentCount = if (closed) points.size else points.size - 1
        out += points[0].first
        out += points[0].second
        for (i in 0 until segmentCount) {
            val from = points[i]
            val to = points[(i + 1) % points.size]
            val bulge = bulges.getOrElse(i) { 0f }
            if (abs(bulge) < 1e-6f) {
                out += to.first
                out += to.second
            } else {
                val arc = bulgeArc(from.first, from.second, to.first, to.second, bulge)
                for (p in arc) {
                    out += p.first
                    out += p.second
                }
            }
        }
    }

    private fun bulgeArc(x1: Float, y1: Float, x2: Float, y2: Float, bulge: Float): List<Pair<Float, Float>> {
        val dx = x2 - x1
        val dy = y2 - y1
        val chord = hypot(dx, dy)
        if (chord < 1e-9f) return emptyList()
        val theta = 4 * atan(bulge.toDouble())
        val half = theta / 2
        if (abs(tan(half)) < 1e-9) return listOf(x2 to y2)
        val radius = chord / (2 * sin(half))
        val h = (chord / 2) / tan(half)
        val mx = (x1 + x2) / 2
        val my = (y1 + y2) / 2
        val ux = -dy / chord
        val uy = dx / chord
        val cx = mx + ux * h
        val cy = my + uy * h
        val startAngle = atan2(y1 - cy.toFloat(), x1 - cx.toFloat())
        val steps = (abs(theta) / (Math.PI / 16)).toInt().coerceIn(4, 64)
        val out = ArrayList<Pair<Float, Float>>(steps)
        for (i in 1..steps) {
            val angle = startAngle + theta * i / steps
            out += (cx + radius * cos(angle)).toFloat() to (cy + radius * sin(angle)).toFloat()
        }
        return out
    }

    private fun sampleCircle(cx: Float, cy: Float, r: Float): FloatArray {
        val steps = 72
        val out = FloatArray(steps * 2)
        for (i in 0 until steps) {
            val angle = Math.PI * 2 * i / steps
            out[i * 2] = (cx + r * cos(angle)).toFloat()
            out[i * 2 + 1] = (cy + r * sin(angle)).toFloat()
        }
        return out
    }

    private fun sampleArc(cx: Float, cy: Float, r: Float, startDeg: Float, endDeg: Float): FloatArray {
        var sweep = endDeg - startDeg
        if (sweep <= 0f) sweep += 360f
        val steps = max(12, (sweep / 5f).toInt())
        val out = FloatArray((steps + 1) * 2)
        for (i in 0..steps) {
            val angle = Math.toRadians((startDeg + sweep * i / steps).toDouble())
            out[i * 2] = (cx + r * cos(angle)).toFloat()
            out[i * 2 + 1] = (cy + r * sin(angle)).toFloat()
        }
        return out
    }

    private fun sampleEllipse(
        cx: Float, cy: Float,
        mx: Float, my: Float,
        ratio: Float,
        startParam: Float,
        endParam: Float,
    ): FloatArray {
        val majorLength = hypot(mx, my)
        if (majorLength < 1e-9f) return FloatArray(0)
        val minorX = -my * ratio
        val minorY = mx * ratio
        var sweep = endParam - startParam
        if (sweep <= 0f) sweep += (Math.PI * 2).toFloat()
        val steps = (sweep / (Math.PI / 32)).toInt().coerceIn(16, 160)
        val out = FloatArray((steps + 1) * 2)
        for (i in 0..steps) {
            val t = startParam + sweep * i / steps
            out[i * 2] = (cx + mx * cos(t) + minorX * sin(t)).toFloat()
            out[i * 2 + 1] = (cy + my * cos(t) + minorY * sin(t)).toFloat()
        }
        return out
    }

    private fun resolveColor(index: Int, trueColor: Int?, layerColor: Int): Int {
        if (trueColor != null) {
            val rgb = trueColor and 0xFFFFFF
            return normalizeInk(0xFF000000.toInt() or rgb)
        }
        val aci = if (index == 0 || index == 256) layerColor else index
        return normalizeInk(0xFF000000.toInt() or aciColor(aci))
    }

    private fun normalizeInk(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return if (min(r, min(g, b)) >= 224) 0xFF14120F.toInt() else argb
    }

    fun aciColor(index: Int): Int {
        return when (index) {
            1 -> 0xFF0000
            2 -> 0xFFFF00
            3 -> 0x00FF00
            4 -> 0x00FFFF
            5 -> 0x0000FF
            6 -> 0xFF00FF
            7, 0 -> 0x000000
            8 -> 0x808080
            9 -> 0xC0C0C0
            250 -> 0x333333
            251 -> 0x505050
            252 -> 0x696969
            253 -> 0x828282
            254 -> 0xBEBEBE
            255 -> 0xFFFFFF
            else -> {
                val hue = ((index - 10) * 17) % 360
                val value = if (index > 200) 0.72 else 0.9
                hsvToRgb(hue.toFloat(), 0.62f, value.toFloat())
            }
        }
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return (((r + m) * 255).toInt() shl 16) or (((g + m) * 255).toInt() shl 8) or ((b + m) * 255).toInt()
    }

    private fun cleanText(value: String): String {
        var text = value
            .replace("\\P", "\n")
            .replace("\\~", " ")
            .replace("%%d", "°")
            .replace("%%D", "°")
            .replace("%%c", "Ø")
            .replace("%%C", "Ø")
            .replace("%%p", "±")
            .replace("%%P", "±")
        text = text.replace(Regex("\\\\[A-Za-z][^;{}]*;"), "")
        text = text.replace("{", "").replace("}", "")
        return text.trim()
    }
}