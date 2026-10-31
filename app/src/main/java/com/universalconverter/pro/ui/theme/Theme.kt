package com.universalconverter.pro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.universalconverter.pro.data.AccentTheme

private data class AccentColors(
    val light: Color,
    val lightSoft: Color,
    val dark: Color,
    val darkSoft: Color,
)

private val AccentPalettes = mapOf(
    AccentTheme.EMBER to AccentColors(
        light = Color(0xFFB44724),
        lightSoft = Color(0xFFF1D6C8),
        dark = Color(0xFFE0764F),
        darkSoft = Color(0xFF42271D),
    ),
    AccentTheme.SAGE to AccentColors(
        light = Color(0xFF4C5C49),
        lightSoft = Color(0xFFDDE5D8),
        dark = Color(0xFFA4B49B),
        darkSoft = Color(0xFF2E3B2B),
    ),
    AccentTheme.GOLD to AccentColors(
        light = Color(0xFF9A7833),
        lightSoft = Color(0xFFF0E3C6),
        dark = Color(0xFFD4AE63),
        darkSoft = Color(0xFF3A2F1B),
    ),
    AccentTheme.COBALT to AccentColors(
        light = Color(0xFF2F5D8A),
        lightSoft = Color(0xFFD8E4F0),
        dark = Color(0xFF8BB8E8),
        darkSoft = Color(0xFF1E3040),
    ),
)

fun paletteFor(accent: AccentTheme, dark: Boolean, pureBlack: Boolean): UCPalette {
    val colors = AccentPalettes[accent] ?: AccentPalettes.getValue(AccentTheme.EMBER)
    val base = if (dark) DarkPalette else LightPalette
    var palette = base.copy(
        ember = if (dark) colors.dark else colors.light,
        emberSoft = if (dark) colors.darkSoft else colors.lightSoft,
    )
    if (dark && pureBlack) {
        palette = palette.copy(
            paper = Color(0xFF000000),
            ivory = Color(0xFF0A0A09),
            parchment = Color(0xFF171615),
            line = Color(0xFF2B2926),
            canvas = Color(0xFF000000),
            canvasRaised = Color(0xFF121110),
        )
    }
    return palette
}

private fun schemeFor(palette: UCPalette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = palette.ember,
        onPrimary = palette.paper,
        primaryContainer = palette.emberSoft,
        onPrimaryContainer = palette.ink,
        secondary = palette.sage,
        onSecondary = palette.paper,
        secondaryContainer = palette.parchment,
        onSecondaryContainer = palette.ink,
        tertiary = palette.gold,
        onTertiary = palette.paper,
        background = palette.paper,
        onBackground = palette.ink,
        surface = palette.ivory,
        onSurface = palette.ink,
        surfaceVariant = palette.parchment,
        onSurfaceVariant = palette.muted,
        outline = palette.line,
        outlineVariant = palette.line,
        error = palette.error,
        onError = palette.paper,
        scrim = Color(0xE6000000),
    )
} else {
    lightColorScheme(
        primary = palette.ember,
        onPrimary = palette.ivory,
        primaryContainer = palette.emberSoft,
        onPrimaryContainer = palette.ink,
        secondary = palette.sage,
        onSecondary = palette.ivory,
        secondaryContainer = palette.parchment,
        onSecondaryContainer = palette.ink,
        tertiary = palette.gold,
        onTertiary = palette.ivory,
        background = palette.paper,
        onBackground = palette.ink,
        surface = palette.ivory,
        onSurface = palette.ink,
        surfaceVariant = palette.parchment,
        onSurfaceVariant = palette.muted,
        outline = palette.line,
        outlineVariant = palette.line,
        error = palette.error,
        onError = Color.White,
        scrim = Color(0xCC14120F),
    )
}

val UCShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Immutable
data object UC {
    val palette: UCPalette
        @Composable get() = LocalPalette.current
}

@Composable
fun UniversalConverterTheme(
    darkTheme: Boolean = false,
    accentTheme: AccentTheme = AccentTheme.EMBER,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = remember(darkTheme, accentTheme, pureBlack) {
        paletteFor(accentTheme, darkTheme, pureBlack)
    }
    val scheme = remember(palette, darkTheme) { schemeFor(palette, darkTheme) }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = UCTypography,
            shapes = UCShapes,
            content = content,
        )
    }
}
