package com.universalconverter.pro.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalconverter.pro.core.convert.TargetSpec
import com.universalconverter.pro.core.formats.FormatFamily
import com.universalconverter.pro.data.JobStatus
import com.universalconverter.pro.data.QueueItem
import com.universalconverter.pro.ui.DraftItem
import com.universalconverter.pro.ui.MainViewModel
import com.universalconverter.pro.ui.components.AsyncThumbnail
import com.universalconverter.pro.ui.components.FamilyMark
import com.universalconverter.pro.ui.components.FormatBadge
import com.universalconverter.pro.ui.components.HairlineCard
import com.universalconverter.pro.ui.components.OrbitDecoration
import com.universalconverter.pro.ui.components.PaperBackground
import com.universalconverter.pro.ui.components.SectionEyebrow
import com.universalconverter.pro.ui.components.SquareIconButton
import com.universalconverter.pro.ui.components.UCBottomBar
import com.universalconverter.pro.ui.components.UCGhostButton
import com.universalconverter.pro.ui.components.UCPrimaryButton
import com.universalconverter.pro.ui.components.UCWordmark
import com.universalconverter.pro.ui.components.UCSheet
import com.universalconverter.pro.ui.formatBytes
import com.universalconverter.pro.ui.formatIcon
import com.universalconverter.pro.ui.familyColor
import com.universalconverter.pro.ui.theme.UC
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ConvertScreen(
    viewModel: MainViewModel,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStudio: () -> Unit,
) {
    val palette = UC.palette
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val queue by viewModel.queueItems.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var targetSheetItem by remember { mutableStateOf<DraftItem?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var dragActive by remember { mutableStateOf(false) }
    var pendingConvert by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importUris(uris)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.importTree(uri)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.markNotificationsAsked()
        if (pendingConvert) {
            pendingConvert = false
            viewModel.convertAll()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshLibrary()
        viewModel.refreshInbox()
    }

    fun beginConvert() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !settings.notificationsAsked
        ) {
            pendingConvert = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.convertAll()
        }
    }

    val dropTarget = remember(viewModel) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragActive = false
                val clip = event.toAndroidDragEvent().clipData ?: return false
                val uris = buildList {
                    for (index in 0 until clip.itemCount) {
                        clip.getItemAt(index).uri?.let { if (it.scheme == "content" || it.scheme == "file") add(it) }
                    }
                }
                if (uris.isNotEmpty()) viewModel.importUris(uris)
                return uris.isNotEmpty()
            }

            override fun onEntered(event: DragAndDropEvent) { dragActive = true }
            override fun onExited(event: DragAndDropEvent) { dragActive = false }
            override fun onEnded(event: DragAndDropEvent) { dragActive = false }
        }
    }

    val activeQueue = queue.filter { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }

    PaperBackground(
        modifier = Modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event -> (event.toAndroidDragEvent().clipData?.itemCount ?: 0) > 0 },
                target = dropTarget,
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UCWordmark(compact = true)
                Spacer(Modifier.weight(1f))
                Box {
                    SquareIconButton(Icons.Rounded.Bolt, "Queue", onOpenQueue)
                    if (activeQueue.isNotEmpty()) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 2.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(palette.ember)
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                SquareIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings)
            }

            AnimatedVisibility(visible = state.importing, enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.importTotal > 0) "Importing ${state.imported} of ${state.importTotal}" else "Reading folder…",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.muted,
                        )
                        Spacer(Modifier.weight(1f))
                        Text("Copying into your vault", style = MaterialTheme.typography.labelSmall, color = palette.muted)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (state.importTotal > 0) state.imported.toFloat() / state.importTotal else 0.15f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(CircleShape),
                        color = palette.ember,
                        trackColor = palette.line.copy(alpha = 0.5f),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (activeQueue.isNotEmpty()) {
                    item { QueueProgressCard(activeQueue, onOpenQueue) }
                }
                if (state.draft.isEmpty()) {
                    item {
                        Column {
                            SectionEyebrow("Offline · private · versatile")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                buildAnnotatedString {
                                    append("Convert ")
                                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("anything.") }
                                    append("\nKeep everything.")
                                },
                                style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 42.sp),
                                color = palette.ink,
                            )
                            Spacer(Modifier.height(9.dp))
                            Text(
                                "3D, CAD, RAW, images, PDF, docs, media and archives — converted offline.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.muted,
                            )
                        }
                    }
                    item {
                        DropCard(
                            dragging = dragActive,
                            enabled = !state.importing,
                            onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                            onPickFolder = { folderPicker.launch(null) },
                        )
                    }
                    item { FamilyChips() }
                    item { StudioBanner(onOpenStudio) }
                } else {
                    item {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    SectionEyebrow("Batch ${state.draft.size.toString().padStart(2, '0')}")
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Your selection",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = palette.ink,
                                    )
                                }
                                Text(
                                    "Clear",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = palette.ember,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.clearDraft() }
                                        .padding(8.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Each file keeps its smart target. Tap a format to fine-tune, or apply one format to the whole compatible batch.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.muted,
                            )
                        }
                    }
                    items(state.draft, key = { it.id }) { item ->
                        DraftCard(
                            item = item,
                            onClickTarget = { targetSheetItem = item },
                            onRemove = { viewModel.removeDraft(item.id) },
                        )
                    }
                    item {
                        AddMoreCard(
                            onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                            onPickFolder = { folderPicker.launch(null) },
                            importing = state.importing,
                        )
                    }
                    item { OptionsSummaryCard(state, onOpen = { showOptions = true }) }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "These files are copied into the app vault before conversion. Nothing is uploaded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.muted,
                            )
                            PrivacyStrip()
                        }
                    }
                }
            }

            if (state.draft.isNotEmpty()) {
                RunBar(state, onRun = { beginConvert() })
            }
        }

        AnimatedVisibility(visible = dragActive, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.paper.copy(alpha = 0.94f))
                    .padding(24.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = palette.ember,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx())),
                            ),
                            cornerRadius = CornerRadius(28.dp.toPx()),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(palette.emberSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, null, tint = palette.ember, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Drop to import", style = MaterialTheme.typography.headlineMedium, color = palette.ink)
                    Text("Files are copied into your private vault.", style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                }
            }
        }

        targetSheetItem?.let { item ->
            FormatSheet(
                viewModel = viewModel,
                item = item,
                batchSize = state.draft.size,
                onDismiss = { targetSheetItem = null },
            )
        }
        if (showOptions) {
            OptionsSheet(
                state = state,
                viewModel = viewModel,
                onDismiss = { showOptions = false },
            )
        }
    }
}

