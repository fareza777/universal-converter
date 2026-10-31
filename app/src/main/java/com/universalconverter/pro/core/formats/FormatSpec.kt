package com.universalconverter.pro.core.formats

enum class FormatFamily(val label: String, val order: Int) {
    MESH("3D model", 0),
    CAD("CAD drawing", 1),
    VECTOR("Vector", 2),
    IMAGE("Image", 3),
    RAW("RAW photo", 4),
    PDF("PDF", 5),
    DOCUMENT("Document", 6),
    SPREADSHEET("Spreadsheet", 7),
    DATA("Data", 8),
    AUDIO("Audio", 9),
    VIDEO("Video", 10),
    ARCHIVE("Archive", 11),
}

data class FormatSpec(
    val id: String,
    val displayName: String,
    val extensions: List<String>,
    val mimeTypes: List<String>,
    val family: FormatFamily,
    val readable: Boolean = true,
    val writable: Boolean = true,
    val note: String = "",
) {
    val primaryExtension: String get() = extensions.first()
    val mimeType: String get() = mimeTypes.firstOrNull() ?: "application/octet-stream"
}