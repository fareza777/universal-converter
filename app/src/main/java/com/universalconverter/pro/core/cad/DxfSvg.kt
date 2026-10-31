package com.universalconverter.pro.core.cad

import java.util.Locale
import kotlin.math.max

data class DxfSvgResult(
    val svg: String,
    val width: Float,
    val height: Float,
)

object DxfSvg {

    fun render(drawing: DxfDrawing): DxfSvgResult {
        val extent = max(drawing.width, drawing.height)
        val margin = max(extent * 0.035f, 1f)
        val width = drawing.width + margin * 2
        val height = drawing.height + margin * 2
        val baseStroke = (extent * 0.0014f).coerceIn(0.15f, 8f)
        val svg = StringBuilder(4096 + drawing.paths.size * 160)
        fun num(value: Float): String = String.format(Locale.US, "%.2f", value)
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(num(width))
            .append("\" height=\"").append(num(height)).append("\" viewBox=\"0 0 ")
            .append(num(width)).append(' ').append(num(height)).append("\">\n")
        svg.append("<title>Universal Converter CAD export</title>\n")
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n")
        for (path in drawing.paths) {
            if (path.points.size < 4) continue
            val color = hex(path.color)
            val stroke = max(baseStroke, path.strokeWidth).coerceAtMost(extent / 40f)
            svg.append("<path d=\"")
            var i = 0
            while (i < path.points.size) {
                val sx = path.points[i] - drawing.minX + margin
                val sy = drawing.maxY - path.points[i + 1] + margin
                svg.append(if (i == 0) "M" else "L").append(num(sx)).append(' ').append(num(sy)).append(' ')
                i += 2
            }
            if (path.closed) svg.append('Z')
            svg.append("\" fill=\"").append(if (path.filled) color else "none")
                .append("\" stroke=\"").append(color).append("\" stroke-width=\"").append(num(stroke))
                .append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n")
        }
        for (text in drawing.texts) {
            val sx = text.x - drawing.minX + margin
            val sy = drawing.maxY - text.y + margin
            val size = max(text.height, baseStroke * 3f)
            val lines = text.text.split('\n')
            val color = hex(text.color)
            svg.append("<text fill=\"").append(color).append("\" font-family=\"Helvetica,Arial,sans-serif\" font-size=\"")
                .append(num(size)).append("\" transform=\"translate(").append(num(sx)).append(' ').append(num(sy))
                .append(") rotate(").append(num(-text.rotationDegrees)).append(")\">")
            lines.forEachIndexed { index, line ->
                if (index > 0) svg.append("<tspan x=\"0\" dy=\"").append(num(size * 1.2f)).append("\">")
                else svg.append("<tspan x=\"0\" dy=\"0\">")
                svg.append(escape(line)).append("</tspan>")
            }
            svg.append("</text>\n")
        }
        svg.append("</svg>\n")
        return DxfSvgResult(svg.toString(), width, height)
    }

    private fun hex(color: Int): String {
        val rgb = color and 0xFFFFFF
        return "#" + rgb.toString(16).padStart(6, '0')
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (char in text) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(char)
            }
        }
    }
}