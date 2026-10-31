package com.universalconverter.pro.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.convert.TargetSpec
import com.universalconverter.pro.core.detect.DetectionResult
import com.universalconverter.pro.core.engine.ConversionEngine
import com.universalconverter.pro.core.engine.ConversionOutcome
import com.universalconverter.pro.work.ConversionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections
import java.util.UUID

enum class JobStatus {
    QUEUED,
    RUNNING,
    DONE,
    FAILED,
    CANCELLED,
}

data class QueueItem(
    val id: String,
    val sourceFile: File,
    val sourceName: String,
    val sourceFormatId: String,
    val sourceFormatLabel: String,
    val target: TargetSpec,
    val options: ConversionOptions,
    val createdAt: Long,
    val status: JobStatus = JobStatus.QUEUED,
    val progress: Float = 0f,
    val stage: String = "Waiting",
    val outcome: ConversionOutcome? = null,
    val error: String? = null,
    val autoSaved: Boolean = false,
) {
    val finished: Boolean get() = status in setOf(JobStatus.DONE, JobStatus.FAILED, JobStatus.CANCELLED)
}

data class NewJob(
    val sourceFile: File,
    val sourceName: String,
    val detection: DetectionResult,
    val target: TargetSpec,
    val options: ConversionOptions,
)

private class JobCancelled : Exception("Conversion cancelled")

class ConversionQueue(
    private val context: Context,
    private val vault: VaultStore,
    private val history: HistoryStore,
    private val settings: SettingsStore,
    private val engine: ConversionEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()
    private var runner: Job? = null
    private val cancelRequests = Collections.synchronizedSet(HashSet<String>())

    fun enqueue(jobs: List<NewJob>): List<String> {
        if (jobs.isEmpty()) return emptyList()
        val created = jobs.map { job ->
            QueueItem(
                id = UUID.randomUUID().toString(),
                sourceFile = job.sourceFile,
                sourceName = job.sourceName,
                sourceFormatId = job.detection.format?.id ?: "bin",
                sourceFormatLabel = job.detection.format?.displayName ?: "Unknown",
                target = job.target,
                options = job.options,
                createdAt = System.currentTimeMillis(),
            )
        }
        _items.update { current -> current + created }
        startForegroundService()
        startRunner()
        return created.map { it.id }
    }

    fun retry(itemId: String) {
        val item = _items.value.firstOrNull { it.id == itemId } ?: return
        val job = NewJob(
            sourceFile = item.sourceFile,
            sourceName = item.sourceName,
            detection = com.universalconverter.pro.core.detect.FormatDetector.detect(item.sourceFile, item.sourceName),
            target = item.target,
            options = item.options,
        )
        enqueue(listOf(job))
    }

    fun cancel(id: String) {
        cancelRequests += id
        _items.update { items ->
            items.map { item ->
                when {
                    item.id == id && item.status == JobStatus.QUEUED -> item.copy(status = JobStatus.CANCELLED, stage = "Cancelled", progress = 0f)
                    item.id == id && item.status == JobStatus.RUNNING -> item.copy(stage = "Cancelling…")
                    else -> item
                }
            }
        }
    }

    fun cancelAll() {
        val active = _items.value.filter { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }
        active.forEach { cancelRequests += it.id }
        _items.update { items ->
            items.map { item ->
                when (item.status) {
                    JobStatus.QUEUED -> item.copy(status = JobStatus.CANCELLED, stage = "Cancelled")
                    JobStatus.RUNNING -> item.copy(stage = "Cancelling…")
                    else -> item
                }
            }
        }
    }

    fun removeFinished() {
        _items.update { items -> items.filterNot { it.finished } }
    }

    fun clearAllFinished() {
        _items.update { items -> items.filterNot { it.finished } }
    }

    fun dismiss(id: String) {
        _items.update { items -> items.filterNot { it.id == id && it.finished } }
    }

    private fun startRunner() {
        if (runner?.isActive == true) return
        runner = scope.launch {
            while (true) {
                val next = _items.value.firstOrNull { it.status == JobStatus.QUEUED } ?: break
                process(next)
            }
        }
    }

    private suspend fun process(item: QueueItem) {
        if (cancelRequests.contains(item.id)) {
            update(item.id) { it.copy(status = JobStatus.CANCELLED, stage = "Cancelled", progress = 0f) }
            cancelRequests.remove(item.id)
            return
        }
        update(item.id) { it.copy(status = JobStatus.RUNNING, stage = "Starting", progress = 0f) }
        val started = System.currentTimeMillis()
        var output: File? = null
        try {
            output = vault.resolveOutput(item.sourceName, item.sourceFormatId, item.target)
            var lastProgress = -1f
            var lastStage = ""
            val progress: (Float, String) -> Unit = { fraction, stage ->
                if (cancelRequests.contains(item.id)) throw JobCancelled()
                val clamped = fraction.coerceIn(0f, 1f)
                if (clamped - lastProgress >= 0.01f || stage != lastStage) {
                    lastProgress = clamped
                    lastStage = stage
                    update(item.id) { it.copy(progress = clamped, stage = stage) }
                }
            }
            val outcome = engine.convert(
                item.sourceFile,
                item.sourceFormatId,
                item.target,
                item.options,
                output,
                progress,
            )
            if (cancelRequests.contains(item.id)) {
                output.deleteRecursively()
                throw JobCancelled()
            }
            var autoSaved = false
            if (settings.settings.value.autoSaveToDownloads && !outcome.outputIsDirectory && Build.VERSION.SDK_INT >= 29) {
                autoSaved = vault.exportToDownloads(outcome.output) != null
            }
            try {
                history.recordSuccess(item.sourceFile, item.sourceName, item.sourceFormatId, item.target, item.options, outcome)
            } catch (_: Exception) {
            }
            update(item.id) {
                it.copy(
                    status = JobStatus.DONE,
                    progress = 1f,
                    stage = "Done",
                    outcome = outcome,
                    autoSaved = autoSaved,
                )
            }
            cancelRequests.remove(item.id)
        } catch (cancelled: JobCancelled) {
            output?.deleteRecursively()
            update(item.id) {
                it.copy(status = JobStatus.CANCELLED, stage = "Cancelled", progress = 0f)
            }
            cancelRequests.remove(item.id)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            output?.deleteRecursively()
            val message = friendlyError(error)
            try {
                history.recordFailure(
                    item.sourceFile,
                    item.sourceName,
                    item.sourceFormatId,
                    item.target,
                    item.options,
                    message,
                    System.currentTimeMillis() - started,
                )
            } catch (_: Exception) {
            }
            update(item.id) {
                it.copy(status = JobStatus.FAILED, stage = "Failed", error = message)
            }
            cancelRequests.remove(item.id)
        }
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message?.takeIf { it.isNotBlank() } ?: "The conversion failed."
        return raw.replace(Regex("\\s+"), " ").take(360)
    }

    private fun update(id: String, transform: (QueueItem) -> QueueItem) {
        _items.update { items -> items.map { if (it.id == id) transform(it) else it } }
    }

    private fun startForegroundService() {
        try {
            val intent = Intent(context, ConversionService::class.java)
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            // The app process still runs the queue; Android 12+ can refuse background FGS starts.
        }
    }
}