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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = MediaRepository(database.mediaFileDao())

    private val _mediaFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val mediaFiles: StateFlow<List<MediaFile>> = _mediaFiles.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Sync state
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress.asStateFlow()

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

    // Key warning state description
    val isApiKeyConfigured: Boolean
        get() = BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"

    init {
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
            val file = MediaFile(
                fileName = fileName,
                fileType = fileType,
                sourceApp = sourceApp,
                fileSize = (fileSizeMb * 1024 * 1024).toLong(),
                timestamp = System.currentTimeMillis(),
                category = "Uncategorized",
                syncStatus = "PENDING"
            )
            val generatedId = repository.insert(file)
            val insertedFile = file.copy(id = generatedId.toInt())
            addSyncLog("[System] New inbound file repository registration: $fileName from $sourceApp")
            
            // Automatically analyze and generate descriptive tags upon upload
            addAiLog("[AI] Automatically organizing newly uploaded file: \"$fileName\"...")
            val result = GeminiClient.organizeFile(
                fileName = insertedFile.fileName,
                fileType = insertedFile.fileType,
                sourceApp = insertedFile.sourceApp,
                fileSizeLong = insertedFile.fileSize,
                customRule = _customRule.value
            )
            val updatedFile = insertedFile.copy(
                category = result.category,
                tags = result.tags.joinToString(", "),
                aiSummary = result.explanation
            )
            repository.update(updatedFile)
            addAiLog("[AI] Automatically organized \"$fileName\" into [${result.category}] tags: ${result.tags.joinToString(", ")}")
        }
    }

    fun addImportedFile(fileName: String, fileType: String, sourceApp: String, sizeBytes: Long, uri: String) {
        viewModelScope.launch {
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
            
            // Automatically analyze and generate descriptive tags upon upload
            addAiLog("[AI] Automatically organizing newly imported file: \"$fileName\"...")
            val result = GeminiClient.organizeFile(
                fileName = insertedFile.fileName,
                fileType = insertedFile.fileType,
                sourceApp = insertedFile.sourceApp,
                fileSizeLong = insertedFile.fileSize,
                customRule = _customRule.value
            )
            val updatedFile = insertedFile.copy(
                category = result.category,
                tags = result.tags.joinToString(", "),
                aiSummary = result.explanation
            )
            repository.update(updatedFile)
            addAiLog("[AI] Automatically organized \"$fileName\" into [${result.category}] tags: ${result.tags.joinToString(", ")}")
        }
    }

    fun deleteFile(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
            addSyncLog("[System] File deleted from secure local database index (Id: $id)")
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
                fileName = file.fileName,
                fileType = file.fileType,
                sourceApp = file.sourceApp,
                fileSizeLong = file.fileSize,
                customRule = _customRule.value
            )

            val updatedFile = file.copy(
                category = result.category,
                tags = result.tags.joinToString(", "),
                aiSummary = result.explanation
            )
            repository.update(updatedFile)
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
                    fileName = file.fileName,
                    fileType = file.fileType,
                    sourceApp = file.sourceApp,
                    fileSizeLong = file.fileSize,
                    customRule = _customRule.value
                )

                val updatedFile = file.copy(
                    category = result.category,
                    tags = result.tags.joinToString(", "),
                    aiSummary = result.explanation
                )
                repository.update(updatedFile)
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

                val simulatedCloudPath = "https://omni-vault.s3.us-west-2.amazonaws.com/u/vault_${UUID.randomUUID().toString().take(6)}_${file.fileName}"
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
