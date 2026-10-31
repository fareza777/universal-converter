package com.universalconverter.pro.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.universalconverter.pro.ConverterApp
import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.convert.QualityPreset
import com.universalconverter.pro.core.convert.TargetMatrix
import com.universalconverter.pro.core.convert.TargetSpec
import com.universalconverter.pro.core.detect.DetectionResult
import com.universalconverter.pro.core.detect.FormatDetector
import com.universalconverter.pro.core.formats.FormatRegistry
import com.universalconverter.pro.data.HistoryEntry
import com.universalconverter.pro.data.JobStatus
import com.universalconverter.pro.data.LibraryItem
import com.universalconverter.pro.data.NewJob
import com.universalconverter.pro.data.QueueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ConvertUiState(
    val draft: List<DraftItem> = emptyList(),
    val options: ConversionOptions = ConversionOptions(),
    val importing: Boolean = false,
    val imported: Int = 0,
    val importTotal: Int = 0,
) {
    val canConvert: Boolean get() = draft.isNotEmpty() && draft.all { it.target != null } && !importing
}

data class DraftItem(
    val id: String,
    val file: File,
    val displayName: String,
    val detection: DetectionResult,
    val target: TargetSpec?,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as ConverterApp).container
    private val vault = container.vault
    private val queue = container.queue
    private val historyStore = container.history
    private val settingsStore = container.settings
    private val caps = container.caps
    private val billing = container.billing
    private val monetization = container.monetization

    val settings = settingsStore.settings
    val billingState = billing.state
    val adState = monetization.state
    val queueItems: StateFlow<List<QueueItem>> = queue.items

    private val initialOptions = ConversionOptions.forPreset(settingsStore.settings.value.defaultPreset)
    private val _ui = MutableStateFlow(ConvertUiState(options = initialOptions))
    val ui: StateFlow<ConvertUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _navigation = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val navigation: SharedFlow<String> = _navigation.asSharedFlow()

    private val _libraryItems = MutableStateFlow<List<LibraryItem>>(emptyList())
    val libraryItems: StateFlow<List<LibraryItem>> = _libraryItems.asStateFlow()

    private val _inboxItems = MutableStateFlow<List<LibraryItem>>(emptyList())
    val inboxItems: StateFlow<List<LibraryItem>> = _inboxItems.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    private var lastFinishedCount = -1

    init {
        refreshLibrary()
        refreshHistory()
        viewModelScope.launch {
            queue.items.collect { items ->
                val finished = items.count { it.finished }
                if (finished != lastFinishedCount) {
                    lastFinishedCount = finished
                    refreshLibrary()
                    refreshHistory()
                }
            }
        }
    }

    fun targetsFor(item: DraftItem): List<TargetSpec> =
        TargetMatrix.targetsFor(item.detection.format?.id, caps)

    fun setPreset(preset: QualityPreset) {
        _ui.update { state ->
            state.copy(options = ConversionOptions.optionsFor(preset, state.options))
        }
    }

    fun updateOptions(transform: (ConversionOptions) -> ConversionOptions) {
        _ui.update { it.copy(options = transform(it.options)) }
    }

    fun importUris(uris: List<Uri>, navigate: Boolean = false) {
        if (uris.isEmpty() || _ui.value.importing) return
        viewModelScope.launch {
            _ui.update { it.copy(importing = true, imported = 0, importTotal = uris.size) }
            val added = ArrayList<DraftItem>()
            var failed = 0
            var unsupported = 0
            withContext(Dispatchers.IO) {
                for ((index, uri) in uris.withIndex()) {
                    try {
                        val imported = vault.importUri(uri)
                        val format = imported.detection.format
                        if (format == null) {
                            unsupported++
                            vault.delete(imported.file)
                        } else {
                            val target = TargetMatrix.defaultTarget(format.id, caps)
                            if (target == null) {
                                unsupported++
                                vault.delete(imported.file)
                            } else {
                                added += DraftItem(
                                    id = UUID.randomUUID().toString(),
                                    file = imported.file,
                                    displayName = imported.displayName,
                                    detection = imported.detection,
                                    target = target,
                                )
                            }
                        }
                    } catch (_: Exception) {
                        failed++
                    }
                    _ui.update { it.copy(imported = index + 1) }
                }
            }
            _ui.update { state ->
                state.copy(
                    draft = state.draft + added,
                    importing = false,
                    imported = 0,
                    importTotal = 0,
                )
            }
            refreshInbox()
            val parts = buildList {
                if (added.isNotEmpty()) add("${added.size} file${if (added.size == 1) "" else "s"} added")
                if (unsupported > 0) add("$unsupported unsupported")
                if (failed > 0) add("$failed failed")
            }
            if (parts.isNotEmpty()) _events.emit(parts.joinToString(" · "))
            if (navigate && added.isNotEmpty()) _navigation.emit("convert")
        }
    }

    fun importTree(treeUri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(importing = true, imported = 0, importTotal = 0) }
            val uris = withContext(Dispatchers.IO) {
                val root = DocumentFile.fromTreeUri(getApplication(), treeUri) ?: return@withContext emptyList()
                collectDocumentUris(root)
            }
            if (uris.isEmpty()) {
                _ui.update { it.copy(importing = false) }
                _events.emit("No supported files were found in that folder.")
                return@launch
            }
            _ui.update { it.copy(importing = false) }
            importUris(uris)
        }
    }

    private fun collectDocumentUris(root: DocumentFile, limit: Int = 300): List<Uri> {
        val result = ArrayList<Uri>()
        val stack = ArrayDeque<DocumentFile>()
        stack += root
        while (stack.isNotEmpty() && result.size < limit) {
            val current = stack.removeFirst()
            if (current.isDirectory) {
                current.listFiles().forEach { stack += it }
            } else if (current.isFile) {
                val name = current.name ?: continue
                val spec = FormatRegistry.findForFileName(name) ?: continue
                if (spec.readable) result += current.uri
            }
        }
        return result
    }

    fun addExistingFile(file: File) {
        if (!file.isFile) {
            viewModelScope.launch { _events.emit("That file is no longer available.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val detection = FormatDetector.detect(file)
            val format = detection.format
            if (format == null) {
                _events.emit("That file type is not supported.")
                return@launch
            }
            val target = TargetMatrix.defaultTarget(format.id, caps)
            if (target == null) {
                _events.emit("No conversion is available for ${format.displayName}.")
                return@launch
            }
            val item = DraftItem(
                id = UUID.randomUUID().toString(),
                file = file,
                displayName = file.name,
                detection = detection,
                target = target,
            )
            _ui.update { it.copy(draft = it.draft + item) }
            _navigation.emit("convert")
        }
    }

    fun removeDraft(id: String) {
        _ui.update { state -> state.copy(draft = state.draft.filterNot { it.id == id }) }
    }

    fun clearDraft() {
        _ui.update { it.copy(draft = emptyList()) }
    }

    fun setTarget(itemId: String, target: TargetSpec) {
        _ui.update { state ->
            state.copy(draft = state.draft.map { if (it.id == itemId) it.copy(target = target) else it })
        }
    }

    fun applyTargetToAll(target: TargetSpec): Int {
        var skipped = 0
        _ui.update { state ->
            state.copy(draft = state.draft.map { item ->
                if (targetsFor(item).any { it.id == target.id }) item.copy(target = target) else {
                    skipped++
                    item
                }
            })
        }
        return skipped
    }

    fun convertAll() {
        val state = _ui.value
        if (state.importing || state.draft.isEmpty()) return
        val valid = state.draft.filter { it.target != null && it.file.isFile }
        if (valid.isEmpty()) {
            viewModelScope.launch { _events.emit("Nothing to convert.") }
            return
        }
        val jobs = valid.map { item ->
            NewJob(
                sourceFile = item.file,
                sourceName = item.displayName,
                detection = item.detection,
                target = item.target!!,
                options = state.options,
            )
        }
        queue.enqueue(jobs)
        _ui.update { it.copy(draft = emptyList()) }
        viewModelScope.launch {
            _events.emit("${jobs.size} conversion${if (jobs.size == 1) "" else "s"} queued")
            if (settingsStore.settings.value.autoOpenQueue) _navigation.emit("queue")
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { vault.listVault() }
            val bytes = withContext(Dispatchers.IO) { vault.totalVaultBytes() }
            _libraryItems.value = items
            _storageBytes.value = bytes
        }
    }

    fun refreshInbox() {
        viewModelScope.launch {
            _inboxItems.value = withContext(Dispatchers.IO) { vault.listInbox() }
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _history.value = withContext(Dispatchers.IO) { historyStore.recent(200) }
        }
    }

    fun deleteHistory(entry: HistoryEntry, deleteOutput: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteOutput && entry.outputPath != null) {
                val file = File(entry.outputPath)
                if (file.exists()) vault.delete(file)
            }
            historyStore.delete(entry.id)
            refreshHistory()
            refreshLibrary()
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyStore.clear()
            refreshHistory()
        }
    }

    fun clearVault() {
        viewModelScope.launch(Dispatchers.IO) {
            vault.listVault().forEach { item ->
                if (!item.isDirectory) vault.delete(item.file)
            }
            refreshLibrary()
            _events.emit("Vault cleared")
        }
    }

    fun resetOnboarding() {
        settingsStore.update { it.copy(onboardingDone = false) }
    }

    fun deleteLibraryItem(item: LibraryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = vault.delete(item.file)
            if (!deleted) _events.emit("Could not delete ${item.displayName}.")
            else _events.emit("${item.displayName} deleted")
            refreshLibrary()
            refreshInbox()
        }
    }

    fun renameLibraryItem(item: LibraryItem, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val renamed = vault.rename(item.file, newName)
            if (renamed == null) _events.emit("Rename failed.")
            refreshLibrary()
            refreshInbox()
        }
    }

    fun exportToDownloads(item: LibraryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (item.isDirectory) {
                _events.emit("Folders can be shared from the file details sheet.")
                return@launch
            }
            val uri = vault.exportToDownloads(item.file)
            _events.emit(if (uri != null) "Saved to Downloads/Universal Converter" else "Saving to Downloads needs Android 10 or newer.")
            refreshLibrary()
        }
    }

    fun shareIntent(item: LibraryItem): Intent? = vault.shareIntent(item.file)

    fun viewIntent(item: LibraryItem): Intent? = vault.viewIntent(item.file)

    fun retryHistory(entry: HistoryEntry) {
        val path = entry.sourcePath ?: run {
            viewModelScope.launch { _events.emit("The original source is no longer available.") }
            return
        }
        val file = File(path)
        if (!file.isFile) {
            viewModelScope.launch { _events.emit("The original source is no longer available.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val detection = FormatDetector.detect(file)
            val format = detection.format ?: return@launch
            val target = TargetMatrix.targetsFor(format.id, caps).firstOrNull { it.id == entry.targetId }
                ?: TargetMatrix.defaultTarget(format.id, caps)
                ?: return@launch
            val options = ConversionOptions.forPreset(
                runCatching { QualityPreset.valueOf(entry.preset) }.getOrDefault(QualityPreset.BALANCED),
            )
            queue.enqueue(
                listOf(
                    NewJob(
                        sourceFile = file,
                        sourceName = entry.sourceName,
                        detection = detection,
                        target = target,
                        options = options,
                    )
                )
            )
            _events.emit("Queued again")
            _navigation.emit("queue")
        }
    }

    fun addHistoryToDraft(entry: HistoryEntry) {
        val path = entry.outputPath ?: return
        addExistingFile(File(path))
    }

    fun setTheme(mode: com.universalconverter.pro.data.ThemeMode) {
        settingsStore.update { it.copy(themeMode = mode) }
    }

    fun setAccentTheme(accent: com.universalconverter.pro.data.AccentTheme) {
        settingsStore.update { it.copy(accentTheme = accent) }
    }

    fun setPureBlack(enabled: Boolean) {
        settingsStore.update { it.copy(pureBlack = enabled) }
    }

    fun setAutoOpenQueue(enabled: Boolean) {
        settingsStore.update { it.copy(autoOpenQueue = enabled) }
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        settingsStore.update { it.copy(keepScreenAwake = enabled) }
    }

    fun completeOnboarding() {
        settingsStore.update { it.copy(onboardingDone = true) }
    }

    fun grantAdFree(untilEpochMs: Long) {
        settingsStore.update { it.copy(adFreeUntilEpochMs = maxOf(it.adFreeUntilEpochMs, untilEpochMs)) }
    }

    fun setRemoveAdsPurchased(purchased: Boolean) {
        settingsStore.update { it.copy(removeAdsPurchased = purchased) }
    }

    fun setDefaultPreset(preset: QualityPreset) {
        settingsStore.update { it.copy(defaultPreset = preset) }
    }

    fun setAutoSave(enabled: Boolean) {
        settingsStore.update { it.copy(autoSaveToDownloads = enabled) }
    }

    fun markNotificationsAsked() {
        settingsStore.update { it.copy(notificationsAsked = true) }
    }

    fun initializeMonetization(activity: Activity) {
        billing.start()
        monetization.initialize(activity)
    }

    fun purchaseRemoveAds(activity: Activity) = billing.purchase(activity)

    fun restorePurchases() = billing.restore()

    fun clearBillingMessage() = billing.clearMessage()

    fun showAdPrivacyOptions(activity: Activity, onFinished: (Boolean) -> Unit = {}) {
        monetization.showPrivacyOptions(activity, onFinished)
    }

    fun maybeShowInterstitial(activity: Activity): Boolean =
        monetization.maybeShowInterstitial(activity)

    fun showRewardedAd(activity: Activity, onResult: (Boolean) -> Unit) {
        monetization.showRewarded(activity, onResult)
    }

    fun refreshRewardedAd() = monetization.refreshRewarded()

    fun showMessage(message: String) {
        viewModelScope.launch { _events.emit(message) }
    }

    fun shareQueueOutput(item: QueueItem): Intent? {
        val output = item.outcome?.output ?: return null
        return vault.shareIntent(output)
    }

    fun viewQueueOutput(item: QueueItem): Intent? {
        val output = item.outcome?.output ?: return null
        return vault.viewIntent(output)
    }

    fun saveQueueOutput(item: QueueItem) {
        val output = item.outcome?.output ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (output.isDirectory) {
                _events.emit("Open the vault folder to save this group.")
                return@launch
            }
            val uri = vault.exportToDownloads(output)
            _events.emit(if (uri != null) "Saved to Downloads/Universal Converter" else "Saving to Downloads needs Android 10 or newer.")
        }
    }

    fun cancelQueueItem(id: String) = queue.cancel(id)
    fun cancelAllQueue() = queue.cancelAll()
    fun dismissQueueItem(id: String) = queue.dismiss(id)
    fun clearFinishedQueue() = queue.clearAllFinished()
    fun retryQueueItem(id: String) = queue.retry(id)

    fun queueSummary(): List<QueueItem> = queue.items.value

    fun hasActiveQueue(): Boolean = queue.items.value.any { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }
}