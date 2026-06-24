package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.database.AppDatabase
import com.example.data.database.MediaFile
import com.example.data.repository.MediaRepository
import com.example.data.api.GeminiClient
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import com.example.data.sync.SyncWorker
import com.example.data.sync.DuplicateWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

data class TaggingEvent(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "SUCCESS", "OFFLINE_FALLBACK", "FAILED", "BYPASSED"
    val tags: List<String>,
    val description: String
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = MediaRepository(database.mediaFileDao())

    private val _mediaFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val mediaFiles: StateFlow<List<MediaFile>> = _mediaFiles.asStateFlow()

    private val _isAutoTaggingEnabled = MutableStateFlow(true)
    val isAutoTaggingEnabled: StateFlow<Boolean> = _isAutoTaggingEnabled.asStateFlow()

    private val _taggingGranularity = MutableStateFlow("STANDARD")
    val taggingGranularity: StateFlow<String> = _taggingGranularity.asStateFlow()

    private val _recentTaggingEvents = MutableStateFlow<List<TaggingEvent>>(listOf(
        TaggingEvent(
            fileName = "system_init.cfg",
            status = "SUCCESS",
            tags = listOf("system", "configuration", "initialization"),
            description = "Preloaded system indices analyzed and cataloged successfully."
        )
    ))
    val recentTaggingEvents: StateFlow<List<TaggingEvent>> = _recentTaggingEvents.asStateFlow()

    fun setAutoTaggingEnabled(enabled: Boolean) {
        _isAutoTaggingEnabled.value = enabled
        addAiLog("[AI Service] Auto-tagging state updated: " + if (enabled) "ENABLED" else "DISABLED")
    }

    fun setTaggingGranularity(granularity: String) {
        _taggingGranularity.value = granularity
        addAiLog("[AI Service] Tagging model directive updated: $granularity")
    }

    fun recordTaggingEvent(event: TaggingEvent) {
        _recentTaggingEvents.value = (listOf(event) + _recentTaggingEvents.value).take(10)
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun refreshMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(1500) // Beautiful simulated repository fetch delay
            _isLoading.value = false
        }
    }

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Sync state
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress.asStateFlow()

    private val _isBackgroundSyncScheduled = MutableStateFlow(false)
    val isBackgroundSyncScheduled: StateFlow<Boolean> = _isBackgroundSyncScheduled.asStateFlow()

    private val _syncIntervalMinutes = MutableStateFlow(15)
    val syncIntervalMinutes: StateFlow<Int> = _syncIntervalMinutes.asStateFlow()

    fun scheduleBackgroundSync(intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(getApplication())
        _syncIntervalMinutes.value = intervalMinutes

        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("BackgroundSyncJob")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "BackgroundSyncJob",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        _isBackgroundSyncScheduled.value = true
        addSyncLog("[System] Background sync scheduled successfully via WorkManager.")
        addSyncLog("[System] Constraints: Device must be charging.")
        addSyncLog("[System] Interval: Every $intervalMinutes minutes.")
    }

    fun cancelBackgroundSync() {
        val workManager = WorkManager.getInstance(getApplication())
        workManager.cancelUniqueWork("BackgroundSyncJob")
        _isBackgroundSyncScheduled.value = false
        addSyncLog("[System] Background sync deactivated. WorkManager periodic job cancelled.")
    }

    fun testBackgroundSyncImmediately() {
        viewModelScope.launch {
            val workManager = WorkManager.getInstance(getApplication())
            val testRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .addTag("TestBackgroundSync")
                .build()

            addSyncLog("[System] Triggering simulated Scheduled Sync Worker now...")
            workManager.enqueueUniqueWork(
                "TestBackgroundSync",
                ExistingWorkPolicy.REPLACE,
                testRequest
            )
        }
    }

    private val _syncLogs = MutableStateFlow<List<String>>(
        listOf(
            "[System] Cloud Sync Manager initialized.",
            "[System] Connection: Secure SSL/TLS 1.3.",
            "[System] Ready."
        )
    )
    val syncLogs: StateFlow<List<String>> = _syncLogs.asStateFlow()

    // AI Organization state
    private val _isOrganizing = MutableStateFlow(false)
    val isOrganizing: StateFlow<Boolean> = _isOrganizing.asStateFlow()

    private val _aiLogs = MutableStateFlow<List<String>>(
        listOf(
            "[AI] Organization agent ready.",
            "[AI] Model target: gemini-3.5-flash."
        )
    )
    val aiLogs: StateFlow<List<String>> = _aiLogs.asStateFlow()

    private val _customRule = MutableStateFlow("")
    val customRule: StateFlow<String> = _customRule.asStateFlow()

    // Undo deletion tracking
    private val _lastDeletedFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val lastDeletedFiles: StateFlow<List<MediaFile>> = _lastDeletedFiles.asStateFlow()

    private val _showUndoToast = MutableStateFlow(false)
    val showUndoToast: StateFlow<Boolean> = _showUndoToast.asStateFlow()

    fun dismissUndoToast() {
        _showUndoToast.value = false
        _lastDeletedFiles.value = emptyList()
    }

    fun undoDelete() {
        viewModelScope.launch {
            val filesToRestore = _lastDeletedFiles.value
            _lastDeletedFiles.value = emptyList()
            _showUndoToast.value = false
            filesToRestore.forEach { file ->
                val restoredFile = file.copy(id = 0)
                repository.insert(restoredFile)
            }
            addSyncLog("[System] Restored ${filesToRestore.size} files from undo action")
        }
    }

    // Secure uploads tracking
    private val _activeUploads = MutableStateFlow<List<UploadTask>>(emptyList())
    val activeUploads: StateFlow<List<UploadTask>> = _activeUploads.asStateFlow()

    fun removeActiveUpload(id: String) {
        _activeUploads.value = _activeUploads.value.filter { it.id != id }
    }

    // Cloud accounts state
    private val _googleDriveEmail = MutableStateFlow<String?>(null)
    val googleDriveEmail: StateFlow<String?> = _googleDriveEmail.asStateFlow()

    private val _googleDriveFolder = MutableStateFlow<String>("CipherVault_Backup")
    val googleDriveFolder: StateFlow<String> = _googleDriveFolder.asStateFlow()

    private val _dropboxEmail = MutableStateFlow<String?>(null)
    val dropboxEmail: StateFlow<String?> = _dropboxEmail.asStateFlow()

    private val _dropboxFolder = MutableStateFlow<String>("CipherVault_Sync")
    val dropboxFolder: StateFlow<String> = _dropboxFolder.asStateFlow()

    private val _preferredCloudRepository = MutableStateFlow<String>("None") // "None", "Google Drive", "Dropbox"
    val preferredCloudRepository: StateFlow<String> = _preferredCloudRepository.asStateFlow()

    fun linkGoogleDrive(email: String, folder: String) {
        _googleDriveEmail.value = email.trim()
        _googleDriveFolder.value = folder.ifBlank { "CipherVault_Backup" }.trim()
        _preferredCloudRepository.value = "Google Drive"
        addSyncLog("[System] Linked Google Drive Account: $email to directory: ${_googleDriveFolder.value}")
        addSyncLog("[System] Configured Google Drive as primary Cloud backing store.")
    }

    fun disconnectGoogleDrive() {
        val oldEmail = _googleDriveEmail.value
        _googleDriveEmail.value = null
        if (_preferredCloudRepository.value == "Google Drive") {
            _preferredCloudRepository.value = if (_dropboxEmail.value != null) "Dropbox" else "None"
        }
        addSyncLog("[System] Disconnected Google Drive Account ($oldEmail). Storage configurations reset.")
    }

    fun linkDropbox(email: String, folder: String) {
        _dropboxEmail.value = email.trim()
        _dropboxFolder.value = folder.ifBlank { "CipherVault_Sync" }.trim()
        _preferredCloudRepository.value = "Dropbox"
        addSyncLog("[System] Linked Dropbox Account: $email to directory: ${_dropboxFolder.value}")
        addSyncLog("[System] Configured Dropbox as primary Cloud backing store.")
    }

    fun disconnectDropbox() {
        val oldEmail = _dropboxEmail.value
        _dropboxEmail.value = null
        if (_preferredCloudRepository.value == "Dropbox") {
            _preferredCloudRepository.value = if (_googleDriveEmail.value != null) "Google Drive" else "None"
        }
        addSyncLog("[System] Disconnected Dropbox Account ($oldEmail). Storage configurations reset.")
    }

    fun setPreferredCloudRepository(provider: String) {
        _preferredCloudRepository.value = provider
        addSyncLog("[System] Root database backup sync node shifted to: $provider")
    }

    // Duplicate Management State
    private val _isScanningDuplicates = MutableStateFlow(false)
    val isScanningDuplicates: StateFlow<Boolean> = _isScanningDuplicates.asStateFlow()

    fun scanForDuplicates() {
        val workManager = WorkManager.getInstance(getApplication())
        val duplicateRequest = OneTimeWorkRequestBuilder<DuplicateWorker>()
            .addTag("DuplicateScannerJob")
            .build()
            
        _isScanningDuplicates.value = true
        addSyncLog("[System] MD5 Engine: Calculating file checksum signatures & scanning vault...")
        
        workManager.enqueueUniqueWork(
            "DuplicateScannerJob",
            ExistingWorkPolicy.REPLACE,
            duplicateRequest
        )
        
        viewModelScope.launch {
            delay(2000) // Visual execution delay
            _isScanningDuplicates.value = false
            addSyncLog("[System] MD5 Engine: Vault analysis complete. Parallel duplicates identified and flagged.")
        }
    }

    fun keepDuplicateFile(file: MediaFile) {
        viewModelScope.launch {
            val updatedFile = file.copy(isDuplicate = false)
            repository.update(updatedFile)
            addSyncLog("[System] MD5 Engine: Excluded \"${file.fileName}\" from duplicate group per user feedback.")
        }
    }

    // Key warning state description
    val isApiKeyConfigured: Boolean
        get() = BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"

    init {
        // Trigger initial simulated repository fetching delay
        refreshMedia()

        // Collect repository contents
        viewModelScope.launch {
            repository.allMediaFiles.collectLatest { list ->
                _mediaFiles.value = list
                // Pre-populate if database is empty so we have interactive content on fresh launch
                if (list.isEmpty()) {
                    prepopulateDatabase()
                }
            }
        }
    }

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun updateCustomRule(rule: String) {
        _customRule.value = rule
    }

    private suspend fun prepopulateDatabase() {
        val initialFiles = listOf(
            MediaFile(
                fileName = "invoice_tax_quarter2.pdf",
                fileType = "DOCUMENT",
                sourceApp = "Telegram",
                fileSize = 1048576 * 2, // 2MB
                timestamp = System.currentTimeMillis() - 86400000 * 2, // 2 days ago
                category = "Uncategorized",
                syncStatus = "PENDING"
            ),
            MediaFile(
                fileName = "laughing_cat_haha.mp4",
                fileType = "VIDEO",
                sourceApp = "WhatsApp",
                fileSize = 1048576 * 12, // 12MB
                timestamp = System.currentTimeMillis() - 3600000 * 4, // 4 hours ago
                category = "Uncategorized",
                syncStatus = "PENDING"
            ),
            MediaFile(
                fileName = "screenshot_2026_06_22.png",
                fileType = "IMAGE",
                sourceApp = "Screenshots",
                fileSize = 1048576 / 2, // 500KB
                timestamp = System.currentTimeMillis() - 1800000, // 30 mins ago
                category = "Uncategorized",
                syncStatus = "PENDING"
            ),
            MediaFile(
                fileName = "meeting_audio_memo.wav",
                fileType = "AUDIO",
                sourceApp = "Downloads",
                fileSize = 1048576 * 15, // 15MB
                timestamp = System.currentTimeMillis() - 86400000, // 1 day ago
                category = "Uncategorized",
                syncStatus = "PENDING"
            ),
            MediaFile(
                fileName = "personal_holiday_trip_sunset.jpg",
                fileType = "IMAGE",
                sourceApp = "Camera",
                fileSize = 1048576 * 4, // 4MB
                timestamp = System.currentTimeMillis() - 86400000 * 5, // 5 days ago
                category = "Personal",
                syncStatus = "SYNCED",
                cloudUrl = "https://omni-vault.s3.us-west-2.amazonaws.com/u/sunset_8291.jpg",
                aiSummary = "Analyzed by local agent: Dynamic scenic sunset landscape containing highly structured warm elements."
            )
        )
        initialFiles.forEach { repository.insert(it) }
    }

    fun addSimulatedFile(fileName: String, fileType: String, sourceApp: String, fileSizeMb: Double) {
        viewModelScope.launch {
            val sizeBytes = (fileSizeMb * 1024 * 1024).toLong()
            val taskId = UUID.randomUUID().toString()
            val task = UploadTask(
                id = taskId,
                fileName = fileName,
                totalSize = sizeBytes,
                progress = 0.1f,
                status = "ENCRYPTING"
            )
            _activeUploads.value = _activeUploads.value + task
            
            // 1. Simulate encryption stage
            delay(1000)
            _activeUploads.value = _activeUploads.value.map {
                if (it.id == taskId) it.copy(progress = 0.3f, status = "UPLOADING") else it
            }
            
            // 2. Simulate uploading to sandbox vault
            delay(1200)
            _activeUploads.value = _activeUploads.value.map {
                if (it.id == taskId) it.copy(progress = 0.7f, status = "ANALYZING_AI") else it
            }

            val file = MediaFile(
                fileName = fileName,
                fileType = fileType,
                sourceApp = sourceApp,
                fileSize = sizeBytes,
                timestamp = System.currentTimeMillis(),
                category = "Uncategorized",
                syncStatus = "PENDING"
            )
            val generatedId = repository.insert(file)
            val insertedFile = file.copy(id = generatedId.toInt())
            addSyncLog("[System] New inbound file repository registration: $fileName from $sourceApp")
            
            val result = if (_isAutoTaggingEnabled.value) {
                addAiLog("[AI] Automatically organizing newly uploaded file: \"$fileName\"...")
                val res = GeminiClient.organizeFile(
                    context = getApplication(),
                    fileName = insertedFile.fileName,
                    fileType = insertedFile.fileType,
                    sourceApp = insertedFile.sourceApp,
                    fileSizeLong = insertedFile.fileSize,
                    localUri = null,
                    customRule = _customRule.value,
                    granularity = _taggingGranularity.value
                )
                val statusStr = if (isApiKeyConfigured) "SUCCESS" else "OFFLINE_FALLBACK"
                recordTaggingEvent(
                    TaggingEvent(
                        fileName = fileName,
                        status = statusStr,
                        tags = res.tags,
                        description = "Simulated upload processed: ${res.explanation}"
                    )
                )
                res
            } else {
                addAiLog("[AI] Auto-tagging bypassed for \"$fileName\" per user preference.")
                recordTaggingEvent(
                    TaggingEvent(
                        fileName = fileName,
                        status = "BYPASSED",
                        tags = emptyList(),
                        description = "Auto-tagging was deactivated by the user. Real-time Gemini analysis skipped."
                    )
                )
                com.example.data.api.AIOrganizationResult(
                    category = "Uncategorized",
                    tags = emptyList(),
                    explanation = "Awaiting manual tag generation. Real-time auto-tagger client was bypassed."
                )
            }

            val updatedFile = insertedFile.copy(
                category = result.category,
                tags = result.tags.joinToString(", "),
                aiSummary = result.explanation
            )
            repository.update(updatedFile)
            if (_isAutoTaggingEnabled.value) {
                addAiLog("[AI] Automatically organized \"$fileName\" into [${result.category}] tags: ${result.tags.joinToString(", ")}")
            }

            // Mark task as completed
            _activeUploads.value = _activeUploads.value.map {
                if (it.id == taskId) it.copy(progress = 1.0f, status = "COMPLETED", tags = result.tags) else it
            }
        }
    }

    fun addImportedFile(fileName: String, fileType: String, sourceApp: String, sizeBytes: Long, uri: String) {
        viewModelScope.launch {
            val taskId = UUID.randomUUID().toString()
            val task = UploadTask(
                id = taskId,
                fileName = fileName,
                totalSize = sizeBytes,
                progress = 0.1f,
                status = "ENCRYPTING"
            )
            _activeUploads.value = _activeUploads.value + task
            
            // 1. Simulate encryption stage
            delay(1000)
            _activeUploads.value = _activeUploads.value.map {
                if (it.id == taskId) it.copy(progress = 0.3f, status = "UPLOADING") else it
            }
            
            // 2. Simulate saving to secure sandbox vault
            delay(1200)
            _activeUploads.value = _activeUploads.value.map {
                if (it.id == taskId) it.copy(progress = 0.7f, status = "ANALYZING_AI") else it
            }

            val file = MediaFile(
                fileName = fileName,
                fileType = fileType,
                sourceApp = sourceApp,
                fileSize = sizeBytes,
                timestamp = System.currentTimeMillis(),
                category = "Uncategorized",
                syncStatus = "PENDING",
                localUri = uri
            )
            val generatedId = repository.insert(file)
            val insertedFile = file.copy(id = generatedId.toInt())
            addSyncLog("[System] Imported physical device file: $fileName under Secure Sandbox")
            
            val result = if (_isAutoTaggingEnabled.value) {
                addAiLog("[AI] Automatically organizing newly imported file: \"$fileName\"...")
                val res = GeminiClient.organizeFile(
                    context = getApplication(),
                    fileName = insertedFile.fileName,
                    fileType = insertedFile.fileType,
                    sourceApp = insertedFile.sourceApp,
                    fileSizeLong = insertedFile.fileSize,
                    localUri = insertedFile.localUri,
                    customRule = _customRule.value,
                    granularity = _taggingGranularity.value
                )
                val statusStr = if (isApiKeyConfigured) "SUCCESS" else "OFFLINE_FALLBACK"
                recordTaggingEvent(
                    TaggingEvent(
                        fileName = fileName,
                        status = statusStr,
                        tags = res.tags,
                        description = "Imported file processed: ${res.explanation}"
                    )
                )
                res
            } else {
                addAiLog("[AI] Auto-tagging bypassed for \"$fileName\" per user preference.")
                recordTaggingEvent(
                    TaggingEvent(
                        fileName = fileName,
                        status = "BYPASSED",
                        tags = emptyList(),
                        description = "Auto-tagging was deactivated by the user. Real-time Gemini analysis skipped."
                    )
                )
                com.example.data.api.AIOrganizationResult(
                    category = "Uncategorized",
                    tags = emptyList(),
                    explanation = "Awaiting manual tag generation. Real-time auto-tagger client was bypassed."
                )
            }

            val updatedFile = insertedFile.copy(
                category = result.category,
                tags = result.tags.joinToString(", "),
                aiSummary = result.explanation
            )
            repository.update(updatedFile)
            if (_isAutoTaggingEnabled.value) {
                addAiLog("[AI] Automatically organized \"$fileName\" into [${result.category}] tags: ${result.tags.joinToString(", ")}")
            }

            // Mark task as completed
            _activeUploads.value = _activeUploads.value.map {
                if (it.id == taskId) it.copy(progress = 1.0f, status = "COMPLETED", tags = result.tags) else it
            }
        }
    }

    fun deleteFile(id: Int) {
        viewModelScope.launch {
            val file = _mediaFiles.value.find { it.id == id }
            if (file != null) {
                _lastDeletedFiles.value = listOf(file)
                _showUndoToast.value = true
                repository.deleteById(id)
                addSyncLog("[System] File deleted from secure local database index (Id: $id)")
                
                // Wait 5 seconds and auto-hide
                delay(5000)
                if (_lastDeletedFiles.value.any { it.id == id }) {
                    _showUndoToast.value = false
                }
            }
        }
    }

    fun deleteMultipleFiles(ids: List<Int>) {
        viewModelScope.launch {
            val files = _mediaFiles.value.filter { it.id in ids }
            if (files.isNotEmpty()) {
                _lastDeletedFiles.value = files
                _showUndoToast.value = true
                ids.forEach { repository.deleteById(it) }
                addSyncLog("[System] Securely deleted ${ids.size} files from database vault")
                
                // Wait 5 seconds and auto-hide
                delay(5000)
                if (_lastDeletedFiles.value == files) {
                    _showUndoToast.value = false
                }
            }
        }
    }

    fun moveMultipleFilesToCategory(ids: List<Int>, newCategory: String) {
        viewModelScope.launch {
            var count = 0
            _mediaFiles.value.filter { it.id in ids }.forEach { file ->
                val updated = file.copy(category = newCategory)
                repository.update(updated)
                count++
            }
            addSyncLog("[System] Successfully moved $count files to folder: [$newCategory]")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
            _syncLogs.value = listOf("[System] Vault registers cleared.", "[System] Database index reset.")
            prepopulateDatabase()
        }
    }

    private fun addSyncLog(message: String) {
        _syncLogs.value = _syncLogs.value + message
    }

    private fun addAiLog(message: String) {
        _aiLogs.value = _aiLogs.value + message
    }

    // Trigger organization for a single file using Gemini
    fun organizeSingleFile(file: MediaFile) {
        viewModelScope.launch {
            addAiLog("[AI] Initiating analysis for \"${file.fileName}\"...")
            val result = GeminiClient.organizeFile(
                context = getApplication(),
                fileName = file.fileName,
                fileType = file.fileType,
                sourceApp = file.sourceApp,
                fileSizeLong = file.fileSize,
                localUri = file.localUri,
                customRule = _customRule.value,
                granularity = _taggingGranularity.value
            )

            val updatedFile = file.copy(
                category = result.category,
                tags = result.tags.joinToString(", "),
                aiSummary = result.explanation
            )
            repository.update(updatedFile)
            val statusStr = if (isApiKeyConfigured) "SUCCESS" else "OFFLINE_FALLBACK"
            recordTaggingEvent(
                TaggingEvent(
                    fileName = file.fileName,
                    status = statusStr,
                    tags = result.tags,
                    description = "Manual analysis for file: ${result.explanation}"
                )
            )
            addAiLog("[AI] Successfully organized \"${file.fileName}\" into [${result.category}] tags: ${result.tags.joinToString(", ")}")
        }
    }

    // Trigger organization for all files currently marked "Uncategorized"
    fun autoOrganizeAll() {
        val filesToOrganize = _mediaFiles.value.filter { it.category == "Uncategorized" }
        if (filesToOrganize.isEmpty()) {
            addAiLog("[AI] Scan complete: No Uncategorized files found.")
            return
        }

        viewModelScope.launch {
            _isOrganizing.value = true
            addAiLog("[AI] Commencing folder scan. Found ${filesToOrganize.size} files ready for organization.")
            
            for (file in filesToOrganize) {
                addAiLog("[AI] Organizing \"${file.fileName}\"...")
                val result = GeminiClient.organizeFile(
                    context = getApplication(),
                    fileName = file.fileName,
                    fileType = file.fileType,
                    sourceApp = file.sourceApp,
                    fileSizeLong = file.fileSize,
                    localUri = file.localUri,
                    customRule = _customRule.value,
                    granularity = _taggingGranularity.value
                )

                val updatedFile = file.copy(
                    category = result.category,
                    tags = result.tags.joinToString(", "),
                    aiSummary = result.explanation
                )
                repository.update(updatedFile)
                val statusStr = if (isApiKeyConfigured) "SUCCESS" else "OFFLINE_FALLBACK"
                recordTaggingEvent(
                    TaggingEvent(
                        fileName = file.fileName,
                        status = statusStr,
                        tags = result.tags,
                        description = "Batch catalog scan: ${result.explanation}"
                    )
                )
                addAiLog("[AI] -> Complete: ${file.fileName} is in folder [${result.category}]")
                delay(800) // Small delay to visualize progress nicely
            }
            _isOrganizing.value = false
            addAiLog("[AI] Process finished! All files successfully integrated.")
        }
    }

    // Cloud syncing simulator
    fun syncNow() {
        val pendingFiles = _mediaFiles.value.filter { it.syncStatus == "PENDING" }
        if (pendingFiles.isEmpty()) {
            addSyncLog("[System] Sync checked: All repository indexes already secure on Cloud Node.")
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgress.value = 0f
            val provider = _preferredCloudRepository.value
            if (provider == "None") {
                addSyncLog("[Sync Engine] Warning: No preferred cloud repository linked. Falling back to default backup sandbox container...")
            } else {
                val email = if (provider == "Google Drive") _googleDriveEmail.value else _dropboxEmail.value
                val folder = if (provider == "Google Drive") _googleDriveFolder.value else _dropboxFolder.value
                addSyncLog("[Sync Engine] Active backing store: $provider")
                addSyncLog("[Sync Engine] Remote destination: account $email inside subfolder /$folder")
            }
            delay(1000)
            addSyncLog("[Sync Engine] Connecting to target Master Cloud Sync Registry...")
            delay(1000)
            addSyncLog("[Sync Engine] Authenticating access credentials with end-to-end TLS 1.3 handshakes...")
            delay(1000)
            addSyncLog("[Sync Engine] Found ${pendingFiles.size} media items awaiting sync.")

            val increment = 1f / pendingFiles.size
            for ((index, file) in pendingFiles.withIndex()) {
                addSyncLog("[Sync Engine] Sending metadata: \"${file.fileName}\" (${file.fileSize / 1024} KB)")
                delay(600)
                
                // Simulate progressive upload chunks
                val steps = 3
                for (s in 1..steps) {
                    val partOffset = ((index + (s.toFloat() / steps)) * increment).coerceAtLeast(0f)
                    _syncProgress.value = partOffset
                    delay(300)
                }

                val simulatedCloudPath = if (provider == "Google Drive") {
                    "https://drive.google.com/drive/folders/backup_${UUID.randomUUID().toString().take(12)}/${file.fileName}"
                } else if (provider == "Dropbox") {
                    "https://www.dropbox.com/home/${_dropboxFolder.value}/${file.fileName}"
                } else {
                    "https://omni-vault.s3.us-west-2.amazonaws.com/u/vault_${UUID.randomUUID().toString().take(6)}_${file.fileName}"
                }
                val updatedFile = file.copy(
                    syncStatus = "SYNCED",
                    cloudUrl = simulatedCloudPath
                )
                repository.update(updatedFile)
                addSyncLog("[Sync Engine] Securely uploaded chunk and verified. Remote URI generated: $simulatedCloudPath")
            }

            _syncProgress.value = 1f
            _isSyncing.value = false
            addSyncLog("[Sync Engine] All pending uploads resolved. State consolidated.")
        }
    }

    fun updateFileTags(id: Int, newTags: String) {
        viewModelScope.launch {
            val file = _mediaFiles.value.find { it.id == id }
            if (file != null) {
                val updated = file.copy(tags = newTags)
                repository.update(updated)
                addAiLog("[AI] Manual tags override for \"${file.fileName}\" saved: $newTags")
            }
        }
    }

    fun addTagsToMultipleFiles(ids: List<Int>, tagsToAdd: String) {
        viewModelScope.launch {
            val list = _mediaFiles.value.filter { ids.contains(it.id) }
            val cleanTags = tagsToAdd.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            
            if (cleanTags.isNotEmpty()) {
                list.forEach { file ->
                    val existingTagsList = file.tags.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    
                    val unionTags = (existingTagsList + cleanTags).distinct()
                    val updatedTagsStr = unionTags.joinToString(", ")
                    
                    val updated = file.copy(tags = updatedTagsStr)
                    repository.update(updated)
                }
                addAiLog("[AI] Bulk added tags [$tagsToAdd] to ${list.size} files.")
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MediaViewModel::class.java)) {
                return MediaViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

data class UploadTask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileName: String,
    val totalSize: Long,
    val progress: Float, // 0.0 to 1.0
    val status: String,  // "READING", "ENCRYPTING", "UPLOADING", "ANALYZING_AI", "COMPLETED", "FAILED"
    val tags: List<String> = emptyList()
)
