package com.universalconverter.pro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalconverter.pro.ConverterApp
import com.universalconverter.pro.R
import com.universalconverter.pro.core.formats.FormatFamily
import com.universalconverter.pro.data.ThumbnailLoader
import com.universalconverter.pro.ui.formatIcon
import com.universalconverter.pro.ui.familyColor
import com.universalconverter.pro.ui.theme.UC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PaperBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = UC.palette
    Box(modifier = modifier.background(palette.paper)) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            drawRect(
                Brush.verticalGradient(
                    listOf(palette.ivory, palette.paper),
                    startY = 0f,
                    endY = size.height,
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(palette.ember.copy(alpha = if (palette.isDark) 0.08f else 0.055f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.02f),
                    radius = size.width * 0.85f,
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * 0.9f, size.height * 0.02f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(palette.gold.copy(alpha = if (palette.isDark) 0.04f else 0.035f), Color.Transparent),
                    center = Offset(size.width * 0.05f, size.height * 0.8f),
                    radius = size.width * 0.7f,
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.05f, size.height * 0.8f),
            )
        }
        content()
    }
}

@Composable
fun UCLogo(size: Dp = 38.dp, dark: Boolean = false) {
    val palette = UC.palette
    val background = if (dark) palette.paper else palette.ink
    val foreground = if (dark) palette.ink else palette.paper
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(size)) {
            val unit = this.size.minDimension / 64f
            fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
            val stroke = 5.6f * unit
            fun arrow(start: Offset, end: Offset, tip: Offset, back: Offset) {
                drawLine(foreground, start, end, strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(foreground, tip, end, strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(foreground, end, back, strokeWidth = stroke, cap = StrokeCap.Round)
            }
            arrow(point(8f, 20f), point(50f, 20f), point(41f, 11f), point(41f, 29f))
            arrow(point(56f, 44f), point(16f, 44f), point(25f, 35f), point(25f, 53f))
            val top = Path().apply {
                moveTo(32f * unit, 23f * unit); lineTo(41f * unit, 28f * unit)
                lineTo(32f * unit, 33f * unit); lineTo(23f * unit, 28f * unit); close()
            }
            val left = Path().apply {
                moveTo(23f * unit, 28f * unit); lineTo(32f * unit, 33f * unit)
                lineTo(32f * unit, 43f * unit); lineTo(23f * unit, 38f * unit); close()
            }
            val right = Path().apply {
                moveTo(41f * unit, 28f * unit); lineTo(41f * unit, 38f * unit)
                lineTo(32f * unit, 43f * unit); lineTo(32f * unit, 33f * unit); close()
            }
            drawPath(top, palette.ember.copy(alpha = 0.55f))
            drawPath(left, palette.ember.copy(alpha = 0.8f))
            drawPath(right, palette.ember)
        }
    }
}

@Composable
fun UCWordmark(dark: Boolean = false, compact: Boolean = false) {
    val palette = UC.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        UCLogo(size = if (compact) 32.dp else 38.dp, dark = dark)
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                "Universal",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = if (compact) 21.sp else 24.sp),
                color = if (dark) palette.paper else palette.ink,
            )
            Text(
                "CONVERTER",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.0.sp, fontSize = 12.sp),
                color = if (dark) palette.paper.copy(alpha = 0.64f) else palette.muted,
            )
        }
    }
}

@Composable
fun UCTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backIcon: ImageVector = Icons.Rounded.ArrowBack,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val palette = UC.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            SquareIconButton(backIcon, "Back", onBack)
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
fun SquareIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = UC.palette.ink,
    background: Color = UC.palette.ivory,
    border: Boolean = true,
    size: Dp = 42.dp,
) {
    val palette = UC.palette
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .then(if (border) Modifier.border(1.dp, palette.line, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun UCPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector = Icons.Rounded.ArrowForward,
    loading: Boolean = false,
    dark: Boolean = false,
) {
    val palette = UC.palette
    val background = if (dark) palette.paper else palette.ink
    val foreground = if (dark) palette.ink else palette.paper
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) background else background.copy(alpha = 0.25f))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = foreground,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = foreground)
            Spacer(Modifier.width(10.dp))
            Icon(icon, null, tint = foreground, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun UCGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val palette = UC.palette
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(17.dp))
            .border(1.dp, palette.line, RoundedCornerShape(17.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = palette.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = palette.ink)
    }
}

