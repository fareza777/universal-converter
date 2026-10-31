package com.universalconverter.pro.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalconverter.pro.core.formats.FormatRegistry
import com.universalconverter.pro.data.JobStatus
import com.universalconverter.pro.data.QueueItem
import com.universalconverter.pro.ui.MainViewModel
import com.universalconverter.pro.ui.components.EmptyState
import com.universalconverter.pro.ui.components.FamilyMark
import com.universalconverter.pro.ui.components.FormatBadge
import com.universalconverter.pro.ui.components.HairlineCard
import com.universalconverter.pro.ui.components.PaperBackground
import com.universalconverter.pro.ui.components.SectionEyebrow
import com.universalconverter.pro.ui.components.SquareIconButton
import com.universalconverter.pro.ui.components.UCTopBar
import com.universalconverter.pro.ui.formatBytes
import com.universalconverter.pro.ui.formatDuration
import com.universalconverter.pro.ui.theme.UC
import java.io.File

@Composable
fun QueueScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenFolder: (File) -> Unit,
    onOpenStudioFile: (File) -> Unit,
) {
    val palette = UC.palette
    val context = LocalContext.current
    val items by viewModel.queueItems.collectAsStateWithLifecycle()
    val active = items.filter { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }
    val finished = items.filter { it.finished }
    val running = items.firstOrNull { it.status == JobStatus.RUNNING }
    val done = items.count { it.status == JobStatus.DONE }
    val failed = items.count { it.status == JobStatus.FAILED }

    PaperBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            UCTopBar(
                title = "Conversions",
                subtitle = when {
                    active.isNotEmpty() -> "${active.size} active · $done done"
                    items.isNotEmpty() -> "$done done${if (failed > 0) " · $failed failed" else ""}"
                    else -> "The batch queue"
                },
                onBack = onBack,
                actions = {
                    if (active.isNotEmpty()) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.cancelAllQueue() }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text("Cancel all", style = MaterialTheme.typography.labelMedium, color = palette.ember)
                        }
                    }
                    if (finished.isNotEmpty()) {
                        SquareIconButton(Icons.Rounded.Delete, "Clear finished", { viewModel.clearFinishedQueue() }, tint = palette.muted)
                    }
                },
            )
            if (running != null) {
                HairlineCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), background = palette.ink) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bolt, null, tint = palette.ember, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            SectionEyebrow(running.stage, color = palette.paper.copy(alpha = 0.55f))
                            Spacer(Modifier.weight(1f))
                            Text("${(running.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = palette.paper)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(running.sourceName, style = MaterialTheme.typography.titleMedium, color = palette.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { running.progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                            color = palette.ember,
                            trackColor = palette.paper.copy(alpha = 0.14f),
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (items.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Rounded.Bolt,
                            "No conversions yet",
                            "Your batch will appear here with live progress and verification.",
                            Modifier.padding(top = 50.dp, start = 24.dp, end = 24.dp),
                        )
                    }
                }
                items(items, key = { it.id }) { item ->
                    QueueItemCard(
                        item = item,
                        onCancel = { viewModel.cancelQueueItem(item.id) },
                        onRetry = { viewModel.retryQueueItem(item.id) },
                        onDismiss = { viewModel.dismissQueueItem(item.id) },
                        onShare = {
                            viewModel.shareQueueOutput(item)?.let { intent ->
                                try {
                                    context.startActivity(Intent.createChooser(intent, "Share ${item.outcome?.output?.name ?: "file"}"))
                                } catch (_: Exception) {
                                    viewModel.showMessage("No app can share this file.")
                                }
                            }
                        },
                        onOpen = {
                            val output = item.outcome?.output
                            if (output != null) {
                                if (output.isDirectory) {
                                    onOpenFolder(output)
                                } else if (FormatRegistry.findForFileName(output.name)?.family == com.universalconverter.pro.core.formats.FormatFamily.MESH) {
                                    onOpenStudioFile(output)
                                } else {
                                    val intent = viewModel.viewQueueOutput(item)
                                    if (intent != null) {
                                        try { context.startActivity(intent) } catch (_: Exception) {
                                            viewModel.showMessage("No app can open this file.")
                                        }
                                    }
                                }
                            }
                        },
                        onSave = { viewModel.saveQueueOutput(item) },
                        onStudio = {
                            item.outcome?.output?.takeIf { it.isFile }?.let(onOpenStudioFile)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueItemCard(
    item: QueueItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onStudio: () -> Unit,
) {
    val palette = UC.palette
    val family = FormatRegistry.findForFileName(item.sourceName)?.family ?: FormatRegistry.byId(item.sourceFormatId)?.family
    val statusColor = when (item.status) {
        JobStatus.RUNNING -> palette.ember
        JobStatus.DONE -> palette.sage
        JobStatus.FAILED -> palette.error
        JobStatus.CANCELLED -> palette.muted
        JobStatus.QUEUED -> palette.gold
    }
    HairlineCard {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (family != null) {
                    FamilyMark(family, size = 40.dp)
                } else {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(palette.parchment))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.sourceName, style = MaterialTheme.typography.titleSmall, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.sourceFormatLabel, style = MaterialTheme.typography.labelSmall, color = palette.muted)
                        Text(" → ", style = MaterialTheme.typography.labelSmall, color = palette.muted)
                        FormatBadge(item.target.label, color = statusColor)
                    }
                }
                when (item.status) {
                    JobStatus.QUEUED, JobStatus.RUNNING -> SquareIconButton(Icons.Rounded.Close, "Cancel", onCancel, tint = palette.muted, background = palette.paper, border = false, size = 34.dp)
                    JobStatus.FAILED -> SquareIconButton(Icons.Rounded.Refresh, "Retry", onRetry, tint = palette.ember, background = palette.paper, border = false, size = 34.dp)
                    else -> SquareIconButton(Icons.Rounded.Close, "Dismiss", onDismiss, tint = palette.muted, background = palette.paper, border = false, size = 34.dp)
                }
            }
            when (item.status) {
                JobStatus.QUEUED, JobStatus.RUNNING -> {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.stage, style = MaterialTheme.typography.labelSmall, color = palette.muted, modifier = Modifier.weight(1f))
                        Text("${(item.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = palette.ember)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (item.status == JobStatus.QUEUED) 0f else item.progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape),
                        color = palette.ember,
                        trackColor = palette.line.copy(alpha = 0.5f),
                    )
                }
                JobStatus.DONE -> {
                    Spacer(Modifier.height(9.dp))
                    val outcome = item.outcome
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = palette.sage, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${formatBytes(outcome?.sizeBytes ?: 0)} · ${formatDuration(outcome?.durationMs ?: 0)} · ${outcome?.verification ?: "Verified"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.autoSaved) {
                        Text("Copy saved to Downloads/Universal Converter", style = MaterialTheme.typography.labelSmall, color = palette.sage)
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QueueAction(Icons.Rounded.Share, "Share", onShare)
                        QueueAction(if (item.outcome?.outputIsDirectory == true) Icons.Rounded.Folder else Icons.Rounded.OpenInNew, if (item.outcome?.outputIsDirectory == true) "Folder" else "Open", onOpen)
                        if (item.outcome?.outputIsDirectory != true) QueueAction(Icons.Rounded.Download, "Save", onSave)
                    }
                    val studioExtension = item.outcome?.output?.extension?.lowercase()
                    if (studioExtension in setOf("glb", "gltf", "obj", "stl")) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(palette.parchment)
                                .clickable(onClick = onStudio)
                                .padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.ViewInAr, null, tint = palette.ink, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Open in 3D Studio", style = MaterialTheme.typography.labelMedium, color = palette.ink)
                        }
                    }
                }
                JobStatus.FAILED -> {
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = palette.error, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(item.error ?: "Conversion failed.", style = MaterialTheme.typography.bodySmall, color = palette.error)
                    }
                }
                JobStatus.CANCELLED -> {
                    Spacer(Modifier.height(6.dp))
                    Text("Cancelled", style = MaterialTheme.typography.bodySmall, color = palette.muted)
                }
            }
        }
    }
}

@Composable
private fun QueueAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val palette = UC.palette
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(palette.parchment)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = palette.ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = palette.ink)
    }
}