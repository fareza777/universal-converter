package com.universalconverter.pro.data

import android.content.Context
import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.convert.QualityPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class AccentTheme(val label: String) {
    EMBER("Ember"),
    SAGE("Sage"),
    GOLD("Gold"),
    COBALT("Cobalt"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentTheme: AccentTheme = AccentTheme.EMBER,
    val pureBlack: Boolean = false,
    val defaultPreset: QualityPreset = QualityPreset.BALANCED,
    val autoSaveToDownloads: Boolean = false,
    val notificationsAsked: Boolean = false,
    val autoOpenQueue: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val onboardingDone: Boolean = false,
    val removeAdsPurchased: Boolean = false,
    val adFreeUntilEpochMs: Long = 0L,
) {
    fun adsFree(now: Long = System.currentTimeMillis()): Boolean =
        removeAdsPurchased || now < adFreeUntilEpochMs
}

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("universal_converter_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _customPresets = MutableStateFlow(loadPresets())
    val customPresets: StateFlow<List<CustomPreset>> = _customPresets.asStateFlow()

    private fun load(): AppSettings {
        val theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM)
        val accent = runCatching { AccentTheme.valueOf(prefs.getString("accent", AccentTheme.EMBER.name)!!) }
            .getOrDefault(AccentTheme.EMBER)
        val preset = runCatching { QualityPreset.valueOf(prefs.getString("preset", QualityPreset.BALANCED.name)!!) }
            .getOrDefault(QualityPreset.BALANCED)
        return AppSettings(
            themeMode = theme,
            accentTheme = accent,
            pureBlack = prefs.getBoolean("pure_black", false),
            defaultPreset = preset,
            autoSaveToDownloads = prefs.getBoolean("auto_downloads", false),
            notificationsAsked = prefs.getBoolean("notifications_asked", false),
            autoOpenQueue = prefs.getBoolean("auto_open_queue", true),
            keepScreenAwake = prefs.getBoolean("keep_screen_awake", true),
            onboardingDone = prefs.getBoolean("onboarding_done", false),
            removeAdsPurchased = prefs.getBoolean("remove_ads_purchased", false),
            adFreeUntilEpochMs = prefs.getLong("ad_free_until", 0L),
        )
    }

    fun saveCustomPreset(name: String, options: ConversionOptions): CustomPreset? {
        val cleanName = name.trim().take(40)
        if (cleanName.isBlank()) return null
        val preset = CustomPreset(name = cleanName, options = options)
        _customPresets.update { current -> (listOf(preset) + current).take(30) }
        persistPresets()
        return preset
    }

    fun deleteCustomPreset(id: String) {
        _customPresets.update { current -> current.filterNot { it.id == id } }
        persistPresets()
    }

    private fun loadPresets(): List<CustomPreset> {
        val raw = prefs.getString("custom_presets", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    CustomPresetJson.fromJson(array.getJSONObject(i))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistPresets() {
        val array = JSONArray()
        _customPresets.value.forEach { array.put(CustomPresetJson.toJson(it)) }
        prefs.edit().putString("custom_presets", array.toString()).apply()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString("theme", next.themeMode.name)
            .putString("accent", next.accentTheme.name)
            .putBoolean("pure_black", next.pureBlack)
            .putString("preset", next.defaultPreset.name)
            .putBoolean("auto_downloads", next.autoSaveToDownloads)
            .putBoolean("notifications_asked", next.notificationsAsked)
            .putBoolean("auto_open_queue", next.autoOpenQueue)
            .putBoolean("keep_screen_awake", next.keepScreenAwake)
            .putBoolean("onboarding_done", next.onboardingDone)
            .putBoolean("remove_ads_purchased", next.removeAdsPurchased)
            .putLong("ad_free_until", next.adFreeUntilEpochMs)
            .apply()
        _settings.value = next
    }
}
