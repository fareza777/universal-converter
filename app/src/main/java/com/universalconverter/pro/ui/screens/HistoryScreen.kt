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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalconverter.pro.ConverterApp
import com.universalconverter.pro.core.formats.FormatRegistry
import com.universalconverter.pro.data.HistoryEntry
import com.universalconverter.pro.ui.MainViewModel
import com.universalconverter.pro.ui.components.EmptyState
import com.universalconverter.pro.ui.components.FormatBadge
import com.universalconverter.pro.ui.components.HairlineCard
import com.universalconverter.pro.ui.components.PaperBackground
import com.universalconverter.pro.ui.components.SquareIconButton
import com.universalconverter.pro.ui.components.UCSheet
import com.universalconverter.pro.ui.components.UCTopBar
import com.universalconverter.pro.ui.formatBytes
import com.universalconverter.pro.ui.formatDate
import com.universalconverter.pro.ui.formatDuration
import com.universalconverter.pro.ui.formatRelative
import com.universalconverter.pro.ui.theme.UC
import java.io.File

@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onOpenFolder: (File) -> Unit,
    onOpenStudio: (File) -> Unit,
) {
    val palette = UC.palette
    val context = LocalContext.current
    val container = (context.applicationContext as ConverterApp).container
    val history by viewModel.history.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("all") }
    var detail by remember { mutableStateOf<HistoryEntry?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshHistory() }

    val filtered = when (filter) {
        "success" -> history.filter { it.succeeded }
        "failed" -> history.filterNot { it.succeeded }
        else -> history
    }

    PaperBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            UCTopBar(
                title = "History",
                subtitle = "Every conversion, kept privately.",
                actions = {
                    if (history.isNotEmpty()) {
                        SquareIconButton(Icons.Rounded.Delete, "Clear history", { confirmClear = true }, tint = palette.muted)
                    }
                },
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("all" to "All", "success" to "Converted", "failed" to "Failed").forEach { (key, label) ->
                    HistoryFilter(label, filter == key) { filter = key }
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Rounded.History,
                        if (history.isEmpty()) "No conversions yet" else "No entries here",
                        if (history.isEmpty()) "Every completed or failed conversion will appear with an auditable report." else "Try a different filter.",
                        Modifier.padding(horizontal = 32.dp),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        HistoryCard(entry) { detail = entry }
                    }
                }
            }
        }
    }

    detail?.let { entry ->
        HistoryDetailSheet(
            entry = entry,
            onDismiss = { detail = null },
            onRetry = { viewModel.retryHistory(entry); detail = null },
            onOpen = {
                val path = entry.outputPath
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        if (file.isDirectory) {
                            onOpenFolder(file)
                        } else if (FormatRegistry.findForFileName(file.name)?.family == com.universalconverter.pro.core.formats.FormatFamily.MESH) {
                            onOpenStudio(file)
                        } else {
                            val intent = container.vault.viewIntent(file)
                            if (intent != null) {
                                try { context.startActivity(intent) } catch (_: Exception) {
                                    viewModel.showMessage("No app can open this file.")
                                }
                            }
                        }
                    } else {
                        viewModel.showMessage("The output file is no longer in the vault.")
                    }
                }
                detail = null
            },
            onShare = {
                val path = entry.outputPath
                if (path != null) {
                    val intent = container.vault.shareIntent(File(path))
                    if (intent != null) {
                        try { context.startActivity(Intent.createChooser(intent, "Share output")) } catch (_: Exception) {
                            viewModel.showMessage("No app can share this file.")
                        }
                    }
                }
            },
            onDelete = {
                viewModel.deleteHistory(entry)
                detail = null
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("Converted files in the vault stay untouched. Only the reports are removed.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory(); confirmClear = false }) {
                    Text("Clear", color = palette.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
            containerColor = palette.ivory,
        )
    }
}

@Composable
private fun HistoryFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = UC.palette
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) palette.paper else palette.muted,
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) palette.ink else palette.ivory)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