@Composable
private fun DropCard(
    dragging: Boolean,
    enabled: Boolean,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
) {
    val palette = UC.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 168.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (dragging) palette.emberSoft else palette.ivory.copy(alpha = 0.9f))
            .drawBehind {
                drawRoundRect(
                    color = if (dragging) palette.ember else palette.line,
                    style = Stroke(
                        width = 1.4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx())),
                    ),
                    cornerRadius = CornerRadius(26.dp.toPx()),
                )
            }
            .clickable(enabled = enabled, onClick = onPickFiles)
            .padding(18.dp),
    ) {
        OrbitDecoration(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 210.dp),
            tint = palette.ember,
            dark = false,
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(palette.ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, tint = palette.paper, modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Drop files here", style = MaterialTheme.typography.headlineSmall, color = palette.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "From another app, or browse this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                        maxLines = 2,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                UCPrimaryButton(
                    "Choose files",
                    onPickFiles,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                )
                UCGhostButton("Folder", onPickFolder, enabled = enabled, icon = Icons.Rounded.Folder)
            }
        }
    }
}

@Composable
private fun DraftCard(item: DraftItem, onClickTarget: () -> Unit, onRemove: () -> Unit) {
    val palette = UC.palette
    val family = item.detection.format?.family ?: FormatFamily.DOCUMENT
    val target = item.target
    HairlineCard {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                AsyncThumbnail(item.file, family, Modifier.fillMaxSize(), sizePx = 220, rounded = 16.dp)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleSmall, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.detection.format?.displayName ?: "Unknown",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                    Text(" · ", style = MaterialTheme.typography.labelSmall, color = palette.muted)
                    Text(formatBytes(item.file.length()), style = MaterialTheme.typography.labelSmall, color = palette.muted)
                    if (!item.detection.verified) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Warning, null, tint = palette.gold, modifier = Modifier.size(12.dp))
                    }
                }
                if (item.detection.mismatch && item.detection.format != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Extension says ${item.detection.declared?.displayName ?: "?"} · detected ${item.detection.format.displayName}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = palette.gold,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(palette.parchment)
                        .clickable(onClick = onClickTarget)
                        .padding(start = 11.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        target?.label ?: "Choose",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = palette.ink,
                    )
                    Icon(Icons.Rounded.ChevronRight, null, tint = palette.muted, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap to change",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = palette.muted,
                )
            }
            Spacer(Modifier.width(4.dp))
            SquareIconButton(
                Icons.Rounded.Close,
                "Remove",
                onRemove,
                tint = palette.muted,
                background = Color.Transparent,
                border = false,
                size = 30.dp,
            )
        }
    }
}

