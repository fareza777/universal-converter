package com.universalconverter.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalconverter.pro.data.AccentTheme
import com.universalconverter.pro.data.ThemeMode
import com.universalconverter.pro.ui.theme.UC
import com.universalconverter.pro.ui.theme.paletteFor

@Composable
fun ThemeModeControl(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = UC.palette
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val active = selected == mode
            Text(
                mode.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) palette.paper else palette.muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) palette.ink else palette.paper)
                    .border(1.dp, if (active) Color.Transparent else palette.line, RoundedCornerShape(12.dp))
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
fun AccentControl(
    selected: AccentTheme,
    onSelect: (AccentTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AccentTheme.entries.forEach { accent ->
            val palette = UC.palette
            val swatch = paletteFor(accent, palette.isDark, pureBlack = false).ember
            val active = selected == accent
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(accent) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            if (active) 2.dp else 1.dp,
                            if (active) palette.ink else palette.line.copy(alpha = 0.6f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) {
                        Icon(
                            Icons.Rounded.Check,
                            null,
                            tint = if (swatch.luminance() > 0.55f) Color(0xFF17140F) else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    accent.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 0.2.sp),
                    color = if (active) palette.ink else palette.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun PureBlackControl(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = UC.palette
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Pure black dark mode", style = MaterialTheme.typography.titleSmall, color = palette.ink)
            Text(
                "OLED-friendly black surfaces when dark mode is active.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
