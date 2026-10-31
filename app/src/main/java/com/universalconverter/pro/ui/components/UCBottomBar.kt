package com.universalconverter.pro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalconverter.pro.R
import com.universalconverter.pro.ui.theme.UC

private data class NavEntry(
    val route: String,
    val label: String,
    val icon: ImageVector? = null,
)

@Composable
fun UCBottomBar(
    currentRoute: String,
    queueActive: Boolean,
    onNavigate: (String) -> Unit,
) {
    val palette = UC.palette
    val entries = listOf(
        NavEntry("convert", "Convert"),
        NavEntry("studio", "Studio", Icons.Rounded.ViewInAr),
        NavEntry("library", "Library", Icons.Rounded.PhotoLibrary),
        NavEntry("history", "History", Icons.Rounded.History),
    )
    Surface(color = palette.ivory, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(palette.line, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                }
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEach { entry ->
                val selected = when (entry.route) {
                    "library" -> currentRoute.startsWith("library")
                    else -> currentRoute == entry.route
                }
                val tint by animateColorAsState(
                    targetValue = if (selected) palette.ink else palette.muted,
                    label = "navTint",
                )
                val background by animateColorAsState(
                    targetValue = if (selected) palette.parchment else Color.Transparent,
                    label = "navBackground",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onNavigate(entry.route) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(background)
                                .padding(horizontal = 15.dp, vertical = 5.dp),
                        ) {
                            if (entry.icon != null) {
                                Icon(entry.icon, entry.label, tint = tint, modifier = Modifier.size(21.dp))
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_launcher_monochrome),
                                    contentDescription = entry.label,
                                    tint = tint,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                        if (queueActive && entry.route == "convert") {
                            Box(
                                Modifier
                                    .padding(top = 2.dp, end = 9.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(palette.ember)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 0.3.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                        color = tint,
                    )
                }
            }
        }
    }
}