package com.universalconverter.pro.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GifBox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalconverter.pro.core.formats.FormatFamily
import com.universalconverter.pro.ui.MainViewModel
import com.universalconverter.pro.ui.components.AccentControl
import com.universalconverter.pro.ui.components.FamilyMark
import com.universalconverter.pro.ui.components.PaperBackground
import com.universalconverter.pro.ui.components.PureBlackControl
import com.universalconverter.pro.ui.components.SectionEyebrow
import com.universalconverter.pro.ui.components.ThemeModeControl
import com.universalconverter.pro.ui.components.UCPrimaryButton
import com.universalconverter.pro.ui.components.UCWordmark
import com.universalconverter.pro.ui.theme.UC
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

@Composable
fun OnboardingScreen(viewModel: MainViewModel, onDone: () -> Unit) {
    val palette = UC.palette
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == PAGE_COUNT - 1

    PaperBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UCWordmark(compact = true)
                Spacer(Modifier.weight(1f))
                Text(
                    "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.ember,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDone)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> StudioPage()
                    else -> ThemePage(
                        selectedMode = settings.themeMode,
                        selectedAccent = settings.accentTheme,
                        pureBlack = settings.pureBlack,
                        onMode = viewModel::setTheme,
                        onAccent = viewModel::setAccentTheme,
                        onPureBlack = viewModel::setPureBlack,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(PAGE_COUNT) { index ->
                        Box(
                            Modifier
                                .width(if (index == pagerState.currentPage) 22.dp else 7.dp)
                                .height(7.dp)
                                .clip(CircleShape)
                                .background(if (index == pagerState.currentPage) palette.ember else palette.line),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                UCPrimaryButton(
                    if (lastPage) "Start converting" else "Continue",
                    onClick = {
                        if (lastPage) onDone() else scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier.width(210.dp),
                    icon = Icons.Rounded.ArrowForward,
                )
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    val palette = UC.palette
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionEyebrow("Welcome · 01")
        Text(
            buildAnnotatedString {
                append("Every format.\nOne ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("quiet") }
                append(" studio.")
            },
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 42.sp),
            color = palette.ink,
        )
        Text(
            "3D, CAD, RAW, images, PDF, docs, audio, video and archives — converted on this phone. No uploads, no accounts.",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.muted,
        )
        Spacer(Modifier.weight(1f))
        FormatMatrix()
        Spacer(Modifier.height(10.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatMatrix() {
    val palette = UC.palette
    val families = listOf(
        FormatFamily.MESH to "3D",
        FormatFamily.IMAGE to "Images",
        FormatFamily.VECTOR to "Vector",
        FormatFamily.PDF to "PDF",
        FormatFamily.DOCUMENT to "Docs",
        FormatFamily.AUDIO to "Audio",
        FormatFamily.VIDEO to "Video",
        FormatFamily.ARCHIVE to "Archive",
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
    ) {
        families.forEach { (family, label) ->
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.ivory.copy(alpha = 0.85f))
                    .border(1.dp, palette.line, RoundedCornerShape(16.dp))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FamilyMark(family, size = 34.dp)
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = palette.ink, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun StudioPage() {
    val palette = UC.palette
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionEyebrow("Inspect · 02")
        Text(
            buildAnnotatedString {
                append("Look inside\nyour ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("models.") }
            },
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 42.sp),
            color = palette.ink,
        )
        Text(
            "A real OpenGL viewport for GLB, glTF, OBJ and STL files. Orbit, score print readiness and export a turntable GIF.",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.muted,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 190.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(palette.ink),
        ) {
            IsometricCube()
            Column(
                Modifier.align(Alignment.TopStart).padding(18.dp),
            ) {
                SectionEyebrow("Live viewport", color = palette.paper.copy(alpha = 0.55f))
                Text("Print-readiness 100", style = MaterialTheme.typography.titleMedium, color = palette.paper)
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StudioFeature(Icons.Rounded.ViewInAr, "Orbit")
                StudioFeature(Icons.Rounded.CheckCircle, "Print score")
                StudioFeature(Icons.Rounded.GifBox, "GIF")
            }
        }
    }
}

@Composable
private fun StudioFeature(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    val palette = UC.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = palette.ember, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = palette.paper.copy(alpha = 0.75f))
    }
}

@Composable
private fun IsometricCube() {
    val palette = UC.palette
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width * 0.7f
        val cy = size.height * 0.48f
        val unit = size.minDimension * 0.22f
        val ring = palette.ember.copy(alpha = 0.18f)
        drawCircle(ring, radius = unit * 2.3f, center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
        drawCircle(palette.paper.copy(alpha = 0.08f), radius = unit * 1.7f, center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
        val top = Path().apply {
            moveTo(cx, cy - unit)
            lineTo(cx + unit * 0.9f, cy - unit * 0.5f)
            lineTo(cx, cy)
            lineTo(cx - unit * 0.9f, cy - unit * 0.5f)
            close()
        }
        val left = Path().apply {
            moveTo(cx - unit * 0.9f, cy - unit * 0.5f)
            lineTo(cx, cy)
            lineTo(cx, cy + unit)
            lineTo(cx - unit * 0.9f, cy + unit * 0.5f)
            close()
        }
        val right = Path().apply {
            moveTo(cx + unit * 0.9f, cy - unit * 0.5f)
            lineTo(cx, cy)
            lineTo(cx, cy + unit)
            lineTo(cx + unit * 0.9f, cy + unit * 0.5f)
            close()
        }
        drawPath(top, palette.gold.copy(alpha = 0.9f))
        drawPath(left, palette.ember.copy(alpha = 0.7f))
        drawPath(right, palette.ember)
    }
}

@Composable
private fun ThemePage(
    selectedMode: com.universalconverter.pro.data.ThemeMode,
    selectedAccent: com.universalconverter.pro.data.AccentTheme,
    pureBlack: Boolean,
    onMode: (com.universalconverter.pro.data.ThemeMode) -> Unit,
    onAccent: (com.universalconverter.pro.data.AccentTheme) -> Unit,
    onPureBlack: (Boolean) -> Unit,
) {
    val palette = UC.palette
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionEyebrow("Your studio · 03")
        Text(
            buildAnnotatedString {
                append("Make it feel\nlike ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("yours.") }
            },
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 42.sp),
            color = palette.ink,
        )
        Text(
            "Choose a theme now. You can change it any time in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.muted,
        )
        Spacer(Modifier.height(2.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(palette.ivory.copy(alpha = 0.9f))
                .border(1.dp, palette.line, RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleSmall, color = palette.ink)
            ThemeModeControl(selected = selectedMode, onSelect = onMode)
            AccentControl(selected = selectedAccent, onSelect = onAccent)
            PureBlackControl(enabled = pureBlack, onToggle = onPureBlack)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.parchment.copy(alpha = 0.65f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, null, tint = palette.sage, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Conversions stay on-device. Optional ads keep the studio free and never see your files.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
    }
}