@Composable
fun HairlineCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    background: Color = UC.palette.ivory,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val palette = UC.palette
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .border(1.dp, palette.line, RoundedCornerShape(24.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun FamilyMark(family: FormatFamily, size: Dp = 46.dp) {
    val tint = familyColor(family)
    val palette = UC.palette
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(tint.copy(alpha = if (palette.isDark) 0.18f else 0.11f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(formatIcon(family), null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun FormatBadge(text: String, color: Color = UC.palette.ember, solid: Boolean = false) {
    val palette = UC.palette
    val background = if (solid) color else color.copy(alpha = if (palette.isDark) 0.18f else 0.1f)
    val foreground = if (solid) palette.paper else color
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 0.6.sp),
        color = foreground,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun SectionEyebrow(text: String, color: Color = UC.palette.muted) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontSize = 12.sp),
        color = color,
    )
}

@Composable
fun SectionHeader(title: String, caption: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val palette = UC.palette
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = palette.ink)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = palette.muted)
        }
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = palette.ember,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun UCSwitchRow(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = UC.palette.ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = UC.palette.muted)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UCSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val palette = UC.palette
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.ivory,
        contentColor = palette.ink,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(palette.line)
            )
        },
    ) {
        Column(Modifier.navigationBarsPadding()) { content() }
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun OrbitDecoration(modifier: Modifier = Modifier, tint: Color = UC.palette.ember, dark: Boolean = false) {
    val palette = UC.palette
    val transition = rememberInfiniteTransition(label = "orbit")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(42000, easing = LinearEasing), RepeatMode.Restart),
    )
    androidx.compose.foundation.Canvas(modifier) {
        val center = Offset(size.width * 0.52f, size.height * 0.5f)
        val lineColor = if (dark) palette.paper.copy(alpha = 0.12f) else palette.ink.copy(alpha = 0.11f)
        val radii = listOf(size.minDimension * 0.23f, size.minDimension * 0.34f, size.minDimension * 0.44f)
        radii.forEach { radius ->
            drawCircle(color = lineColor, radius = radius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        for (i in 0..2) {
            val angle = Math.toRadians((rotation + i * 120.0).toDouble())
            val radius = radii[i % radii.size]
            val x = center.x + (radius * kotlin.math.cos(angle)).toFloat()
            val y = center.y + (radius * kotlin.math.sin(angle)).toFloat()
            drawCircle(color = tint.copy(alpha = if (i == 0) 0.95f else 0.55f), radius = if (i == 0) 3.5.dp.toPx() else 2.dp.toPx(), center = Offset(x, y))
        }
        drawCircle(
            color = tint.copy(alpha = 0.14f),
            radius = size.minDimension * 0.13f,
            center = center,
        )
        drawCircle(color = tint, radius = 2.dp.toPx(), center = center)
    }
}

@Composable
fun AsyncThumbnail(file: File, family: FormatFamily, modifier: Modifier = Modifier, sizePx: Int = 320, rounded: Dp = 16.dp) {
    val context = LocalContext.current
    val loader = (context.applicationContext as? ConverterApp)?.container?.thumbnails
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, file.absolutePath, file.lastModified(), sizePx) {
        value = withContext(Dispatchers.IO) { loader?.load(file, sizePx) }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(rounded))
            .background(UC.palette.parchment),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            FamilyMark(family, size = 48.dp)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    val palette = UC.palette
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(palette.parchment),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = palette.ember, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = palette.ink)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
    }
}