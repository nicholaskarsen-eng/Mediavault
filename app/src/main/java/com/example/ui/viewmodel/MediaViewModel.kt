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
import com.example.data.settings.SettingsManager
import android.provider.MediaStore
import android.content.ContentUris
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CloudAccount(
    val id: String = java.util.UUID.randomUUID().toString(),
    val provider: String,
    val accountName: String,
    val region: String,
    val type: String // "Primary", "Backup", "Archive", "DR"
)

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
    private val settingsManager = SettingsManager(application)

    private val _mediaFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val mediaFiles: StateFlow<List<MediaFile>> = _mediaFiles.asStateFlow()

    private val _connectedAccounts = MutableStateFlow<List<CloudAccount>>(
        listOf(
            CloudAccount(provider = "AWS S3", accountName = "primary-vault-prod", region = "us-east-1", type = "Primary"),
            CloudAccount(provider = "Azure Blob", accountName = "backup-node-01", region = "westeurope", type = "Backup")
        )
    )
    val connectedAccounts: StateFlow<List<CloudAccount>> = _connectedAccounts.asStateFlow()

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

    // Import state
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importProgress = MutableStateFlow(0f)
    val importProgress: StateFlow<Float> = _importProgress.asStateFlow()

    private val _customRule = MutableStateFlow(settingsManager.getString(SettingsManager.KEY_CUSTOM_RULE, ""))
    val customRule: StateFlow<String> = _customRule.asStateFlow()

    private val _apiKey = MutableStateFlow(settingsManager.getString(SettingsManager.KEY_API_KEY, BuildConfig.GEMINI_API_KEY))
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    // Fully comprehensive settings state
    private val _isWifiOnlySync = MutableStateFlow(settingsManager.getBoolean(SettingsManager.KEY_WIFI_ONLY, true))
    val isWifiOnlySync: StateFlow<Boolean> = _isWifiOnlySync.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(settingsManager.getBoolean(SettingsManager.KEY_BIOMETRIC, false))
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _appTheme = MutableStateFlow(settingsManager.getString(SettingsManager.KEY_THEME, "System")) // "Light", "Dark", "System"
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    private val _autoOrganizeOnImport = MutableStateFlow(settingsManager.getBoolean(SettingsManager.KEY_AUTO_ORGANIZE, true))
    val autoOrganizeOnImport: StateFlow<Boolean> = _autoOrganizeOnImport.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(settingsManager.getBoolean(SettingsManager.KEY_NOTIFICATIONS, true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _isCloudSyncEnabled = MutableStateFlow(settingsManager.getBoolean(SettingsManager.KEY_CLOUD_SYNC, false))
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()

    private val _selectedAiModel = MutableStateFlow(settingsManager.getString(SettingsManager.KEY_AI_MODEL, "gemini-1.5-flash")) // "gemini-1.5-flash", "gemini-1.5-pro"
    val selectedAiModel: StateFlow<String> = _selectedAiModel.asStateFlow()

    private val _maxVaultSizeMb = MutableStateFlow(settingsManager.getInt(SettingsManager.KEY_MAX_SIZE, 500))
    val maxVaultSizeMb: StateFlow<Int> = _maxVaultSizeMb.asStateFlow()

    private val _autoDeleteAfterDays = MutableStateFlow(settingsManager.getInt(SettingsManager.KEY_AUTO_DELETE, 0)) // 0 means disabled
    val autoDeleteAfterDays: StateFlow<Int> = _autoDeleteAfterDays.asStateFlow()

    private val _isUniversalRepoEnabled = MutableStateFlow(settingsManager.getBoolean(SettingsManager.KEY_UNIVERSAL_REPO, false))
    val isUniversalRepoEnabled: StateFlow<Boolean> = _isUniversalRepoEnabled.asStateFlow()

    private val _autoConsolidate = MutableStateFlow(true)
    val autoConsolidate: StateFlow<Boolean> = _autoConsolidate.asStateFlow()

    private val _deviceId = MutableStateFlow(settingsManager.getString(SettingsManager.KEY_DEVICE_ID, "DEV-${java.util.UUID.randomUUID().toString().take(6).uppercase()}"))
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _redundancyLevel = MutableStateFlow(settingsManager.getInt(SettingsManager.KEY_REDUNDANCY, 4)) // 1 to 4 nodes
    val redundancyLevel: StateFlow<Int> = _redundancyLevel.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)
    val lastSyncTimestamp: StateFlow<Long?> = _lastSyncTimestamp.asStateFlow()

    // Key warning state description
    val isApiKeyConfigured: Boolean
        get() = _apiKey.value.isNotBlank() && 
                _apiKey.value != "MY_GEMINI_API_KEY" &&
                !_apiKey.value.contains("REPLACE_WITH_YOUR_GEMINI_API_KEY")

    init {
        // Trigger initial simulated repository fetching delay
        refreshMedia()

        // Collect repository contents
        viewModelScope.launch {
            repository.allMediaFiles.collectLatest { list ->
                _mediaFiles.value = list
            }
        }
    }

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun updateCustomRule(rule: String) {
        _customRule.value = rule
        settingsManager.setString(SettingsManager.KEY_CUSTOM_RULE, rule)
        autoSyncConfigIfEnabled()
    }

    fun updateApiKey(key: String) {
        _apiKey.value = key
        settingsManager.setString(SettingsManager.KEY_API_KEY, key)
        autoSyncConfigIfEnabled()
    }

    fun updateWifiOnlySync(enabled: Boolean) {
        _isWifiOnlySync.value = enabled
        settingsManager.setBoolean(SettingsManager.KEY_WIFI_ONLY, enabled)
        autoSyncConfigIfEnabled()
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        _isBiometricEnabled.value = enabled
        settingsManager.setBoolean(SettingsManager.KEY_BIOMETRIC, enabled)
        autoSyncConfigIfEnabled()
    }

    fun updateAppTheme(theme: String) {
        _appTheme.value = theme
        settingsManager.setString(SettingsManager.KEY_THEME, theme)
        autoSyncConfigIfEnabled()
    }

    fun updateAutoOrganizeOnImport(enabled: Boolean) {
        _autoOrganizeOnImport.value = enabled
        settingsManager.setBoolean(SettingsManager.KEY_AUTO_ORGANIZE, enabled)
        autoSyncConfigIfEnabled()
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        settingsManager.setBoolean(SettingsManager.KEY_NOTIFICATIONS, enabled)
        autoSyncConfigIfEnabled()
    }

    fun updateSelectedAiModel(model: String) {
        _selectedAiModel.value = model
        settingsManager.setString(SettingsManager.KEY_AI_MODEL, model)
        addAiLog("[System] AI Engine switched to: $model")
        autoSyncConfigIfEnabled()
    }

    fun updateMaxVaultSize(size: Int) {
        _maxVaultSizeMb.value = size
        settingsManager.setInt(SettingsManager.KEY_MAX_SIZE, size)
        autoSyncConfigIfEnabled()
    }

    fun updateAutoDeleteAfterDays(days: Int) {
        _autoDeleteAfterDays.value = days
        settingsManager.setInt(SettingsManager.KEY_AUTO_DELETE, days)
        autoSyncConfigIfEnabled()
    }

    fun updateUniversalRepoEnabled(enabled: Boolean) {
        _isUniversalRepoEnabled.value = enabled
        settingsManager.setBoolean(SettingsManager.KEY_UNIVERSAL_REPO, enabled)
        if (enabled) {
            addSyncLog("[System] Universal Repository Protocol: ACTIVATED. Cross-device consolidation enabled.")
            autoSyncConfigIfEnabled()
        } else {
            addSyncLog("[System] Universal Repository Protocol: DEACTIVATED. Device isolation enforced.")
        }
    }

    fun updateRedundancyLevel(level: Int) {
        val maxNodes = _connectedAccounts.value.size.coerceAtLeast(1)
        val coercedLevel = level.coerceIn(1, maxNodes)
        _redundancyLevel.value = coercedLevel
        settingsManager.setInt(SettingsManager.KEY_REDUNDANCY, coercedLevel)
        addSyncLog("[System] Sync Redundancy re-configured to: $coercedLevel-Node Matrix.")
        autoSyncConfigIfEnabled()
    }

    private fun autoSyncConfigIfEnabled() {
        if (_isUniversalRepoEnabled.value && _isCloudSyncEnabled.value) {
            syncConfigurationToCloud()
        }
    }

    fun syncConfigurationToCloud() {
        viewModelScope.launch {
            addSyncLog("[Sync Engine] Mirroring system configuration to Global Matrix...")
            delay(500)
            // Simulated upload of settings to cloud metadata
            addSyncLog("[Sync Engine] Configuration mirroring SUCCESSFUL. All nodes updated.")
        }
    }

    fun autoConfigureRedundancyFromAccounts() {
        val count = _connectedAccounts.value.size.coerceIn(1, 4)
        _redundancyLevel.value = count
        addSyncLog("[System] Auto-configured Redundancy Level to $count based on ${_connectedAccounts.value.size} linked cloud accounts.")
    }

    fun linkAccount(provider: String, name: String, region: String, type: String) {
        val newAccount = CloudAccount(provider = provider, accountName = name, region = region, type = type)
        _connectedAccounts.value = _connectedAccounts.value + newAccount
        addSyncLog("[System] New cloud account linked: $name ($provider)")
    }

    fun unlinkAccount(id: String) {
        val account = _connectedAccounts.value.find { it.id == id }
        _connectedAccounts.value = _connectedAccounts.value.filter { it.id != id }
        account?.let { addSyncLog("[System] Cloud account removed: ${it.accountName}") }
        
        // Ensure redundancy level does not exceed the new account count
        val maxNodes = _connectedAccounts.value.size.coerceAtLeast(1)
        if (_redundancyLevel.value > maxNodes) {
            _redundancyLevel.value = maxNodes
            addSyncLog("[System] Sync Redundancy auto-adjusted to $maxNodes nodes due to account removal.")
        }
    }

    fun updateCloudSyncEnabled(enabled: Boolean) {
        _isCloudSyncEnabled.value = enabled
        if (enabled) {
            addSyncLog("[System] Cloud Synchronization Gate: OPENED.")
        } else {
            addSyncLog("[System] Cloud Synchronization Gate: CLOSED. Data restricted to local sandbox.")
        }
    }

    fun addImportedFile(fileName: String, fileType: String, sourceApp: String, sizeBytes: Long, uri: String) {
        viewModelScope.launch {
            _isImporting.value = true
            _importProgress.value = 0.5f
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
            _importProgress.value = 1f
            addSyncLog("[System] Imported physical device file: $fileName under Secure Sandbox")
            delay(500)
            _isImporting.value = false
            
            // Automatically analyze and generate descriptive tags upon upload if enabled
            if (_autoOrganizeOnImport.value) {
                addAiLog("[AI] Automatically organizing newly imported file: \"$fileName\"...")
                val result = GeminiClient.organizeFile(
                    fileName = insertedFile.fileName,
                    fileType = insertedFile.fileType,
                    sourceApp = insertedFile.sourceApp,
                    fileSizeLong = insertedFile.fileSize,
                    customRule = _customRule.value,
                    modelName = _selectedAiModel.value,
                    apiKey = _apiKey.value
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
                customRule = _customRule.value,
                modelName = _selectedAiModel.value,
                apiKey = _apiKey.value
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

    // Trigger organization for all files currently marked "Uncategorized" with parallel execution
    fun autoOrganizeAll() {
        val filesToOrganize = _mediaFiles.value.filter { it.category == "Uncategorized" }
        if (filesToOrganize.isEmpty()) {
            addAiLog("[AI] Scan complete: No Uncategorized files found.")
            return
        }

        viewModelScope.launch {
            _isOrganizing.value = true
            addAiLog("[AI] Commencing Parallel Agent cataloging. Found ${filesToOrganize.size} files for organization.")
            
            val mutex = Mutex()
            
            // Process files in parallel to optimize deployment throughput
            filesToOrganize.map { file ->
                async {
                    mutex.withLock {
                        addAiLog("[AI] Analyzing \"${file.fileName}\" on background thread...")
                    }
                    
                    val result = GeminiClient.organizeFile(
                        fileName = file.fileName,
                        fileType = file.fileType,
                        sourceApp = file.sourceApp,
                        fileSizeLong = file.fileSize,
                        customRule = _customRule.value,
                        modelName = _selectedAiModel.value,
                        apiKey = _apiKey.value
                    )

                    val updatedFile = file.copy(
                        category = result.category,
                        tags = result.tags.joinToString(", "),
                        aiSummary = result.explanation
                    )
                    repository.update(updatedFile)
                    
                    mutex.withLock {
                        addAiLog("[AI] -> Integrated: ${file.fileName} cataloged into [${result.category}]")
                    }
                    delay(300) // Reduced delay for parallel visualization
                }
            }.awaitAll()
            
            _isOrganizing.value = false
            addAiLog("[AI] Parallel Multi-Agent process finished! Vault consolidated.")
        }
    }

    fun scanDeviceMedia() {
        viewModelScope.launch {
            _isImporting.value = true
            _importProgress.value = 0f
            addSyncLog("[System] Initializing Deep Scan of local device directories...")
            
            val context = getApplication<Application>().applicationContext
            val contentResolver = context.contentResolver
            
            val mediaUris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                listOf(
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                )
            } else {
                listOf(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                )
            }
            
            val foundItems = mutableListOf<MediaFile>()
            
            mediaUris.forEach { uri ->
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATA
                )
                
                contentResolver.query(uri, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                    val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        val name = cursor.getString(nameIndex)
                        val size = cursor.getLong(sizeIndex)
                        val mime = cursor.getString(mimeIndex)
                        val date = cursor.getLong(dateIndex) * 1000
                        val path = cursor.getString(dataIndex)
                        
                        val type = when {
                            mime.startsWith("image/") -> "IMAGE"
                            mime.startsWith("video/") -> "VIDEO"
                            mime.startsWith("audio/") -> "AUDIO"
                            else -> "DOCUMENT"
                        }
                        
                        val sourceApp = detectAppRegistry(path)
                        val contentUri = ContentUris.withAppendedId(uri, id).toString()
                        
                        if (_mediaFiles.value.none { it.fileName == name }) {
                            foundItems.add(MediaFile(
                                fileName = name,
                                fileType = type,
                                sourceApp = sourceApp,
                                fileSize = size,
                                timestamp = date,
                                category = "Uncategorized",
                                syncStatus = "PENDING",
                                localUri = contentUri
                            ))
                        }
                    }
                }
            }
            
            var count = 0
            val total = foundItems.size
            if (total > 0) {
                foundItems.forEachIndexed { index, file ->
                    repository.insert(file)
                    count++
                    addSyncLog("[System] Discovered: ${file.fileName}")
                    _importProgress.value = (index + 1).toFloat() / total
                    delay(50) 
                }
            }
            
            _isImporting.value = false
            if (count > 0) {
                addAiLog("[AI] Scan complete. $count new files indexed. Triggering Organization...")
                autoOrganizeAll()
            } else {
                addAiLog("[AI] Scan complete. No new files found.")
            }
        }
    }

    // Cloud syncing to multiple mirrors based on connected accounts
    fun syncNow() {
        val pendingFiles = _mediaFiles.value.filter { it.syncStatus != "SYNCED" }
        if (pendingFiles.isEmpty()) {
            addSyncLog("[System] Sync checked: All repository mirrors already secure.")
            return
        }

        val accounts = _connectedAccounts.value
        if (accounts.isEmpty()) {
            addSyncLog("[Error] No cloud accounts linked. Cannot initiate mirror sync.")
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgress.value = 0f
            addSyncLog("[Sync Engine] Initializing Multi-Threaded Deployment Matrix for ${accounts.size} mirrors...")
            delay(1000)

            val totalSteps = pendingFiles.size * accounts.size
            var completedSteps = 0
            val mutex = Mutex()

            // Process all pending files in parallel
            pendingFiles.map { file ->
                async {
                    var updatedFile = file
                    
                    // Sync to each connected account
                    accounts.forEach { account ->
                        // Simulate mirror-specific logic based on type
                        val isAlreadySynced = when (account.type) {
                            "Primary" -> updatedFile.primarySyncStatus == "SYNCED"
                            "Backup" -> updatedFile.backupSyncStatus == "SYNCED"
                            "Archive" -> updatedFile.archiveSyncStatus == "SYNCED"
                            "DR" -> updatedFile.disasterRecoverySyncStatus == "SYNCED"
                            else -> false
                        }

                        if (!isAlreadySynced) {
                            delay(300) // Latency simulation
                            
                            val mirrorUrl = "https://${account.provider.lowercase().replace(" ", "-")}.io/u/${file.fileName}"
                            
                            updatedFile = when (account.type) {
                                "Primary" -> updatedFile.copy(primarySyncStatus = "SYNCED", primaryUrl = mirrorUrl)
                                "Backup" -> updatedFile.copy(backupSyncStatus = "SYNCED", backupUrl = mirrorUrl)
                                "Archive" -> updatedFile.copy(archiveSyncStatus = "SYNCED", archiveUrl = mirrorUrl)
                                "DR" -> updatedFile.copy(disasterRecoverySyncStatus = "SYNCED", disasterRecoveryUrl = mirrorUrl)
                                else -> updatedFile
                            }

                            mutex.withLock {
                                completedSteps++
                                _syncProgress.value = completedSteps.toFloat() / totalSteps
                                addSyncLog("[Sync Engine] Mirror ${account.provider} (${account.type}): Secured \"${file.fileName}\"")
                            }
                        }
                    }

                    // Calculate final status based on available accounts
                    val requiredTypes = accounts.map { it.type }.toSet()
                    val isPrimaryOk = !"Primary".isIn(requiredTypes) || updatedFile.primarySyncStatus == "SYNCED"
                    val isBackupOk = !"Backup".isIn(requiredTypes) || updatedFile.backupSyncStatus == "SYNCED"
                    val isArchiveOk = !"Archive".isIn(requiredTypes) || updatedFile.archiveSyncStatus == "SYNCED"
                    val isDrOk = !"DR".isIn(requiredTypes) || updatedFile.disasterRecoverySyncStatus == "SYNCED"

                    val finalStatus = if (isPrimaryOk && isBackupOk && isArchiveOk && isDrOk) "SYNCED" else "PARTIAL"
                    repository.update(updatedFile.copy(syncStatus = finalStatus))
                }
            }.awaitAll()

            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncProgress.value = 1f
            _isSyncing.value = false
            addSyncLog("[Sync Engine] Multi-threaded deployment complete across all connected mirrors.")
        }
    }

    private fun String.isIn(set: Set<String>) = set.contains(this)

    // Consolidate universal repository by pulling definitions from the mirror matrix
    fun consolidateUniversalRepository() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgress.value = 0f
            addSyncLog("[Universal Repo] Initializing cross-device consolidation protocol...")
            delay(1000)
            
            addSyncLog("[Universal Repo] Syncing System Configuration from Mirror Matrix...")
            delay(1000)
            // Simulated pull of remote settings
            val remoteCustomRule = "Always tag invoices as #tax-records"
            if (_customRule.value != remoteCustomRule) {
                _customRule.value = remoteCustomRule
                settingsManager.setString(SettingsManager.KEY_CUSTOM_RULE, remoteCustomRule)
                addSyncLog("[Universal Repo] Updated Classification Rule from remote master.")
            }

            addSyncLog("[Universal Repo] Accessing Global Manifest from Mirror Matrix...")
            delay(800)
            
            // Simulated remote files found on other devices in the same universal repo
            val mockRemoteFiles = listOf(
                MediaFile(
                    fileName = "remote_shared_asset_01.png",
                    fileType = "IMAGE",
                    sourceApp = "Shared (DEV-X92B)",
                    fileSize = 2048576,
                    timestamp = System.currentTimeMillis() - 86400000,
                    category = "Work",
                    syncStatus = "SYNCED",
                    primaryUrl = "https://omni-primary.s3.amazonaws.com/u/remote_shared_asset_01.png",
                    primarySyncStatus = "SYNCED"
                ),
                MediaFile(
                    fileName = "universal_doc_v2.pdf",
                    fileType = "DOCUMENT",
                    sourceApp = "Shared (DEV-L041)",
                    fileSize = 512000,
                    timestamp = System.currentTimeMillis() - 172800000,
                    category = "Finance",
                    syncStatus = "SYNCED",
                    primaryUrl = "https://omni-primary.s3.amazonaws.com/u/universal_doc_v2.pdf",
                    primarySyncStatus = "SYNCED"
                )
            )
            
            var addedCount = 0
            mockRemoteFiles.forEach { remoteFile ->
                if (_mediaFiles.value.none { it.fileName == remoteFile.fileName }) {
                    repository.insert(remoteFile)
                    addedCount++
                    addSyncLog("[Universal Repo] Imported remote reference: ${remoteFile.fileName}")
                }
            }
            
            _syncProgress.value = 1f
            delay(500)
            _isSyncing.value = false
            addSyncLog("[Universal Repo] Consolidation complete. Found $addedCount new remote repository items.")
        }
    }

    private fun detectAppRegistry(path: String): String {
        return when {
            path.contains("WhatsApp", true) -> "WhatsApp"
            path.contains("Telegram", true) -> "Telegram"
            path.contains("Signal", true) -> "Signal"
            path.contains("Instagram", true) -> "Instagram"
            path.contains("Facebook", true) -> "Facebook"
            path.contains("Twitter", true) || path.contains("X-App", true) -> "X/Twitter"
            path.contains("Snapchat", true) -> "Snapchat"
            path.contains("Screenshots", true) -> "Screenshots"
            path.contains("DCIM/Camera", true) -> "Camera"
            path.contains("Download", true) -> "Downloads"
            !path.contains("/emulated/0", true) -> "SD Card"
            else -> {
                // Heuristic: Extract the last folder name before the file name
                val parts = path.split("/")
                if (parts.size >= 2) {
                    val folder = parts[parts.size - 2]
                    if (folder.isNotEmpty() && folder != "0" && folder != "emulated") {
                        folder.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    } else "Device Storage"
                } else "Device Storage"
            }
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
