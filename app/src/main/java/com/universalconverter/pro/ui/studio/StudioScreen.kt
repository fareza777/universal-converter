package com.universalconverter.pro.ui.studio

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GifBox
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.universalconverter.pro.ConverterApp
import com.universalconverter.pro.core.detect.FormatDetector
import com.universalconverter.pro.core.formats.FormatFamily
import com.universalconverter.pro.core.formats.FormatRegistry
import com.universalconverter.pro.core.mesh.Mesh
import com.universalconverter.pro.core.mesh.MeshAnalyzer
import com.universalconverter.pro.core.mesh.MeshHealth
import com.universalconverter.pro.core.mesh.MeshIO
import com.universalconverter.pro.core.mesh.MeshRenderOptions
import com.universalconverter.pro.core.mesh.MeshRenderer
import com.universalconverter.pro.core.mesh.MeshTurntable
import com.universalconverter.pro.data.LibraryItem
import com.universalconverter.pro.ui.MainViewModel
import com.universalconverter.pro.ui.components.SectionEyebrow
import com.universalconverter.pro.ui.components.UCSheet
import com.universalconverter.pro.ui.formatBytes
import com.universalconverter.pro.ui.theme.UC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val studioInk = Color(0xFF14120F)
private val studioPaper = Color(0xFFF5F0E7)
private val studioMuted = Color(0xFFA9A093)
private val studioEmber = Color(0xFFE0764F)
private val studioLine = Color(0x22FFFFFF)
private val studioSurface = Color(0xF0141311)

