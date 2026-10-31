package com.universalconverter.pro.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalconverter.pro.ConverterApp
import com.universalconverter.pro.core.formats.FormatFamily
import com.universalconverter.pro.core.formats.FormatRegistry
import com.universalconverter.pro.data.LibraryItem
import com.universalconverter.pro.ui.MainViewModel
import com.universalconverter.pro.ui.components.AsyncThumbnail
import com.universalconverter.pro.ui.components.EmptyState
import com.universalconverter.pro.ui.components.FamilyMark
import com.universalconverter.pro.ui.components.FormatBadge
import com.universalconverter.pro.ui.components.HairlineCard
import com.universalconverter.pro.ui.components.PaperBackground
import com.universalconverter.pro.ui.components.SquareIconButton
import com.universalconverter.pro.ui.components.UCPrimaryButton
import com.universalconverter.pro.ui.components.UCSheet
import com.universalconverter.pro.ui.components.UCTopBar
import com.universalconverter.pro.ui.formatBytes
import com.universalconverter.pro.ui.formatDate
import com.universalconverter.pro.ui.formatRelative
import com.universalconverter.pro.ui.theme.UC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class LibFilter(val label: String, val families: Set<FormatFamily>)

private val libraryFilters = listOf(
    LibFilter("All", emptySet()),
    LibFilter("3D", setOf(FormatFamily.MESH)),
    LibFilter("CAD", setOf(FormatFamily.CAD)),
    LibFilter("Images", setOf(FormatFamily.IMAGE, FormatFamily.RAW)),
    LibFilter("PDF", setOf(FormatFamily.PDF)),
    LibFilter("Docs", setOf(FormatFamily.DOCUMENT, FormatFamily.SPREADSHEET, FormatFamily.DATA)),
    LibFilter("Media", setOf(FormatFamily.AUDIO, FormatFamily.VIDEO)),
    LibFilter("Archives", setOf(FormatFamily.ARCHIVE)),
)

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    folderPath: String?,
    onBack: () -> Unit,
    onOpenFolder: (File) -> Unit,
    onOpenStudio: (File) -> Unit,
    onOpenConvert: () -> Unit,
) {
    val palette = UC.palette
    val context = LocalContext.current
    val container = (context.applicationContext as ConverterApp).container
    val vault = container.vault
    val library by viewModel.libraryItems.collectAsStateWithLifecycle()
    val inbox by viewModel.inboxItems.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val directory = remember(folderPath) { folderPath?.let(::File) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var folderItems by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<LibraryItem?>(null) }
    var preview by remember { mutableStateOf<File?>(null) }
    var renameTarget by remember { mutableStateOf<LibraryItem?>(null) }
    var deleteTarget by remember { mutableStateOf<LibraryItem?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importUris(uris, navigate = true)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.importTree(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshLibrary()
        viewModel.refreshInbox()
    }
    LaunchedEffect(directory, refreshTick, tab) {
        if (directory != null) {
            folderItems = withContext(Dispatchers.IO) { vault.listDirectory(directory) }
        } else if (tab == 1) {
            viewModel.refreshInbox()
        } else {
            viewModel.refreshLibrary()
        }
    }

    val rawItems = when {
        directory != null -> folderItems
        tab == 0 -> library
        else -> inbox
    }
    val activeFilter = libraryFilters[filterIndex.coerceIn(0, libraryFilters.lastIndex)]
    val items = rawItems.filter { item ->
        val family = if (item.isDirectory) FormatFamily.ARCHIVE else FormatRegistry.findForFileName(item.displayName)?.family
        val matchesFamily = activeFilter.families.isEmpty() || (family != null && family in activeFilter.families)
        val matchesQuery = query.isBlank() || item.displayName.contains(query, true)
        matchesFamily && matchesQuery
    }
    val title = if (directory != null) directory.name else "Library"
    val subtitle = if (directory != null) {
        "${items.size} item${if (items.size == 1) "" else "s"} in this folder"
    } else if (tab == 0) {
        "${library.size} result${if (library.size == 1) "" else "s"} · ${formatBytes(storageBytes)} in your private vault"
    } else {
        "${inbox.size} imported source${if (inbox.size == 1) "" else "s"}"
    }

    PaperBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(if (directory != null) Modifier.navigationBarsPadding() else Modifier)
        ) {
            UCTopBar(
                title = title,
                subtitle = subtitle,
                onBack = if (directory != null) onBack else null,
                backIcon = Icons.Rounded.ArrowBack,
                actions = {
                    SquareIconButton(Icons.Rounded.AddPhotoAlternate, "Import files", { filePicker.launch(arrayOf("*/*")) })
                    Spacer(Modifier.width(6.dp))
                    SquareIconButton(Icons.Rounded.FolderOpen, "Import folder", { folderPicker.launch(null) })
                },
            )
            if (directory == null) {
                StorageSummary(
                    bytes = storageBytes,
                    results = library.size,
                    imports = inbox.size,
                    tab = tab,
                    onTab = { tab = it },
                )
                Spacer(Modifier.height(12.dp))
            }
            SearchField(query) { query = it }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                libraryFilters.forEachIndexed { index, filter ->
                    FilterPill(filter.label, filterIndex == index) { filterIndex = index }
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                rawItems.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(
                                if (directory != null) Icons.Rounded.Folder else Icons.Rounded.PhotoLibrary,
                                if (tab == 1 && directory == null) "No imported sources" else "Your vault is quiet",
                                when {
                                    directory != null -> "This folder is empty."
                                    tab == 1 -> "Files you import are copied here for conversion."
                                    else -> "Converted files land here, private and easy to share."
                                },
                                Modifier.padding(horizontal = 32.dp),
                            )
                            Spacer(Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                UCPrimaryButton("Import files", { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.width(172.dp))
                                Box(
                                    Modifier
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .border(1.dp, palette.line, RoundedCornerShape(18.dp))
                                        .clickable(onClick = onOpenConvert)
                                        .padding(horizontal = 18.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Convert", style = MaterialTheme.typography.labelLarge, color = palette.ink)
                                }
                            }
                        }
                    }
                }
                items.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        EmptyState(Icons.Rounded.Search, "Nothing matches", "Try another search or filter.", Modifier.padding(32.dp))
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(152.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items, key = { it.file.absolutePath }) { item ->
                            LibraryCard(item) {
                                if (item.isDirectory) onOpenFolder(item.file) else detail = item
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { item ->
        LibraryDetailSheet(
            item = item,
            onDismiss = { detail = null },
            onOpen = {
                when {
                    item.isDirectory -> { detail = null; onOpenFolder(item.file) }
                    isMesh(item) -> { detail = null; onOpenStudio(item.file) }
                    isPreviewableImage(item) -> { detail = null; preview = item.file }
                    else -> {
                        val intent = viewModel.viewIntent(item)
                        if (intent != null) {
                            try { context.startActivity(intent) } catch (_: Exception) {
                                viewModel.showMessage("No app can open this file.")
                            }
                        } else viewModel.showMessage("This item cannot be opened.")
                    }
                }
            },
            onShare = {
                if (!item.isDirectory) {
                    viewModel.shareIntent(item)?.let { intent ->
                        try { context.startActivity(Intent.createChooser(intent, "Share ${item.displayName}")) }
                        catch (_: Exception) { viewModel.showMessage("No app can share this file.") }
                    }
                }
            },
            onSave = { if (!item.isDirectory) viewModel.exportToDownloads(item) },
            onConvert = {
                if (!item.isDirectory) {
                    detail = null
                    viewModel.addExistingFile(item.file)
                }
            },
            onRename = { detail = null; renameTarget = item },
            onDelete = { detail = null; deleteTarget = item },
        )
    }
    renameTarget?.let { item ->
        RenameDialog(
            initial = item.displayName,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.renameLibraryItem(item, newName)
                renameTarget = null
                refreshTick++
            },
        )
    }
    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${item.displayName}?") },
            text = { Text(if (item.isDirectory) "This removes the folder and everything inside it from your vault." else "This removes the file from your vault. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLibraryItem(item)
                    deleteTarget = null
                    refreshTick++
                }) { Text("Delete", color = palette.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
            containerColor = palette.ivory,
        )
    }
    preview?.let { file ->
        ImagePreviewDialog(file) { preview = null }
    }
}

@Composable
private fun StorageSummary(bytes: Long, results: Int, imports: Int, tab: Int, onTab: (Int) -> Unit) {
    val palette = UC.palette
    HairlineCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.parchment),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Storage, null, tint = palette.ink, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Private vault", style = MaterialTheme.typography.titleSmall, color = palette.ink)
                    Text("$results result${if (results == 1) "" else "s"} · $imports import${if (imports == 1) "" else "s"} · ${formatBytes(bytes)}", style = MaterialTheme.typography.bodySmall, color = palette.muted)
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.paper)
                        .padding(3.dp)
                ) {
                    TabPill("Results", tab == 0) { onTab(0) }
                    TabPill("Imports", tab == 1) { onTab(1) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val total = (results + imports).coerceAtLeast(1)
                repeat(12) { index ->
                    val filled = index < (results.toFloat() / total * 12).toInt().coerceAtLeast(if (results > 0) 1 else 0)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(if (filled) palette.ink else palette.line.copy(alpha = 0.6f))
                    )
                }
            }
        }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = UC.palette
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) palette.paper else palette.muted,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) palette.ink else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val palette = UC.palette
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 50.dp),
        placeholder = { Text("Search your vault", color = palette.muted, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = palette.muted, modifier = Modifier.size(18.dp)) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear", tint = palette.muted, modifier = Modifier.size(17.dp)) } }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.ember,
            unfocusedBorderColor = palette.line,
            focusedContainerColor = palette.ivory,
            unfocusedContainerColor = palette.ivory,
            cursorColor = palette.ember,
        ),
    )
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = UC.palette
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) palette.paper else palette.muted,
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) palette.ink else palette.ivory)
            .border(1.dp, if (selected) palette.ink else palette.line, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun LibraryCard(item: LibraryItem, onClick: () -> Unit) {
    val palette = UC.palette
    val family = if (item.isDirectory) FormatFamily.ARCHIVE else FormatRegistry.findForFileName(item.displayName)?.family
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.ivory)
            .border(1.dp, palette.line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.28f)) {
            if (item.isDirectory) {
                Box(Modifier.fillMaxSize().background(palette.parchment), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Folder, null, tint = palette.ember, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(5.dp))
                        Text("${item.childCount} items", style = MaterialTheme.typography.labelSmall, color = palette.muted)
                    }
                }
            } else {
                AsyncThumbnail(item.file, family ?: FormatFamily.DOCUMENT, Modifier.fillMaxSize(), sizePx = 360, rounded = 0.dp)
            }
            Box(
                Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(palette.ink.copy(alpha = 0.8f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    if (item.isDirectory) "FOLDER" else item.displayName.substringAfterLast('.', "").uppercase().ifBlank { "FILE" },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 0.7.sp),
                    color = palette.paper,
                )
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(item.displayName, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = palette.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                "${if (item.isDirectory) "${item.childCount} items" else formatBytes(item.sizeBytes)} · ${formatRelative(item.modifiedAt)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = palette.muted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LibraryDetailSheet(
    item: LibraryItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onConvert: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = UC.palette
    val family = if (item.isDirectory) FormatFamily.ARCHIVE else FormatRegistry.findForFileName(item.displayName)?.family
    UCSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (family != null) FamilyMark(family, size = 54.dp) else FormatBadge("FILE")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.displayName, style = MaterialTheme.typography.titleLarge, color = palette.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${if (item.isDirectory) "${item.childCount} items" else formatBytes(item.sizeBytes)} · ${formatDate(item.modifiedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = palette.muted) }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DetailAction(
                    icon = when {
                        item.isDirectory -> Icons.Rounded.FolderOpen
                        isMesh(item) -> Icons.Rounded.ViewInAr
                        isPreviewableImage(item) -> Icons.Rounded.PhotoLibrary
                        else -> Icons.Rounded.OpenInNew
                    },
                    label = when {
                        item.isDirectory -> "Open folder"
                        isMesh(item) -> "Studio"
                        isPreviewableImage(item) -> "Preview"
                        else -> "Open"
                    },
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
                if (!item.isDirectory) {
                    DetailAction(Icons.Rounded.Share, "Share", onShare, Modifier.weight(1f))
                    DetailAction(Icons.Rounded.Download, "Save copy", onSave, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DetailAction(Icons.Rounded.AddPhotoAlternate, "Convert", onConvert, Modifier.weight(1f), enabled = !item.isDirectory)
                DetailAction(Icons.Rounded.Edit, "Rename", onRename, Modifier.weight(1f))
                DetailAction(Icons.Rounded.Delete, "Delete", onDelete, Modifier.weight(1f), danger = true)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (item.isDirectory) "Stored inside your private vault. Sharing whole folders is disabled; share individual files instead." else "Stored inside your private vault. Nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val palette = UC.palette
    val background = when {
        primary -> palette.ink
        danger -> palette.error.copy(alpha = 0.09f)
        else -> palette.parchment
    }
    val foreground = when {
        primary -> palette.paper
        danger -> palette.error
        else -> palette.ink
    }
    Column(
        modifier = modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) background else background.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = foreground, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = foreground, maxLines = 1)
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val palette = UC.palette
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.ember, unfocusedBorderColor = palette.line),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }) { Text("Rename", color = palette.ember) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = palette.ivory,
    )
}

@Composable
private fun ImagePreviewDialog(file: File, onDismiss: () -> Unit) {
    val palette = UC.palette
    val context = LocalContext.current
    val loader = (context.applicationContext as ConverterApp).container.thumbnails
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { loader.load(file, 1800) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0A09))) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            } else {
                CircularProgressIndicator(color = palette.ember, modifier = Modifier.align(Alignment.Center))
            }
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(file.name, style = MaterialTheme.typography.titleSmall, color = palette.paper, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Close", tint = palette.paper)
                }
            }
        }
    }
}

private fun isMesh(item: LibraryItem): Boolean =
    !item.isDirectory && FormatRegistry.findForFileName(item.displayName)?.family == FormatFamily.MESH

private fun isPreviewableImage(item: LibraryItem): Boolean {
    val family = FormatRegistry.findForFileName(item.displayName)?.family
    return !item.isDirectory && (family == FormatFamily.IMAGE || family == FormatFamily.RAW)
}