@Composable
private fun AddMoreCard(onPickFiles: () -> Unit, onPickFolder: () -> Unit, importing: Boolean) {
    val palette = UC.palette
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .drawBehind {
                    drawRoundRect(
                        color = palette.line,
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                    )
                }
                .clickable(enabled = !importing, onClick = onPickFiles)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AddPhotoAlternate, null, tint = palette.ember, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add more files", style = MaterialTheme.typography.labelLarge, color = palette.ink)
            Spacer(Modifier.width(16.dp))
            Text("·", color = palette.muted)
            Spacer(Modifier.width(16.dp))
            Text(
                "Import folder",
                style = MaterialTheme.typography.labelLarge,
                color = palette.muted,
                modifier = Modifier.clickable(enabled = !importing, onClick = onPickFolder),
            )
        }
    }
}

@Composable
private fun OptionsSummaryCard(state: com.universalconverter.pro.ui.ConvertUiState, onOpen: () -> Unit) {
    val palette = UC.palette
    val options = state.options
    val families = state.draft.mapNotNull { it.target?.family }.toSet()
    val details = buildList {
        add(options.preset.title)
        if (families.any { it == FormatFamily.IMAGE || it == FormatFamily.RAW || it == FormatFamily.VECTOR || it == FormatFamily.PDF }) {
            add(if (options.imageMaxEdge > 0) "${options.imageMaxEdge}px" else "Original size")
            add("${options.imageQuality}% quality")
        }
        if (families.any { it == FormatFamily.AUDIO }) add("${options.audioBitrateKbps} kbps audio")
        if (families.any { it == FormatFamily.VIDEO }) add("${if (options.videoHeight > 0) "${options.videoHeight}p" else "Source"} video")
        if (families.any { it == FormatFamily.ARCHIVE }) add("Compression ${options.archiveLevel}")
        if (families.any { it == FormatFamily.MESH }) add("Binary mesh")
        if (families.any { it == FormatFamily.PDF }) add(options.pdfPage.label)
    }
    HairlineCard(onClick = onOpen) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(palette.emberSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Tune, null, tint = palette.ember, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Quality & options", style = MaterialTheme.typography.titleSmall, color = palette.ink)
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 2)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = palette.muted)
        }
    }
}

@Composable
private fun RunBar(state: com.universalconverter.pro.ui.ConvertUiState, onRun: () -> Unit) {
    val palette = UC.palette
    Surface(color = palette.ivory, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${state.draft.size} file${if (state.draft.size == 1) "" else "s"} ready",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.ink,
                    )
                    Text(
                        state.draft.mapNotNull { it.target?.extension?.uppercase() }.distinct().joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        maxLines = 1,
                    )
                }
                UCPrimaryButton(
                    "Convert",
                    onRun,
                    enabled = state.canConvert,
                    icon = Icons.Rounded.ArrowForward,
                    modifier = Modifier.width(180.dp),
                )
            }
        }
    }
}

@Composable
private fun QueueProgressCard(items: List<QueueItem>, onOpen: () -> Unit) {
    val palette = UC.palette
    val running = items.firstOrNull { it.status == JobStatus.RUNNING } ?: items.first()
    val progress = if (running.status == JobStatus.RUNNING) running.progress else 0f
    HairlineCard(onClick = onOpen, background = palette.ink) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(palette.ember))
                Spacer(Modifier.width(9.dp))
                Text(
                    if (running.status == JobStatus.QUEUED) "Waiting in queue" else running.stage,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                    color = palette.paper.copy(alpha = 0.64f),
                )
                Spacer(Modifier.weight(1f))
                Text("${items.size} active", style = MaterialTheme.typography.labelSmall, color = palette.paper.copy(alpha = 0.64f))
            }
            Spacer(Modifier.height(7.dp))
            Text(running.sourceName, style = MaterialTheme.typography.titleMedium, color = palette.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = palette.ember,
                trackColor = palette.paper.copy(alpha = 0.14f),
            )
        }
    }
}

@Composable
private fun StudioBanner(onOpenStudio: () -> Unit) {
    val palette = UC.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(palette.ink)
            .clickable(onClick = onOpenStudio)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(palette.emberSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ViewInAr, null, tint = palette.ember, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("3D Studio", style = MaterialTheme.typography.titleLarge, color = palette.paper)
            Spacer(Modifier.height(2.dp))
            Text(
                "Inspect print readiness · export a turntable GIF",
                style = MaterialTheme.typography.bodySmall,
                color = palette.paper.copy(alpha = 0.66f),
                maxLines = 2,
            )
        }
        Icon(Icons.Rounded.ArrowForward, null, tint = palette.ember, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PrivacyStrip() {
    val palette = UC.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.parchment.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Shield, null, tint = palette.sage, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "Conversions stay local. Nothing is uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyChips() {
    val palette = UC.palette
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
    ) {
        listOf("3D", "CAD", "RAW", "PDF", "Docs", "Audio", "Video", "Archives").forEach { label ->
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.ivory.copy(alpha = 0.72f))
                    .border(1.dp, palette.line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}