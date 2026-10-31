package com.universalconverter.pro.core.convert

enum class QualityPreset(val title: String, val caption: String) {
    ARCHIVE("Archive", "Keep every detail. Largest files."),
    BALANCED("Balanced", "The best everyday trade-off."),
    WEB("Web", "Sharp and light for sharing."),
    SMALL("Tiny", "Smallest useful files."),
}

data class ConversionOptions(
    val preset: QualityPreset = QualityPreset.BALANCED,
    val imageMaxEdge: Int = 0,
    val imageQuality: Int = 88,
    val keepMetadata: Boolean = true,
    val imageMatte: Int = 0xFFFFFFFF.toInt(),
    val meshBinary: Boolean = true,
    val meshScale: Float = 1f,
    val audioBitrateKbps: Int = 192,
    val audioChannels: Int = 0,
    val videoHeight: Int = 720,
    val videoBitrateKbps: Int = 2800,
    val videoFps: Int = 30,
    val gifWidth: Int = 480,
    val gifFps: Int = 12,
    val gifColors: Int = 256,
    val contactColumns: Int = 4,
    val contactCount: Int = 12,
    val archiveLevel: Int = 6,
    val pdfPage: PdfPageSize = PdfPageSize.A4,
    val pageRange: String = "all",
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null,
    val stripMetadata: Boolean = false,
) {
    companion object {
        fun forPreset(preset: QualityPreset): ConversionOptions = when (preset) {
            QualityPreset.ARCHIVE -> ConversionOptions(
                preset = preset,
                imageMaxEdge = 0,
                imageQuality = 96,
                audioBitrateKbps = 256,
                videoHeight = 0,
                videoBitrateKbps = 0,
                videoFps = 30,
                gifWidth = 640,
                gifFps = 15,
                archiveLevel = 9,
                keepMetadata = true,
            )
            QualityPreset.BALANCED -> ConversionOptions(
                preset = preset,
                imageMaxEdge = 0,
                imageQuality = 88,
                audioBitrateKbps = 192,
                videoHeight = 1080,
                videoBitrateKbps = 5000,
                videoFps = 30,
                gifWidth = 480,
                gifFps = 12,
                archiveLevel = 6,
                keepMetadata = true,
            )
            QualityPreset.WEB -> ConversionOptions(
                preset = preset,
                imageMaxEdge = 2048,
                imageQuality = 82,
                audioBitrateKbps = 128,
                videoHeight = 720,
                videoBitrateKbps = 2500,
                videoFps = 30,
                gifWidth = 480,
                gifFps = 12,
                archiveLevel = 6,
                keepMetadata = false,
                stripMetadata = true,
            )
            QualityPreset.SMALL -> ConversionOptions(
                preset = preset,
                imageMaxEdge = 1280,
                imageQuality = 70,
                audioBitrateKbps = 96,
                videoHeight = 480,
                videoBitrateKbps = 1200,
                videoFps = 24,
                gifWidth = 320,
                gifFps = 10,
                archiveLevel = 9,
                keepMetadata = false,
                stripMetadata = true,
            )
        }

        fun optionsFor(preset: QualityPreset, current: ConversionOptions): ConversionOptions {
            val base = forPreset(preset)
            return base.copy(
                meshBinary = current.meshBinary,
                meshScale = current.meshScale,
                pdfPage = current.pdfPage,
                pageRange = current.pageRange,
                contactColumns = current.contactColumns,
                contactCount = current.contactCount,
                trimStartMs = current.trimStartMs,
                trimEndMs = current.trimEndMs,
            )
        }
    }
}

enum class PdfPageSize(val label: String) {
    A4("A4"),
    LETTER("Letter"),
    FIT("Fit image"),
}