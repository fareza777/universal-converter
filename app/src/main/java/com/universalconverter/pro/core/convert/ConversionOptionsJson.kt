package com.universalconverter.pro.core.convert

import org.json.JSONObject

object ConversionOptionsJson {

    fun toJson(options: ConversionOptions): JSONObject = JSONObject().apply {
        put("preset", options.preset.name)
        put("imageMaxEdge", options.imageMaxEdge)
        put("imageQuality", options.imageQuality)
        put("keepMetadata", options.keepMetadata)
        put("imageMatte", options.imageMatte)
        put("meshBinary", options.meshBinary)
        put("meshScale", options.meshScale.toDouble())
        put("audioBitrateKbps", options.audioBitrateKbps)
        put("audioChannels", options.audioChannels)
        put("videoHeight", options.videoHeight)
        put("videoBitrateKbps", options.videoBitrateKbps)
        put("videoFps", options.videoFps)
        put("gifWidth", options.gifWidth)
        put("gifFps", options.gifFps)
        put("gifColors", options.gifColors)
        put("contactColumns", options.contactColumns)
        put("contactCount", options.contactCount)
        put("archiveLevel", options.archiveLevel)
        put("pdfPage", options.pdfPage.name)
        put("pageRange", options.pageRange)
        put("trimStartMs", options.trimStartMs ?: JSONObject.NULL)
        put("trimEndMs", options.trimEndMs ?: JSONObject.NULL)
        put("stripMetadata", options.stripMetadata)
    }

    fun fromJson(json: JSONObject): ConversionOptions {
        val defaults = ConversionOptions()
        return ConversionOptions(
            preset = enumValueOrDefault(json.optString("preset"), defaults.preset),
            imageMaxEdge = json.optInt("imageMaxEdge", defaults.imageMaxEdge),
            imageQuality = json.optInt("imageQuality", defaults.imageQuality),
            keepMetadata = json.optBoolean("keepMetadata", defaults.keepMetadata),
            imageMatte = json.optInt("imageMatte", defaults.imageMatte),
            meshBinary = json.optBoolean("meshBinary", defaults.meshBinary),
            meshScale = json.optDouble("meshScale", defaults.meshScale.toDouble()).toFloat(),
            audioBitrateKbps = json.optInt("audioBitrateKbps", defaults.audioBitrateKbps),
            audioChannels = json.optInt("audioChannels", defaults.audioChannels),
            videoHeight = json.optInt("videoHeight", defaults.videoHeight),
            videoBitrateKbps = json.optInt("videoBitrateKbps", defaults.videoBitrateKbps),
            videoFps = json.optInt("videoFps", defaults.videoFps),
            gifWidth = json.optInt("gifWidth", defaults.gifWidth),
            gifFps = json.optInt("gifFps", defaults.gifFps),
            gifColors = json.optInt("gifColors", defaults.gifColors),
            contactColumns = json.optInt("contactColumns", defaults.contactColumns),
            contactCount = json.optInt("contactCount", defaults.contactCount),
            archiveLevel = json.optInt("archiveLevel", defaults.archiveLevel),
            pdfPage = enumValueOrDefault(json.optString("pdfPage"), defaults.pdfPage),
            pageRange = json.optString("pageRange", defaults.pageRange),
            trimStartMs = if (json.has("trimStartMs") && !json.isNull("trimStartMs")) json.optLong("trimStartMs") else null,
            trimEndMs = if (json.has("trimEndMs") && !json.isNull("trimEndMs")) json.optLong("trimEndMs") else null,
            stripMetadata = json.optBoolean("stripMetadata", defaults.stripMetadata),
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name!!) }.getOrDefault(fallback)
}
