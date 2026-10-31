package com.universalconverter.pro.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.universalconverter.pro.core.formats.FormatFamily
import com.universalconverter.pro.ui.theme.UC
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return if (value >= 100) "${value.toInt()} ${units[unit]}" else String.format(Locale.US, "%.1f %s", value, units[unit])
}

fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "—"
    val format = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
    return format.format(Date(timestamp))
}

fun formatRelative(timestamp: Long): String {
    if (timestamp <= 0) return "—"
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 60_000) return "just now"
    if (diff < 3_600_000) return "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
    if (diff < 86_400_000) return "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
    if (diff < 7 * 86_400_000L) return "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
    return SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
}

fun formatDuration(ms: Long): String {
    if (ms < 1000) return "${ms.coerceAtLeast(0)} ms"
    if (ms < 60_000) return String.format(Locale.US, "%.1f s", ms / 1000.0)
    return "${ms / 60_000}m ${ms % 60_000 / 1000}s"
}

fun formatIcon(family: FormatFamily): ImageVector = when (family) {
    FormatFamily.MESH -> Icons.Rounded.ViewInAr
    FormatFamily.CAD -> Icons.Rounded.Architecture
    FormatFamily.VECTOR -> Icons.Rounded.Polyline
    FormatFamily.IMAGE -> Icons.Rounded.Image
    FormatFamily.RAW -> Icons.Rounded.AutoAwesome
    FormatFamily.PDF -> Icons.Rounded.PictureAsPdf
    FormatFamily.DOCUMENT -> Icons.Rounded.Description
    FormatFamily.SPREADSHEET -> Icons.Rounded.TableChart
    FormatFamily.DATA -> Icons.Rounded.DataObject
    FormatFamily.AUDIO -> Icons.Rounded.GraphicEq
    FormatFamily.VIDEO -> Icons.Rounded.Movie
    FormatFamily.ARCHIVE -> Icons.Rounded.Archive
}

@androidx.compose.runtime.Composable
fun familyColor(family: FormatFamily): Color {
    val palette = UC.palette
    return when (family) {
        FormatFamily.MESH, FormatFamily.CAD -> palette.ember
        FormatFamily.VECTOR, FormatFamily.PDF -> palette.ink
        FormatFamily.IMAGE, FormatFamily.RAW -> palette.gold
        FormatFamily.DOCUMENT, FormatFamily.SPREADSHEET, FormatFamily.DATA -> palette.sage
        FormatFamily.AUDIO, FormatFamily.VIDEO -> palette.ember
        FormatFamily.ARCHIVE -> palette.muted
    }
}

fun extensionOfName(name: String): String = name.substringAfterLast('.', "").uppercase(Locale.US).ifBlank { "FILE" }

fun presetSummary(preset: String): String = preset.lowercase().replaceFirstChar { it.uppercase() }