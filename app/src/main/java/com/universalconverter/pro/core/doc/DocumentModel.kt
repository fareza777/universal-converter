package com.universalconverter.pro.core.doc

data class DocModel(
    val title: String,
    val blocks: List<DocBlock>,
)

sealed interface DocBlock {
    data class Heading(val level: Int, val text: String) : DocBlock
    data class Paragraph(val text: String, val bullet: Boolean = false) : DocBlock
    data class Quote(val text: String) : DocBlock
    data class Code(val text: String) : DocBlock
    data class Table(val rows: List<List<String>>) : DocBlock
}

data class TableModel(
    val name: String,
    val rows: List<List<String>>,
) {
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0
    val rowCount: Int get() = rows.size

    fun toDocument(title: String = name): DocModel {
        val blocks = mutableListOf<DocBlock>()
        if (title.isNotBlank()) blocks += DocBlock.Heading(1, title)
        if (rows.isNotEmpty()) blocks += DocBlock.Table(rows)
        return DocModel(title, blocks)
    }

    fun normalize(width: Int = columnCount): TableModel {
        if (width <= 0) return this
        return copy(rows = rows.map { row -> List(width) { index -> row.getOrElse(index) { "" } } })
    }
}

fun DocModel.plainText(): String {
    val out = StringBuilder()
    for (block in blocks) {
        when (block) {
            is DocBlock.Heading -> out.append(block.text).append("\n\n")
            is DocBlock.Paragraph -> out.append(if (block.bullet) "• " else "").append(block.text).append("\n")
            is DocBlock.Quote -> out.append("> ").append(block.text).append("\n")
            is DocBlock.Code -> out.append(block.text).append("\n")
            is DocBlock.Table -> {
                for (row in block.rows) out.append(row.joinToString("\t")).append("\n")
                out.append('\n')
            }
        }
    }
    return out.toString().trim() + "\n"
}

fun sanitizeDocText(value: String): String {
    val builder = StringBuilder(value.length)
    for (char in value) {
        when {
            char == '\n' || char == '\t' -> builder.append(char)
            char.code < 32 -> Unit
            else -> builder.append(char)
        }
    }
    return builder.toString()
}