@Composable
private fun HistoryCard(entry: HistoryEntry, onClick: () -> Unit) {
    val palette = UC.palette
    val success = entry.succeeded
    HairlineCard(onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (success) palette.sage.copy(alpha = 0.13f) else palette.error.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    null,
                    tint = if (success) palette.sage else palette.error,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.sourceName, style = MaterialTheme.typography.titleSmall, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FormatBadge(entry.sourceFormat.uppercase(), color = palette.muted)
                    Text(" → ", style = MaterialTheme.typography.labelSmall, color = palette.muted)
                    Text(entry.targetLabel, style = MaterialTheme.typography.labelSmall, color = palette.ink)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatRelative(entry.createdAt), style = MaterialTheme.typography.labelSmall, color = palette.muted)
                Text(
                    if (success) formatBytes(entry.outputSize) else "Failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (success) palette.muted else palette.error,
                )
            }
        }
    }
}

@Composable
private fun HistoryDetailSheet(
    entry: HistoryEntry,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = UC.palette
    val context = LocalContext.current
    UCSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxHeight(0.9f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (entry.succeeded) palette.sage.copy(alpha = 0.13f) else palette.error.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (entry.succeeded) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        null,
                        tint = if (entry.succeeded) palette.sage else palette.error,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Conversion X-ray", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp), color = palette.muted)
                    Text(entry.targetLabel, style = MaterialTheme.typography.headlineSmall, color = palette.ink)
                }
                TextButton(onClick = onDismiss) { Text("Close", color = palette.muted) }
            }
            Text(entry.sourceName, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
            Spacer(Modifier.height(16.dp))
            if (entry.succeeded) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.parchment)
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = palette.sage, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Output verified", style = MaterialTheme.typography.titleSmall, color = palette.ink)
                        Text(entry.verification, style = MaterialTheme.typography.bodySmall, color = palette.muted)
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.error.copy(alpha = 0.08f))
                        .padding(13.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = palette.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(entry.error ?: "The conversion failed.", style = MaterialTheme.typography.bodySmall, color = palette.error)
                }
            }
            Spacer(Modifier.height(16.dp))
            InfoGrid(entry)
            if (entry.steps.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("PIPELINE", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp), color = palette.muted)
                Spacer(Modifier.height(8.dp))
                entry.steps.forEach { step ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(palette.ember))
                        Spacer(Modifier.width(9.dp))
                        Text(step, style = MaterialTheme.typography.bodySmall, color = palette.ink)
                    }
                }
            }
            if (entry.warnings.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("NOTES", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp), color = palette.gold)
                Spacer(Modifier.height(8.dp))
                entry.warnings.forEach { warning ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Warning, null, tint = palette.gold, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(warning, style = MaterialTheme.typography.bodySmall, color = palette.muted)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.succeeded && entry.outputPath != null) {
                    HistoryAction(Icons.Rounded.ArrowOutward, if (entry.outputIsDirectory) "Open folder" else "Open", onOpen, primary = true)
                    HistoryAction(Icons.Rounded.Share, "Share", onShare)
                }
                HistoryAction(Icons.Rounded.Refresh, "Run again", onRetry)
                HistoryAction(Icons.Rounded.Delete, "Delete", onDelete, danger = true)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Reports are stored only in this app. Sources stay in your private inbox for retries.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = context
}

@Composable
private fun InfoGrid(entry: HistoryEntry) {
    val palette = UC.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoCell("Engine", entry.engine, Modifier.weight(1f))
            InfoCell("Duration", formatDuration(entry.durationMs), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoCell("Preset", entry.preset.lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
            InfoCell("Created", formatDate(entry.createdAt), Modifier.weight(1f))
        }
        if (entry.succeeded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoCell("Size", formatBytes(entry.outputSize), Modifier.weight(1f))
                InfoCell("SHA-256", entry.checksum?.take(16)?.plus("…") ?: "Not computed", Modifier.weight(1f), mono = true)
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = palette
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier, mono: Boolean = false) {
    val palette = UC.palette
    Column(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(palette.paper)
            .padding(11.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 1.sp), color = palette.muted)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default),
            color = palette.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HistoryAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    danger: Boolean = false,
) {
    val palette = UC.palette
    val background = when {
        primary -> palette.ink
        danger -> palette.error.copy(alpha = 0.08f)
        else -> palette.parchment
    }
    val tint = when {
        primary -> palette.paper
        danger -> palette.error
        else -> palette.ink
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}