@Composable
fun StudioScreen(
    viewModel: MainViewModel,
    filePath: String?,
    onBack: () -> Unit,
    onOpenPath: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onConvertFile: (File) -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as ConverterApp).container
    val vault = container.vault
    val scope = rememberCoroutineScope()
    val glesSupported = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo.reqGlEsVersion >= 0x30000
    }
    val glView = remember(context, glesSupported) { if (glesSupported) MeshGLSurfaceView(context) else null }
    DisposableEffect(glView) {
        glView?.onResume()
        onDispose { glView?.onPause() }
    }

    var mesh by remember { mutableStateOf<Mesh?>(null) }
    var health by remember { mutableStateOf<MeshHealth?>(null) }
    var loading by remember { mutableStateOf(true) }
    var healthLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeFile by remember { mutableStateOf<File?>(null) }
    var isDemo by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(StudioRenderMode.CLAY) }
    var grid by remember { mutableStateOf(true) }
    var autoRotate by remember { mutableStateOf(true) }
    var showDetails by remember { mutableStateOf(false) }
    var showBrowse by remember { mutableStateOf(false) }
    var browseItems by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
    var exportResult by remember { mutableStateOf<File?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportLabel by remember { mutableStateOf("") }
    val exportProgressFlow = remember { MutableStateFlow(0f) }
    val exportProgress by exportProgressFlow.collectAsState()
    var fallbackBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val imported = withContext(Dispatchers.IO) { vault.importUri(uri) }
                    val detection = withContext(Dispatchers.IO) { FormatDetector.detect(imported.file, imported.displayName) }
                    if (detection.format?.family == FormatFamily.MESH) {
                        onOpenPath(imported.file.absolutePath)
                    } else {
                        viewModel.showMessage("That file is not a supported 3D model.")
                    }
                } catch (exception: Exception) {
                    viewModel.showMessage(exception.message ?: "The model could not be imported.")
                }
            }
        }
    }

    LaunchedEffect(filePath) {
        loading = true
        error = null
        health = null
        healthLoading = false
        fallbackBitmap = null
        try {
            if (filePath.isNullOrBlank()) {
                val sample = withContext(Dispatchers.Default) { SampleModels.torus() }
                mesh = sample
                activeFile = null
                isDemo = true
                loading = false
                healthLoading = true
                health = withContext(Dispatchers.Default) { MeshAnalyzer.analyze(sample) }
                healthLoading = false
            } else {
                val file = File(filePath)
                if (!file.isFile) throw IllegalArgumentException("That model is no longer in the vault.")
                val spec = FormatRegistry.findForFileName(file.name)
                if (spec?.family != FormatFamily.MESH) throw IllegalArgumentException("This file is not a supported 3D model.")
                val loaded = withContext(Dispatchers.IO) { MeshIO.read(file, spec.id) }
                mesh = loaded.mesh
                activeFile = file
                isDemo = false
                loading = false
                healthLoading = true
                health = withContext(Dispatchers.Default) { MeshAnalyzer.analyze(loaded.mesh) }
                healthLoading = false
            }
        } catch (exception: Exception) {
            loading = false
            healthLoading = false
            error = exception.message ?: "The model could not be loaded."
            if (mesh == null) {
                val sample = withContext(Dispatchers.Default) { SampleModels.torus() }
                mesh = sample
                isDemo = true
            }
        }
    }

    LaunchedEffect(showBrowse) {
        if (showBrowse) {
            browseItems = withContext(Dispatchers.IO) {
                (vault.listVault() + vault.listInbox()).filter { item ->
                    !item.isDirectory && FormatRegistry.findForFileName(item.displayName)?.family == FormatFamily.MESH
                }
            }
        }
    }

    LaunchedEffect(glesSupported, mesh) {
        if (!glesSupported && mesh != null) {
            val target = mesh!!
            fallbackBitmap = withContext(Dispatchers.Default) {
                val pixels = MeshRenderer.render(target, MeshRenderOptions(width = 900, height = 1400, supersample = 1))
                Bitmap.createBitmap(pixels, 900, 1400, Bitmap.Config.ARGB_8888)
            }
        }
    }

    fun exportGif() {
        val model = mesh ?: return
        if (exporting) return
        exporting = true
        exportProgressFlow.value = 0f
        exportLabel = "Rendering turntable"
        scope.launch {
            try {
                val base = activeFile?.nameWithoutExtension ?: "torus"
                val output = vault.uniqueFile(vault.vaultDir, "$base-turntable.gif")
                withContext(Dispatchers.Default) {
                    MeshTurntable.exportGif(model, output, frames = 28, size = 480, fps = 12) { fraction ->
                        exportProgressFlow.value = fraction
                    }
                }
                viewModel.refreshLibrary()
                exportResult = output
            } catch (exception: Exception) {
                viewModel.showMessage(exception.message ?: "The turntable could not be rendered.")
            } finally {
                exporting = false
            }
        }
    }

    fun exportStill() {
        val model = mesh ?: return
        if (exporting) return
        exporting = true
        exportProgressFlow.value = 0.2f
        exportLabel = "Rendering still"
        scope.launch {
            try {
                val base = activeFile?.nameWithoutExtension ?: "torus"
                val output = vault.uniqueFile(vault.vaultDir, "$base-studio.png")
                withContext(Dispatchers.Default) {
                    val pixels = MeshRenderer.render(
                        model,
                        MeshRenderOptions(width = 1200, height = 1200, supersample = 1, azimuthDegrees = 34f),
                    )
                    val bitmap = Bitmap.createBitmap(pixels, 1200, 1200, Bitmap.Config.ARGB_8888)
                    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    exportProgressFlow.value = 1f
                }
                viewModel.refreshLibrary()
                exportResult = output
            } catch (exception: Exception) {
                viewModel.showMessage(exception.message ?: "The still could not be rendered.")
            } finally {
                exporting = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(studioInk)) {
        if (glesSupported && glView != null) {
            AndroidView(
                factory = { glView },
                modifier = Modifier.fillMaxSize(),
                update = { view -> view.setScene(mesh, mode, grid, autoRotate) },
            )
        } else {
            val bitmap = fallbackBitmap
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = "3D preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = studioEmber)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(320.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
        )

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudioIconButton(Icons.Rounded.Close, "Back", onBack)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        activeFile?.name ?: "Torus study",
                        style = MaterialTheme.typography.titleSmall,
                        color = studioPaper,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (isDemo) "DEMO MODEL" else (activeFile?.let { FormatRegistry.findForFileName(it.name)?.displayName } ?: "3D MODEL"),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                        color = studioMuted,
                    )
                }
                StudioIconButton(Icons.Rounded.AddPhotoAlternate, "Open model") { picker.launch(arrayOf("*/*")) }
                Spacer(Modifier.width(8.dp))
                StudioIconButton(Icons.Rounded.FolderOpen, "Browse models") { showBrowse = true }
            }
            Spacer(Modifier.weight(1f))
            if (isDemo && mesh != null && error == null) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x66000000))
                        .border(1.dp, studioLine, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text("Drag to orbit · pinch to zoom", style = MaterialTheme.typography.labelSmall, color = studioMuted)
                }
                Spacer(Modifier.height(10.dp))
            }
            if (!glesSupported) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Interactive preview needs OpenGL ES 3.0", style = MaterialTheme.typography.labelSmall, color = studioMuted)
                }
                Spacer(Modifier.height(8.dp))
            }
            ModelToolbar(
                mode = mode,
                grid = grid,
                autoRotate = autoRotate,
                onMode = { mode = it; glView?.setMode(it) },
                onGrid = { grid = !grid; glView?.setGrid(!grid) },
                onAutoRotate = { autoRotate = !autoRotate; glView?.setAutoRotate(!autoRotate) },
                onReset = { glView?.resetCamera() },
            )
            Spacer(Modifier.height(10.dp))
            ModelPanel(
                modifier = Modifier.navigationBarsPadding(),
                fileName = activeFile?.name ?: "Torus study",
                isDemo = isDemo,
                mesh = mesh,
                health = health,
                healthLoading = healthLoading,
                fileSize = activeFile?.length() ?: 0L,
                error = error,
                mode = mode,
                onDetails = { showDetails = true },
                onGif = { exportGif() },
                onStill = { exportStill() },
                onConvert = { activeFile?.let(onConvertFile) },
                onChoose = { picker.launch(arrayOf("*/*")) },
                onBrowse = { showBrowse = true },
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = studioEmber)
                    Spacer(Modifier.height(12.dp))
                    Text("Reading geometry…", style = MaterialTheme.typography.bodySmall, color = studioMuted)
                }
            }
        }

        if (exporting) {
            Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(studioSurface)
                        .border(1.dp, studioLine, RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        progress = { exportProgress },
                        color = studioEmber,
                        trackColor = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(exportLabel, style = MaterialTheme.typography.titleSmall, color = studioPaper)
                    Text("${(exportProgress * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = studioMuted)
                }
            }
        }
    }

    if (showDetails) {
        health?.let { detail ->
            MeshDetailsSheet(detail, onDismiss = { showDetails = false })
        }
    }
    if (showBrowse) {
        UCSheet(onDismiss = { showBrowse = false }) {
            Column(Modifier.fillMaxHeight(0.8f)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionEyebrow("Your models")
                        Text("Choose a 3D file", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (browseItems.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No mesh files in the vault yet.", style = MaterialTheme.typography.bodyMedium, color = UC.palette.muted)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudioSheetAction("Import file", Icons.Rounded.AddPhotoAlternate) { picker.launch(arrayOf("*/*")) }
                            StudioSheetAction("Library", Icons.Rounded.PhotoLibrary) { onOpenLibrary(); showBrowse = false }
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(browseItems, key = { it.file.absolutePath }) { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(UC.palette.paper)
                                    .clickable { showBrowse = false; onOpenPath(item.file.absolutePath) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.ViewInAr, null, tint = UC.palette.ember, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.displayName, style = MaterialTheme.typography.titleSmall, color = UC.palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatBytes(item.sizeBytes), style = MaterialTheme.typography.bodySmall, color = UC.palette.muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    exportResult?.let { file ->
        ExportResultSheet(
            file = file,
            onDismiss = { exportResult = null },
            onShare = {
                vault.shareIntent(file)?.let { intent ->
                    try { context.startActivity(Intent.createChooser(intent, "Share ${file.name}")) }
                    catch (_: Exception) { viewModel.showMessage("No app can share this file.") }
                }
            },
            onSave = {
                val saved = vault.exportToDownloads(file)
                viewModel.showMessage(if (saved != null) "Saved to Downloads/Universal Converter" else "Saving to Downloads needs Android 10 or newer.")
            },
            onLibrary = { exportResult = null; onOpenLibrary() },
        )
    }
}

@Composable
private fun StudioIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x66000000))
            .border(1.dp, studioLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = studioPaper, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ModelToolbar(
    mode: StudioRenderMode,
    grid: Boolean,
    autoRotate: Boolean,
    onMode: (StudioRenderMode) -> Unit,
    onGrid: () -> Unit,
    onAutoRotate: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0x99000000))
                .border(1.dp, studioLine, RoundedCornerShape(15.dp))
                .padding(3.dp)
        ) {
            listOf(
                StudioRenderMode.CLAY to "Clay",
                StudioRenderMode.SURFACE to "Color",
                StudioRenderMode.WIRE to "Wire",
            ).forEach { (renderMode, label) ->
                val selected = mode == renderMode
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) studioInk else studioMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) studioPaper else Color.Transparent)
                        .clickable { onMode(renderMode) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        StudioToolButton(if (autoRotate) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (autoRotate) "Pause orbit" else "Auto orbit", onAutoRotate)
        StudioToolButton(Icons.Rounded.GridOn, "Grid", onGrid, active = grid)
        StudioToolButton(Icons.Rounded.Refresh, "Reset view", onReset)
    }
}

@Composable
private fun StudioToolButton(icon: ImageVector, description: String, onClick: () -> Unit, active: Boolean = false) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) studioPaper else Color(0x99000000))
            .border(1.dp, if (active) Color.Transparent else studioLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (active) studioInk else studioMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ModelPanel(
    modifier: Modifier,
    fileName: String,
    isDemo: Boolean,
    mesh: Mesh?,
    health: MeshHealth?,
    healthLoading: Boolean,
    fileSize: Long,
    error: String?,
    mode: StudioRenderMode,
    onDetails: () -> Unit,
    onGif: () -> Unit,
    onStill: () -> Unit,
    onConvert: () -> Unit,
    onChoose: () -> Unit,
    onBrowse: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(studioSurface)
            .border(1.dp, studioLine, RoundedCornerShape(26.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MeshScore(health?.score, healthLoading)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                SectionEyebrow(if (isDemo) "Demo model" else "Mesh engine", color = studioMuted)
                Spacer(Modifier.height(3.dp))
                Text(fileName, style = MaterialTheme.typography.titleSmall, color = studioPaper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val triangles = mesh?.triangleCount ?: 0
                    Text(
                        "${formatCount(triangles)} tris · ${formatCount(mesh?.vertexCount ?: 0)} verts${if (fileSize > 0) " · ${formatBytes(fileSize)}" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = studioMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (health != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (health.printReady) Color(0x224C5C49) else Color(0x22B98A2F))
                        .clickable(onClick = onDetails)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (health.printReady) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                        null,
                        tint = if (health.printReady) Color(0xFFA4B49B) else Color(0xFFD4AE63),
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (health.printReady) "Print ready" else "Inspect",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 0.4.sp),
                        color = if (health.printReady) Color(0xFFA4B49B) else Color(0xFFD4AE63),
                    )
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x22A33A2E)).padding(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.Warning, null, tint = Color(0xFFF09A8C), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(7.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFF09A8C))
            }
        }
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StudioAction(Icons.Rounded.GifBox, "Turntable GIF", onGif, primary = true, enabled = mesh != null)
            StudioAction(Icons.Rounded.CameraAlt, "Still", onStill, enabled = mesh != null)
            if (!isDemo) {
                StudioAction(Icons.Rounded.ViewInAr, "Convert", onConvert)
            } else {
                StudioAction(Icons.Rounded.FolderOpen, "Browse", onBrowse)
            }
        }
    }
}

@Composable
private fun MeshScore(score: Int?, loading: Boolean) {
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawCircle(color = Color.White.copy(alpha = 0.08f), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            if (score != null) {
                drawArc(
                    color = studioEmber,
                    startAngle = -90f,
                    sweepAngle = score * 3.6f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = studioEmber, strokeWidth = 2.dp)
        else Text(score?.toString() ?: "—", style = MaterialTheme.typography.titleSmall, color = studioPaper)
    }
}

@Composable
private fun StudioAction(icon: ImageVector, label: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true) {
    Row(
        Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (primary) studioPaper else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (primary) Color.Transparent else studioLine, RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (primary) studioInk else studioMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = if (primary) studioInk else studioMuted,
        )
    }
}

@Composable
private fun StudioSheetAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(UC.palette.parchment)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = UC.palette.ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = UC.palette.ink)
    }
}

@Composable
private fun MeshDetailsSheet(health: MeshHealth, onDismiss: () -> Unit) {
    val palette = UC.palette
    UCSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxHeight(0.88f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mesh prescription", style = MaterialTheme.typography.headlineMedium, color = palette.ink, modifier = Modifier.weight(1f))
                Text("SCORE ${health.score}", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp), color = palette.ember)
            }
            Text(
                if (health.printReady) "This mesh is watertight, manifold and ready for a 3D printer." else "The model is viewable, but a print would need repairs.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MeshMetric("Triangles", formatCount(health.triangleCount), Modifier.weight(1f))
                MeshMetric("Shells", health.shellCount.toString(), Modifier.weight(1f))
                MeshMetric("Score", "${health.score}/100", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MeshMetric("Open edges", health.boundaryEdges.toString(), Modifier.weight(1f))
                MeshMetric("Non-manifold", health.nonManifoldEdges.toString(), Modifier.weight(1f))
                MeshMetric("Degenerate", health.degenerateFaces.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Text("BOUNDS", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp), color = palette.muted)
            Spacer(Modifier.height(5.dp))
            Text(
                "${fmt(health.bounds.sizeX)} × ${fmt(health.bounds.sizeY)} × ${fmt(health.bounds.sizeZ)} units",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink,
            )
            Text(
                "Volume ${fmt(health.volume)} · surface ${fmt(health.surfaceArea)} · ${if (health.topologyChecked) "topology checked" else "topology skipped" }",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
            if (health.issues.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("NOTES", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp), color = palette.gold)
                Spacer(Modifier.height(6.dp))
                health.issues.forEach { issue ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Warning, null, tint = palette.gold, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(issue, style = MaterialTheme.typography.bodySmall, color = palette.muted)
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text("No watertightness or normal issues were found.", style = MaterialTheme.typography.bodySmall, color = palette.sage)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MeshMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(UC.palette.paper)
            .padding(10.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 0.8.sp), color = UC.palette.muted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = UC.palette.ink)
    }
}

@Composable
private fun ExportResultSheet(
    file: File,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onLibrary: () -> Unit,
) {
    val palette = UC.palette
    UCSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(palette.sage.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = palette.sage)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Studio export ready", style = MaterialTheme.typography.headlineSmall, color = palette.ink)
                    Text(file.name, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("${formatBytes(file.length())} · saved in your private vault", style = MaterialTheme.typography.bodySmall, color = palette.muted)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StudioSheetAction("Share", Icons.Rounded.Share, onShare)
                StudioSheetAction("Save copy", Icons.Rounded.Download, onSave)
                StudioSheetAction("Library", Icons.Rounded.PhotoLibrary, onLibrary)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun formatCount(value: Int): String = when {
    value >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(java.util.Locale.US, "%.0fk", value / 1_000.0)
    else -> value.toString()
}

private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)
private fun fmt(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)