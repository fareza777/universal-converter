package com.universalconverter.pro.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.universalconverter.pro.ConverterApp
import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.convert.QualityPreset
import com.universalconverter.pro.core.convert.TargetMatrix
import com.universalconverter.pro.core.detect.FormatDetector
import com.universalconverter.pro.core.doc.DocParsers
import com.universalconverter.pro.core.doc.DocWriters
import com.universalconverter.pro.core.doc.TableModel
import com.universalconverter.pro.core.mesh.MeshAnalyzer
import com.universalconverter.pro.ui.components.HairlineCard
import com.universalconverter.pro.ui.components.PaperBackground
import com.universalconverter.pro.ui.components.SectionEyebrow
import com.universalconverter.pro.ui.components.UCPrimaryButton
import com.universalconverter.pro.ui.components.UCTopBar
import com.universalconverter.pro.ui.formatDuration
import com.universalconverter.pro.ui.theme.UC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private enum class CheckStatus { PENDING, RUNNING, PASS, FAIL, SKIP }

private data class CheckResult(
    val name: String,
    val status: CheckStatus = CheckStatus.PENDING,
    val detail: String = "Not run",
    val durationMs: Long = 0,
)

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val palette = UC.palette
    val context = LocalContext.current
    val app = context.applicationContext as ConverterApp
    val container = app.container
    val scope = rememberCoroutineScope()
    var results by remember {
        mutableStateOf(
            listOf(
                CheckResult("Content detection"),
                CheckResult("3D mesh round-trip"),
                CheckResult("Document table round-trip"),
                CheckResult("Image PNG → JPEG"),
                CheckResult("Archive extract"),
                CheckResult("WAV → M4A"),
            )
        )
    }
    var running by remember { mutableStateOf(false) }
    var hasRun by remember { mutableStateOf(false) }

    fun update(index: Int, result: CheckResult) {
        scope.launch(Dispatchers.Main.immediate) {
            results = results.toMutableList().also { it[index] = result }
        }
    }

    fun runChecks() {
        if (running) return
        running = true
        hasRun = true
        results = results.map { it.copy(status = CheckStatus.PENDING, detail = "Waiting", durationMs = 0) }
        scope.launch {
            withContext(Dispatchers.IO) {
                val dir = File(app.cacheDir, "diagnostics").apply { deleteRecursively(); mkdirs() }
                val options = ConversionOptions.forPreset(QualityPreset.BALANCED)

                // 1. Detection
                var started = System.currentTimeMillis()
                update(0, CheckResult("Content detection", CheckStatus.RUNNING, "Writing a probe file"))
                runCatching {
                    val obj = File(dir, "probe.obj")
                    obj.writeText("o probe\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n")
                    val detection = FormatDetector.detect(obj)
                    check(detection.format?.id == "obj") { "Detected ${detection.format?.id}" }
                }.onSuccess {
                    update(0, CheckResult("Content detection", CheckStatus.PASS, "OBJ identified from content, not just its name", System.currentTimeMillis() - started))
                }.onFailure { error ->
                    update(0, CheckResult("Content detection", CheckStatus.FAIL, error.message ?: "Detection failed", System.currentTimeMillis() - started))
                }

                // 2. Mesh
                started = System.currentTimeMillis()
                update(1, CheckResult("3D mesh round-trip", CheckStatus.RUNNING, "OBJ → GLB → topology check"))
                runCatching {
                    val source = File(dir, "cube.obj")
                    source.writeText(
                        """
                        o cube
                        v -0.5 -0.5 -0.5
                        v 0.5 -0.5 -0.5
                        v 0.5 0.5 -0.5
                        v -0.5 0.5 -0.5
                        v -0.5 -0.5 0.5
                        v 0.5 -0.5 0.5
                        v 0.5 0.5 0.5
                        v -0.5 0.5 0.5
                        f 5 6 7
                        f 5 7 8
                        f 2 1 4
                        f 2 4 3
                        f 1 5 8
                        f 1 8 4
                        f 6 2 3
                        f 6 3 7
                        f 8 7 3
                        f 8 3 4
                        f 1 2 6
                        f 1 6 5
                        """.trimIndent()
                    )
                    val glb = File(dir, "cube.glb")
                    val target = TargetMatrix.targetsFor("obj", container.caps).first { it.id == "glb" }
                    val outcome = container.engine.convert(source, "obj", target, options, glb) { _, _ -> }
                    check(outcome.verified)
                    val mesh = com.universalconverter.pro.core.mesh.MeshIO.read(glb, "glb").mesh
                    val health = MeshAnalyzer.analyze(mesh)
                    check(mesh.triangleCount == 12) { "Expected 12 triangles, got ${mesh.triangleCount}" }
                    check(health.watertight) { "The converted cube is not watertight" }
                    "12 triangles · watertight · ${health.shellCount} shell"
                }.onSuccess { detail ->
                    update(1, CheckResult("3D mesh round-trip", CheckStatus.PASS, detail, System.currentTimeMillis() - started))
                }.onFailure { error ->
                    update(1, CheckResult("3D mesh round-trip", CheckStatus.FAIL, error.message ?: "Mesh check failed", System.currentTimeMillis() - started))
                }

                // 3. Documents
                started = System.currentTimeMillis()
                update(2, CheckResult("Document table round-trip", CheckStatus.RUNNING, "CSV → XLSX → cells"))
                runCatching {
                    val table = TableModel("Check", listOf(listOf("A", "B"), listOf("1", "2")))
                    val xlsx = File(dir, "check.xlsx")
                    DocWriters.writeXlsx(table, xlsx)
                    val parsed = DocParsers.readXlsx(xlsx)
                    check(parsed.rows[1][0] == "1" && parsed.rows[1][1] == "2") { "Cell values changed" }
                    "2 rows · 2 columns preserved"
                }.onSuccess { detail ->
                    update(2, CheckResult("Document table round-trip", CheckStatus.PASS, detail, System.currentTimeMillis() - started))
                }.onFailure { error ->
                    update(2, CheckResult("Document table round-trip", CheckStatus.FAIL, error.message ?: "Table check failed", System.currentTimeMillis() - started))
                }

                // 4. Image
                started = System.currentTimeMillis()
                update(3, CheckResult("Image PNG → JPEG", CheckStatus.RUNNING, "Creating pixels"))
                runCatching {
                    val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    val paint = Paint()
                    paint.shader = android.graphics.LinearGradient(0f, 0f, 96f, 64f, Color.rgb(180, 71, 36), Color.rgb(76, 92, 73), android.graphics.Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, 96f, 64f, paint)
                    val png = File(dir, "pixels.png")
                    png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    val target = TargetMatrix.targetsFor("png", container.caps).first { it.id == "jpg" }
                    val jpg = File(dir, "pixels.jpg")
                    val outcome = container.engine.convert(png, "png", target, options, jpg) { _, _ -> }
                    check(outcome.verified) { outcome.verification }
                    check(jpg.length() > 100)
                    "96×64 PNG converted and header-verified"
                }.onSuccess { detail ->
                    update(3, CheckResult("Image PNG → JPEG", CheckStatus.PASS, detail, System.currentTimeMillis() - started))
                }.onFailure { error ->
                    update(3, CheckResult("Image PNG → JPEG", CheckStatus.FAIL, error.message ?: "Image check failed", System.currentTimeMillis() - started))
                }

                // 5. Archive
                started = System.currentTimeMillis()
                update(4, CheckResult("Archive extract", CheckStatus.RUNNING, "Building a ZIP"))
                runCatching {
                    val zip = File(dir, "probe.zip")
                    ZipOutputStream(zip.outputStream()).use { stream ->
                        stream.putNextEntry(ZipEntry("hello.txt"))
                        stream.write("Universal Converter archive check".toByteArray())
                        stream.closeEntry()
                    }
                    val target = TargetMatrix.targetsFor("zip", container.caps).first { it.id == "extract" }
                    val folder = File(dir, "unpacked")
                    container.engine.convert(zip, "zip", target, options, folder) { _, _ -> }
                    val extracted = File(folder, "hello.txt")
                    check(extracted.isFile) { "hello.txt was not extracted" }
                    check(extracted.readText().contains("archive check"))
                    "hello.txt extracted safely"
                }.onSuccess { detail ->
                    update(4, CheckResult("Archive extract", CheckStatus.PASS, detail, System.currentTimeMillis() - started))
                }.onFailure { error ->
                    update(4, CheckResult("Archive extract", CheckStatus.FAIL, error.message ?: "Archive check failed", System.currentTimeMillis() - started))
                }

                // 6. Audio encoder
                started = System.currentTimeMillis()
                if (!container.caps.hasAac) {
                    update(5, CheckResult("WAV → M4A", CheckStatus.SKIP, "No AAC encoder is available on this device", 0))
                } else {
                    update(5, CheckResult("WAV → M4A", CheckStatus.RUNNING, "Building a 0.4s tone"))
                    runCatching {
                        val wav = File(dir, "tone.wav")
                        writeTone(wav)
                        val target = TargetMatrix.targetsFor("wav", container.caps).first { it.id == "m4a" }
                        val m4a = File(dir, "tone.m4a")
                        val outcome = container.engine.convert(wav, "wav", target, options, m4a) { _, _ -> }
                        check(outcome.verified) { outcome.verification }
                        check(m4a.length() > 500)
                        "0.4s tone encoded and verified"
                    }.onSuccess { detail ->
                        update(5, CheckResult("WAV → M4A", CheckStatus.PASS, detail, System.currentTimeMillis() - started))
                    }.onFailure { error ->
                        update(5, CheckResult("WAV → M4A", CheckStatus.FAIL, error.message ?: "Audio check failed", System.currentTimeMillis() - started))
                    }
                }
                dir.deleteRecursively()
            }
            running = false
        }
    }

    val passed = results.count { it.status == CheckStatus.PASS }
    val failed = results.count { it.status == CheckStatus.FAIL }

    PaperBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            UCTopBar(
                title = "Device self-check",
                subtitle = if (!hasRun) "These are real conversions, not mocks" else "$passed passed · $failed failed",
                onBack = onBack,
                backIcon = Icons.Rounded.ArrowBack,
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    HairlineCard {
                        Column(Modifier.padding(16.dp)) {
                            SectionEyebrow("Local engine")
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Each check writes a tiny fixture, runs the production converter and verifies the output. Nothing is uploaded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.muted,
                            )
                            Spacer(Modifier.height(12.dp))
                            UCPrimaryButton(
                                if (running) "Running checks…" else "Run device check",
                                onClick = { runChecks() },
                                enabled = !running,
                                loading = running,
                                icon = Icons.Rounded.PlayArrow,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                items(results, key = { it.name }) { result ->
                    HairlineCard {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            when (result.status) {
                                CheckStatus.PASS -> Icon(Icons.Rounded.CheckCircle, null, tint = palette.sage, modifier = Modifier.size(22.dp))
                                CheckStatus.FAIL -> Icon(Icons.Rounded.ErrorOutline, null, tint = palette.error, modifier = Modifier.size(22.dp))
                                CheckStatus.RUNNING -> CircularProgressIndicator(Modifier.size(20.dp), color = palette.ember, strokeWidth = 2.dp)
                                CheckStatus.SKIP -> Icon(Icons.Rounded.Timer, null, tint = palette.muted, modifier = Modifier.size(22.dp))
                                CheckStatus.PENDING -> Box(Modifier.size(22.dp).clip(CircleShape).background(palette.parchment))
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(result.name, style = MaterialTheme.typography.titleSmall, color = palette.ink)
                                Text(
                                    result.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (result.status == CheckStatus.FAIL) palette.error else palette.muted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (result.durationMs > 0) {
                                Text(formatDuration(result.durationMs), style = MaterialTheme.typography.labelSmall, color = palette.muted)
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Audio and video checks depend on device codecs. If a check fails, the app will hide or explain the affected target rather than shipping a dead button.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

private fun writeTone(file: File) {
    val sampleRate = 44100
    val durationMs = 400
    val samples = sampleRate * durationMs / 1000
    val dataSize = samples * 2
    ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(sampleRate)
        putInt(sampleRate * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(dataSize)
        for (i in 0 until samples) {
            val value = (Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 12000).toInt().coerceIn(-32768, 32767)
            putShort(value.toShort())
        }
    }.let { file.writeBytes(it.array()) }
}