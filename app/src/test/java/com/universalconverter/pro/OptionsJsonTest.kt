package com.universalconverter.pro

import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.convert.ConversionOptionsJson
import com.universalconverter.pro.core.convert.PdfPageSize
import com.universalconverter.pro.core.convert.QualityPreset
import com.universalconverter.pro.data.CustomPreset
import com.universalconverter.pro.data.CustomPresetJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OptionsJsonTest {

    @Test
    fun conversionOptionsRoundTripKeepsEveryField() {
        val options = ConversionOptions(
            preset = QualityPreset.WEB,
            imageMaxEdge = 2048,
            imageQuality = 81,
            keepMetadata = false,
            imageMatte = 0x11223344,
            meshBinary = false,
            meshScale = 2.5f,
            audioBitrateKbps = 128,
            audioChannels = 1,
            videoHeight = 1080,
            videoBitrateKbps = 5000,
            videoFps = 24,
            gifWidth = 640,
            gifFps = 15,
            gifColors = 128,
            contactColumns = 3,
            contactCount = 9,
            archiveLevel = 9,
            pdfPage = PdfPageSize.LETTER,
            pageRange = "1-3",
            trimStartMs = 1200L,
            trimEndMs = 9000L,
            stripMetadata = true,
        )
        val restored = ConversionOptionsJson.fromJson(ConversionOptionsJson.toJson(options))
        assertEquals(options, restored)
    }

    @Test
    fun nullTrimBoundsSurviveRoundTrip() {
        val options = ConversionOptions(trimStartMs = null, trimEndMs = null)
        val restored = ConversionOptionsJson.fromJson(ConversionOptionsJson.toJson(options))
        assertEquals(null, restored.trimStartMs)
        assertEquals(null, restored.trimEndMs)
    }

    @Test
    fun customPresetRoundTrip() {
        val preset = CustomPreset(name = "Print STL", options = ConversionOptions(meshBinary = true))
        val restored = CustomPresetJson.fromJson(CustomPresetJson.toJson(preset))
        assertNotNull(restored)
        assertEquals(preset.id, restored!!.id)
        assertEquals(preset.name, restored.name)
        assertEquals(preset.options, restored.options)
    }
}
