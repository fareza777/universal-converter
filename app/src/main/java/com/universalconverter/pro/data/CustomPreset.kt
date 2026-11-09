package com.universalconverter.pro.data

import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.convert.ConversionOptionsJson
import org.json.JSONObject
import java.util.UUID

data class CustomPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val options: ConversionOptions,
    val createdAt: Long = System.currentTimeMillis(),
)

object CustomPresetJson {

    fun toJson(preset: CustomPreset): JSONObject = JSONObject().apply {
        put("id", preset.id)
        put("name", preset.name)
        put("createdAt", preset.createdAt)
        put("options", ConversionOptionsJson.toJson(preset.options))
    }

    fun fromJson(json: JSONObject): CustomPreset? = runCatching {
        val name = json.optString("name").trim()
        if (name.isBlank()) return null
        CustomPreset(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = name,
            options = ConversionOptionsJson.fromJson(json.getJSONObject("options")),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        )
    }.getOrNull()
}
