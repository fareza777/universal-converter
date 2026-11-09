package com.universalconverter.pro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.convert.PdfPageSize
import com.universalconverter.pro.core.convert.QualityPreset
import com.universalconverter.pro.core.formats.FormatFamily
import com.universalconverter.pro.ui.ConvertUiState
import com.universalconverter.pro.ui.MainViewModel
import com.universalconverter.pro.ui.components.SectionEyebrow
import com.universalconverter.pro.ui.components.UCPrimaryButton
import com.universalconverter.pro.ui.components.UCSwitchRow
import com.universalconverter.pro.ui.components.UCSheet
import com.universalconverter.pro.ui.theme.UC
import kotlin.math.roundToInt

@Composable
fun OptionsSheet(
    state: ConvertUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val palette = UC.palette
    val families = state.draft.mapNotNull { it.target?.family }.toSet()
    val hasJpg = state.draft.any { it.target?.id == "jpg" }
    val customPresets by viewModel.customPresets.collectAsStateWithLifecycle()
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    UCSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    SectionEyebrow("Quality")
                    Text("Conversion options", style = MaterialTheme.typography.headlineMedium, color = palette.ink)
                    Text("Applied to the current batch", style = MaterialTheme.typography.bodySmall, color = palette.muted)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Close", tint = palette.muted)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                PresetGrid(state.options.preset) { viewModel.setPreset(it) }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("My presets", style = MaterialTheme.typography.labelLarge, color = palette.ink, modifier = Modifier.weight(1f))
                    Text(
                        "Save current",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.ember,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                presetName = ""
                                showSaveDialog = true
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (customPresets.isEmpty()) {
                    Text(
                        "Save the current options as a reusable template for future batches.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        customPresets.forEach { preset ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.ivory)
                                    .border(1.dp, palette.line, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.applyCustomPreset(preset.id) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Bookmark, null, tint = palette.ember, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    preset.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.ink,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                if (state.draft.any { it.target?.id in setOf("jpg", "png", "webp", "tiff", "bmp", "gif", "pdf", "pdf_png", "pdf_jpg", "contact_sheet") }) {
                    Section("Image")
                    OptionChips(
                        label = "Long edge",
                        values = listOf(0 to "Original", 2048 to "2048 px", 1280 to "1280 px", 800 to "800 px"),
                        selected = state.options.imageMaxEdge,
                        onSelect = { value -> viewModel.updateOptions { it.copy(imageMaxEdge = value) } },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Quality", style = MaterialTheme.typography.labelLarge, color = palette.ink, modifier = Modifier.weight(1f))
                        Text("${state.options.imageQuality}%", style = MaterialTheme.typography.labelLarge, color = palette.ember)
                    }
                    Slider(
                        value = state.options.imageQuality.toFloat(),
                        onValueChange = { value -> viewModel.updateOptions { it.copy(imageQuality = value.roundToInt()) } },
                        valueRange = 50f..96f,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.ember,
                            activeTrackColor = palette.ember,
                            inactiveTrackColor = palette.line,
                        ),
                    )
                    if (hasJpg) {
                        UCSwitchRow(
                            "Keep capture metadata",
                            "Date, camera and GPS tags are copied to the JPEG.",
                            state.options.keepMetadata,
                        ) { enabled -> viewModel.updateOptions { it.copy(keepMetadata = enabled) } }
                    }
                }
                if (state.draft.any { it.target?.id in setOf("m4a", "wav", "mp4") }) {
                    Section("Sound")
                    OptionChips(
                        label = "Bitrate",
                        values = listOf(96 to "96k", 128 to "128k", 192 to "192k", 256 to "256k", 320 to "320k"),
                        selected = state.options.audioBitrateKbps,
                        onSelect = { value -> viewModel.updateOptions { it.copy(audioBitrateKbps = value) } },
                    )
                }
                if (state.draft.any { it.target?.id == "mp4" }) {
                    Section("Video")
                    OptionChips(
                        label = "Height",
                        values = listOf(0 to "Source", 480 to "480p", 720 to "720p", 1080 to "1080p"),
                        selected = state.options.videoHeight,
                        onSelect = { value -> viewModel.updateOptions { it.copy(videoHeight = value) } },
                    )
                    Spacer(Modifier.height(10.dp))
                    OptionChips(
                        label = "Video bitrate",
                        values = listOf(0 to "Auto", 1500 to "1.5M", 2800 to "2.8M", 5000 to "5M", 8000 to "8M"),
                        selected = state.options.videoBitrateKbps,
                        onSelect = { value -> viewModel.updateOptions { it.copy(videoBitrateKbps = value) } },
                    )
                }
                if (state.draft.any { it.target?.id == "gif" }) {
                    Section("GIF")
                    OptionChips(
                        label = "Width",
                        values = listOf(320 to "320", 480 to "480", 640 to "640"),
                        selected = state.options.gifWidth,
                        onSelect = { value -> viewModel.updateOptions { it.copy(gifWidth = value) } },
                    )
                    Spacer(Modifier.height(10.dp))
                    OptionChips(
                        label = "Frame rate",
                        values = listOf(8 to "8 fps", 12 to "12 fps", 15 to "15 fps"),
                        selected = state.options.gifFps,
                        onSelect = { value -> viewModel.updateOptions { it.copy(gifFps = value) } },
                    )
                }
                if (families.contains(FormatFamily.ARCHIVE)) {
                    Section("Archive")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Compression", style = MaterialTheme.typography.labelLarge, color = palette.ink, modifier = Modifier.weight(1f))
                        Text("Level ${state.options.archiveLevel}", style = MaterialTheme.typography.labelLarge, color = palette.ember)
                    }
                    Slider(
                        value = state.options.archiveLevel.toFloat(),
                        onValueChange = { value -> viewModel.updateOptions { it.copy(archiveLevel = value.roundToInt()) } },
                        valueRange = 1f..9f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.ember,
                            activeTrackColor = palette.ember,
                            inactiveTrackColor = palette.line,
                        ),
                    )
                }
                if (state.draft.any { it.target?.id == "pdf" || it.target?.formatId == "pdf" }) {
                    Section("PDF page")
                    OptionChips(
                        label = "Page size",
                        values = PdfPageSize.entries.map { it.ordinal to it.label },
                        selected = state.options.pdfPage.ordinal,
                        onSelect = { value -> viewModel.updateOptions { it.copy(pdfPage = PdfPageSize.entries[value]) } },
                    )
                }
                if (families.contains(FormatFamily.MESH)) {
                    Section("3D")
                    UCSwitchRow(
                        "Binary mesh output",
                        "Binary STL, PLY, GLB and glTF are smaller and faster.",
                        state.options.meshBinary,
                    ) { enabled -> viewModel.updateOptions { it.copy(meshBinary = enabled) } }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Reset to ${state.options.preset.title}",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.ember,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.setPreset(state.options.preset) }
                            .padding(8.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                UCPrimaryButton("Done", onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (showSaveDialog) {
        val palette = UC.palette
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save preset", style = MaterialTheme.typography.titleLarge, color = palette.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "The current quality options are stored on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted,
                    )
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it.take(40) },
                        label = { Text("Preset name") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (viewModel.saveCustomPreset(presetName)) {
                            showSaveDialog = false
                        }
                    },
                    enabled = presetName.isNotBlank(),
                ) {
                    Text("Save", style = MaterialTheme.typography.labelLarge, color = palette.ember)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = palette.muted)
                }
            },
            containerColor = palette.ivory,
        )
    }
}

@Composable
private fun PresetGrid(selected: QualityPreset, onSelect: (QualityPreset) -> Unit) {
    val palette = UC.palette
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QualityPreset.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { preset ->
                    val isSelected = selected == preset
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) palette.ink else palette.paper)
                            .clickable { onSelect(preset) }
                            .padding(14.dp),
                    ) {
                        Text(
                            preset.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) palette.paper else palette.ink,
                        )
                        Text(
                            preset.caption,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = if (isSelected) palette.paper.copy(alpha = 0.6f) else palette.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
        color = UC.palette.muted,
        modifier = Modifier.padding(top = 22.dp, bottom = 9.dp),
    )
}

@Composable
private fun OptionChips(
    label: String,
    values: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val palette = UC.palette
    Text(label, style = MaterialTheme.typography.labelLarge, color = palette.ink)
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { (value, text) ->
            val isSelected = selected == value
            Text(
                text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                color = if (isSelected) palette.paper else palette.ink,
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) palette.ink else palette.paper)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            )
        }
    }
}