package com.universalconverter.pro.ui.theme

import androidx.compose.ui.graphics.Color

internal object Ink {
    val Paper = Color(0xFFF5F0E7)
    val Ivory = Color(0xFFFBF7F0)
    val Parchment = Color(0xFFECE4D6)
    val Line = Color(0xFFDCD2C1)
    val Ink = Color(0xFF17140F)
    val Muted = Color(0xFF60594C)
    val Ember = Color(0xFFB44724)
    val EmberSoft = Color(0xFFF1D6C8)
    val Sage = Color(0xFF4C5C49)
    val Gold = Color(0xFF9A7833)
    val Error = Color(0xFFA33A2E)
    val Canvas = Color(0xFF1B1916)
    val CanvasRaised = Color(0xFF25221E)
}

internal object Night {
    val Paper = Color(0xFF121110)
    val Ivory = Color(0xFF1A1816)
    val Parchment = Color(0xFF272320)
    val Line = Color(0xFF37322C)
    val Ink = Color(0xFFF4EEE3)
    val Muted = Color(0xFFA9A093)
    val Ember = Color(0xFFE0764F)
    val EmberSoft = Color(0xFF42271D)
    val Sage = Color(0xFFA4B49B)
    val Gold = Color(0xFFD4AE63)
    val Error = Color(0xFFF09A8C)
    val Canvas = Color(0xFF0D0C0B)
    val CanvasRaised = Color(0xFF1A1816)
}

data class UCPalette(
    val paper: Color,
    val ivory: Color,
    val parchment: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val ember: Color,
    val emberSoft: Color,
    val sage: Color,
    val gold: Color,
    val error: Color,
    val canvas: Color,
    val canvasRaised: Color,
    val isDark: Boolean,
)

val LightPalette = UCPalette(
    paper = Ink.Paper,
    ivory = Ink.Ivory,
    parchment = Ink.Parchment,
    line = Ink.Line,
    ink = Ink.Ink,
    muted = Ink.Muted,
    ember = Ink.Ember,
    emberSoft = Ink.EmberSoft,
    sage = Ink.Sage,
    gold = Ink.Gold,
    error = Ink.Error,
    canvas = Ink.Canvas,
    canvasRaised = Ink.CanvasRaised,
    isDark = false,
)

val DarkPalette = UCPalette(
    paper = Night.Paper,
    ivory = Night.Ivory,
    parchment = Night.Parchment,
    line = Night.Line,
    ink = Night.Ink,
    muted = Night.Muted,
    ember = Night.Ember,
    emberSoft = Night.EmberSoft,
    sage = Night.Sage,
    gold = Night.Gold,
    error = Night.Error,
    canvas = Night.Canvas,
    canvasRaised = Night.CanvasRaised,
    isDark = true,
)
