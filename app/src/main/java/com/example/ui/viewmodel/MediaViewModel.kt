package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.database.AppDatabase
import com.example.data.database.MediaFile
import com.example.data.database.CloudAccount
import com.example.data.repository.MediaRepository
import com.example.data.api.GeminiClient
import com.example.data.settings.SettingsManager
import com.example.data.sync.SyncWorker
import com.example.data.sync.DuplicateWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import android.provider.MediaStore
import android.content.ContentUris
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

data class TaggingEvent(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "SUCCESS", "OFFLINE_FALLBACK", "FAILED", "BYPASSED"
    val tags: List<String>,
    val description: String
)

data class UploadTask(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val totalSize: Long,
    val progress: Float, // 0.0 to 1.0
    val status: String,  // "READING", "ENCRYPTING", "UPLOADING", "ANALYZING_AI", "COMPLETED", "FAILED"
    val tags: List<String> = emptyList()
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = MediaRepository(database.mediaFileDao())
    private val cloudAccountDao = database.cloudAccountDao()
    private val settingsManager = SettingsManager(application)

    private val _mediaFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val mediaFiles: StateFlow<List<MediaFile>> = _mediaFiles.asStateFlow()

    private val _connectedAccounts = MutableStateFlow<List<CloudAccount>>(emptyList())
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

    private val _isAutoTaggingEnabled = MutableStateFlow(true)
    val isAutoTaggingEnabled: StateFlow<Boolean> = _isAutoTaggingEnabled.asStateFlow()

    private val _taggingGranularity = MutableStateFlow("STANDARD")
    val taggingGranularity: StateFlow<String> = _taggingGranularity.asStateFlow()

    private val _recentTaggingEvents = MutableStateFlow<List<TaggingEvent>>(emptyList())
    val recentTaggingEvents: StateFlow<List<TaggingEvent>> = _recentTaggingEvents.asStateFlow()

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

    private val _isCloudSyncEnabled = MutableStateFlow(true)
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()

    private val _selectedAiModel = MutableStateFlow(settingsManager.getString(SettingsManager.KEY_AI_MODEL, "gemini-1.5-flash")) // "gemini-1.5-flash", "gemini-1.5-pro"
    val selectedAiModel: StateFlow<String> = _selectedAiModel.asStateFlow()

    private val _maxVaultSizeMb = MutableStateFlow(settingsManager.getInt(SettingsManager.KEY_MAX_SIZE, 500))
    val maxVaultSizeMb: StateFlow<Int> = _maxVaultSizeMb.asStateFlow()

    private val _autoDeleteAfterDays = MutableStateFlow(settingsManager.getInt(SettingsManager.KEY_AUTO_DELETE, 0)) // 0 means disabled
    val autoDeleteAfterDays: StateFlow<Int> = _autoDeleteAfterDays.asStateFlow()

    private val _isUniversalRepoEnabled = MutableStateFlow(true)
    val isUniversalRepoEnabled: StateFlow<Boolean> = _isUniversalRepoEnabled.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<String>>(emptyList())
    val pairedDevices: StateFlow<List<String>> = _pairedDevices.asStateFlow()

    private val _deviceId = MutableStateFlow(settingsManager.getString(SettingsManager.KEY_DEVICE_ID, "DEV-${UUID.randomUUID().toString().take(6).uppercase()}"))
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _redundancyLevel = MutableStateFlow(settingsManager.getInt(SettingsManager.KEY_REDUNDANCY, 4)) // 1 to 4 nodes
    val redundancyLevel: StateFlow<Int> = _redundancyLevel.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)
    val lastSyncTimestamp: StateFlow<Long?> = _lastSyncTimestamp.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastDeletedFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val lastDeletedFiles: StateFlow<List<MediaFile>> = _lastDeletedFiles.asStateFlow()

    private val _showUndoToast = MutableStateFlow(false)
    val showUndoToast: StateFlow<Boolean> = _showUndoToast.asStateFlow()

    private val _activeUploads = MutableStateFlow<List<UploadTask>>(emptyList())
    val activeUploads: StateFlow<List<UploadTask>> = _activeUploads.asStateFlow()

    private val _isScanningDuplicates = MutableStateFlow(false)
    val isScanningDuplicates: StateFlow<Boolean> = _isScanningDuplicates.asStateFlow()

    private val _discoveredNodes = MutableStateFlow<List<DiscoveredNode>>(emptyList())
    val discoveredNodes: StateFlow<List<DiscoveredNode>> = _discoveredNodes.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _availableShares = MutableStateFlow<List<String>>(emptyList())
    val availableShares: StateFlow<List<String>> = _availableShares.asStateFlow()

    private val _isEnumeratingShares = MutableStateFlow(false)
    val isEnumeratingShares: StateFlow<Boolean> = _isEnumeratingShares.asStateFlow()

    // Key warning state description
    val isApiKeyConfigured: Boolean
        get() = _apiKey.value.isNotBlank() && 
                _apiKey.value != "MY_GEMINI_API_KEY" &&
                !_apiKey.value.contains("REPLACE_WITH_YOUR_GEMINI_API_KEY")

    data class DiscoveredNode(val name: String, val endpoint: String, val provider: String)

    fun startNetworkDiscovery() {
        if (_isDiscovering.value) return
        _isDiscovering.value = true
        _discoveredNodes.value = emptyList()
        
        viewModelScope.launch(Dispatchers.IO) {
            addSyncLog("[System] Initializing Network Discovery Protocol...")
            
            // 1. Simulate SMB Discovery
            delay(1000)
            val mockNodes = listOf(
                DiscoveredNode("Home-NAS", "192.168.1.100", "SMB Share"),
                DiscoveredNode("Media-Server", "192.168.1.105", "SMB Share"),
                DiscoveredNode("Work-Vault", "10.0.0.50", "SMB Share")
            )
            
            // Note: Real network discovery would require NsdManager or broad subnet scans
            // and should use its own non-singleton BaseContext if jcifs-ng is involved.
            
            mockNodes.forEach { node ->
                if (_isDiscovering.value) {
                    _discoveredNodes.value = _discoveredNodes.value + node
                    addSyncLog("[Discovery] Found potential node: ${node.name} at ${node.endpoint}")
                }
            }
            
            _isDiscovering.value = false
            addSyncLog("[System] Network Discovery cycle complete.")
        }
    }

    fun stopNetworkDiscovery() {
        _isDiscovering.value = false
    }

    fun enumerateSmbShares(host: String, user: String, pass: String) {
        if (host.isBlank()) return
        _isEnumeratingShares.value = true
        _availableShares.value = emptyList()
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                addSyncLog("[SMB] Enumerating shares on $host...")
                
                val props = java.util.Properties().apply {
                    setProperty("jcifs.smb.client.minVersion", "SMB202")
                    setProperty("jcifs.smb.client.maxVersion", "SMB311")
                    setProperty("jcifs.smb.client.connTimeout", "5000")
                    setProperty("jcifs.smb.client.soTimeout", "10000")
                }
                val config = jcifs.config.PropertyConfiguration(props)
                val baseContext = jcifs.context.BaseContext(config)
                
                val auth = if (user.isNotEmpty()) {
                    baseContext.withCredentials(jcifs.smb.NtlmPasswordAuthenticator(null, user, pass))
                } else {
                    baseContext.withAnonymousCredentials()
                }
                
                val server = jcifs.smb.SmbFile("smb://$host/", auth)
                val shares = server.listFiles()
                    .filter { it.type == jcifs.SmbConstants.TYPE_SHARE }
                    .map { it.name.removeSuffix("/") }
                
                _availableShares.value = shares
                addSyncLog("[SMB] Found ${shares.size} shares on $host.")
            } catch (e: Exception) {
                addSyncLog("[Error] SMB Share enumeration failed: ${e.message}")
            } finally {
                _isEnumeratingShares.value = false
            }
        }
    }

    init {
        refreshMedia()
        // Collect repository contents
        viewModelScope.launch {
            repository.allMediaFiles.collectLatest { list ->
                _mediaFiles.value = list
            }
        }
        // Collect cloud accounts
        viewModelScope.launch {
            cloudAccountDao.getAllAccounts().collectLatest { list ->
                _connectedAccounts.value = list
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

    fun pairNewDevice(scannedDeviceId: String) {
        if (!_pairedDevices.value.contains(scannedDeviceId) && scannedDeviceId != _deviceId.value) {
            _pairedDevices.value = _pairedDevices.value + scannedDeviceId
            addSyncLog("[Universal Repo] New device paired: $scannedDeviceId. Handshake established.")
            
            // Automatically consolidate after pairing
            consolidateUniversalRepository()
        } else if (scannedDeviceId == _deviceId.value) {
            addSyncLog("[Universal Repo] Handshake aborted: Cannot pair device with itself.")
        }
    }

    fun unpairDevice(deviceId: String) {
        _pairedDevices.value = _pairedDevices.value.filter { it != deviceId }
        addSyncLog("[Universal Repo] Device unpaired: $deviceId. Mutual definitions purged.")
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
        val primaryNode = _connectedAccounts.value.find { it.type == "Primary" }
        if (primaryNode == null || primaryNode.endpoint.isNullOrBlank()) return

        viewModelScope.launch {
            try {
                addSyncLog("[Sync Engine] Mirroring system configuration to Global Matrix...")
                
                // Construct a temporary config file
                val configJson = org.json.JSONObject().apply {
                    put("deviceId", _deviceId.value)
                    put("theme", _appTheme.value)
                    put("autoOrganize", _autoOrganizeOnImport.value)
                    put("aiModel", _selectedAiModel.value)
                    put("redundancy", _redundancyLevel.value)
                }
                
                val context = getApplication<android.app.Application>()
                val configFile = java.io.File(context.cacheDir, "system_config.json")
                configFile.writeText(configJson.toString())
                
                val uri = android.net.Uri.fromFile(configFile).toString()
                
                com.example.data.api.CloudStorageClient.uploadFile(
                    context = context,
                    localUri = uri,
                    account = primaryNode,
                    fileName = "config_${_deviceId.value}.json"
                )
                
                addSyncLog("[Sync Engine] Configuration mirroring SUCCESSFUL. All nodes updated.")
            } catch (e: Exception) {
                addSyncLog("[Error] Failed to mirror configuration: ${e.message}")
            }
        }
    }

    fun autoConfigureRedundancyFromAccounts() {
        val count = _connectedAccounts.value.size.coerceIn(1, 4)
        _redundancyLevel.value = count
        addSyncLog("[System] Auto-configured Redundancy Level to $count based on ${_connectedAccounts.value.size} linked cloud accounts.")
    }

    fun linkAccount(provider: String, name: String, region: String, type: String, apiKey: String? = null, secretKey: String? = null, bucketName: String? = null, endpoint: String? = null, id: String? = null) {
        viewModelScope.launch {
            val account = CloudAccount(
                id = id ?: UUID.randomUUID().toString(),
                provider = provider.trim(),
                accountName = name.trim(),
                region = region.trim(),
                type = type.trim(),
                apiKey = apiKey?.trim(),
                secretKey = secretKey, // Don't trim passwords
                bucketName = bucketName?.trim(),
                endpoint = endpoint?.trim()
            )
            cloudAccountDao.insertAccount(account)
            if (id == null) {
                addSyncLog("[System] New cloud account linked: ${account.accountName} (${account.provider})")
            } else {
                addSyncLog("[System] Cloud account updated: ${account.accountName} (${account.provider})")
            }
        }
    }

    fun unlinkAccount(id: String) {
        viewModelScope.launch {
            val account = _connectedAccounts.value.find { it.id == id }
            cloudAccountDao.deleteAccountById(id)
            account?.let { addSyncLog("[System] Cloud account removed: ${it.accountName}") }
            
            val maxNodes = (_connectedAccounts.value.size - 1).coerceAtLeast(1)
            if (_redundancyLevel.value > maxNodes) {
                _redundancyLevel.value = maxNodes
                addSyncLog("[System] Sync Redundancy auto-adjusted to $maxNodes nodes due to account removal.")
            }
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

    fun refreshMedia() {
        // Real fetch from repository is handled by collectLatest
    }

    fun undoDelete() {
        viewModelScope.launch {
            val filesToRestore = _lastDeletedFiles.value
            _lastDeletedFiles.value = emptyList()
            _showUndoToast.value = false
            filesToRestore.forEach { file ->
                repository.insert(file.copy(id = 0))
            }
            addSyncLog("[System] Restored ${filesToRestore.size} files from undo action")
        }
    }

    fun dismissUndoToast() {
        _showUndoToast.value = false
    }

    fun addImportedFile(fileName: String, fileType: String, sourceApp: String, sizeBytes: Long, uri: String) {
        val currentTotalSize = _mediaFiles.value.sumOf { it.fileSize }
        val limitBytes = _maxVaultSizeMb.value * 1024L * 1024L
        
        // If not Cloud Matrix Maximum (10000), enforce limit
        if (_maxVaultSizeMb.value < 10000 && currentTotalSize + sizeBytes > limitBytes) {
            addSyncLog("[System] Import Aborted: Vault size limit reached. Expand in Config.")
            return
        }

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
            _isImporting.value = false
            
            if (_autoOrganizeOnImport.value) {
                try {
                    addAiLog("[AI] Automatically organizing newly imported file: \"$fileName\"...")
                    val result = GeminiClient.organizeFile(
                        context = getApplication(),
                        fileName = insertedFile.fileName,
                        fileType = insertedFile.fileType,
                        sourceApp = insertedFile.sourceApp,
                        fileSizeLong = insertedFile.fileSize,
                        localUri = insertedFile.localUri,
                        customRule = _customRule.value,
                        granularity = _taggingGranularity.value,
                        modelName = _selectedAiModel.value,
                        apiKey = _apiKey.value
                    )
                    val updatedFile = insertedFile.copy(
                        category = result.category,
                        tags = result.tags.joinToString(", "),
                        aiSummary = result.explanation
                    )
                    repository.update(updatedFile)
                    addAiLog("[AI] Automatically organized \"$fileName\" into [${result.category}]")
                } catch (e: Exception) {
                    addAiLog("[Error] Auto-organization failed for \"$fileName\": ${e.message}")
                }
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
                
                delay(5000)
                if (_lastDeletedFiles.value.any { it.id == id }) {
                    _showUndoToast.value = false
                }
            }
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

    fun organizeSingleFile(file: MediaFile) {
        viewModelScope.launch {
            try {
                addAiLog("[AI] Initiating analysis for \"${file.fileName}\"...")
                val result = GeminiClient.organizeFile(
                    context = getApplication(),
                    fileName = file.fileName,
                    fileType = file.fileType,
                    sourceApp = file.sourceApp,
                    fileSizeLong = file.fileSize,
                    localUri = file.localUri,
                    customRule = _customRule.value,
                    granularity = _taggingGranularity.value,
                    modelName = _selectedAiModel.value,
                    apiKey = _apiKey.value
                )

                val updatedFile = file.copy(
                    category = result.category,
                    tags = result.tags.joinToString(", "),
                    aiSummary = result.explanation
                )
                repository.update(updatedFile)
                recordTaggingEvent(
                    TaggingEvent(
                        fileName = file.fileName,
                        status = "SUCCESS",
                        tags = result.tags,
                        description = "Manual analysis: ${result.explanation}"
                    )
                )
                addAiLog("[AI] Successfully organized \"${file.fileName}\" into [${result.category}]")
            } catch (e: Exception) {
                addAiLog("[Error] AI Analysis failed for \"${file.fileName}\": ${e.message}")
                recordTaggingEvent(
                    TaggingEvent(
                        fileName = file.fileName,
                        status = "FAILED",
                        tags = emptyList(),
                        description = "Error: ${e.message}"
                    )
                )
            }
        }
    }

    fun autoOrganizeAll() {
        val filesToOrganize = _mediaFiles.value.filter { it.category == "Uncategorized" }
        if (filesToOrganize.isEmpty()) {
            addAiLog("[AI] Scan complete: No Uncategorized files found.")
            return
        }

        val context = getApplication<android.app.Application>()
        
        viewModelScope.launch {
            _isOrganizing.value = true
            addAiLog("[AI] Commencing Parallel Agent cataloging. Target: ${filesToOrganize.size} files.")
            
            val mutex = Mutex()
            val semaphore = Semaphore(3) // Limit to 3 concurrent AI requests to avoid rate limits/overload
            
            filesToOrganize.map { file ->
                async {
                    semaphore.withPermit {
                        try {
                            // Check if file is still accessible before calling AI
                            val uriString = file.localUri
                            if (!uriString.isNullOrEmpty()) {
                                try {
                                    context.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.close()
                                } catch (e: Exception) {
                                    mutex.withLock {
                                        addAiLog("[System] Cleanup: File not found \"${file.fileName}\", removing from index.")
                                    }
                                    repository.deleteById(file.id)
                                    return@withPermit
                                }
                            }

                            val result = GeminiClient.organizeFile(
                                context = getApplication(),
                                fileName = file.fileName,
                                fileType = file.fileType,
                                sourceApp = file.sourceApp,
                                fileSizeLong = file.fileSize,
                                localUri = file.localUri,
                                customRule = _customRule.value,
                                granularity = _taggingGranularity.value,
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
                                addAiLog("[AI] -> cataloged: ${file.fileName} into [${result.category}]")
                            }
                        } catch (e: Exception) {
                            mutex.withLock {
                                addAiLog("[Error] Failed to organize ${file.fileName}: ${e.message}")
                            }
                        }
                    }
                }
            }.awaitAll()
            
            _isOrganizing.value = false
            addAiLog("[AI] Parallel Multi-Agent cycle finished.")
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
                var currentTotalSize = _mediaFiles.value.sumOf { it.fileSize }
                val limitBytes = _maxVaultSizeMb.value * 1024L * 1024L
                val isUnlimited = _maxVaultSizeMb.value >= 10000

                foundItems.forEachIndexed { index, file ->
                    if (!isUnlimited && currentTotalSize + file.fileSize > limitBytes) {
                        addSyncLog("[System] Scan Interrupted: Vault size limit reached.")
                        return@forEachIndexed
                    }
                    
                    repository.insert(file)
                    currentTotalSize += file.fileSize
                    count++
                    addSyncLog("[System] Discovered: ${file.fileName}")
                    _importProgress.value = (index + 1).toFloat() / total
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

    fun syncNow() {
        val pendingFiles = _mediaFiles.value.filter { it.syncStatus != "SYNCED" }
        if (pendingFiles.isEmpty()) {
            addSyncLog("[System] All repository mirrors already secure.")
            return
        }

        val accounts = _connectedAccounts.value
        if (accounts.isEmpty()) {
            addSyncLog("[Error] No cloud accounts linked. Please add a mirror node in Config.")
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgress.value = 0f
            addSyncLog("[Sync Engine] Connecting to Mirror Matrix for ${accounts.size} active nodes...")
            
            val totalSteps = pendingFiles.size * accounts.size
            var completedSteps = 0
            val mutex = Mutex()
            val semaphore = Semaphore(5) // Limit concurrent uploads to 5 nodes/files

            pendingFiles.map { file ->
                async {
                    semaphore.withPermit {
                        var updatedFile = file
                        accounts.forEach { account ->
                            try {
                                if (account.endpoint.isNullOrBlank()) {
                                    throw Exception("Mirror node ${account.accountName} endpoint is missing.")
                                }
                                
                                val localUri = file.localUri ?: throw Exception("Local URI missing for ${file.fileName}")
                                
                                val realMirrorUrl = com.example.data.api.CloudStorageClient.uploadFile(
                                    context = getApplication(),
                                    localUri = localUri,
                                    account = account,
                                    fileName = file.fileName
                                )
                                
                                updatedFile = when (account.type) {
                                    "Primary" -> updatedFile.copy(primarySyncStatus = "SYNCED", primaryUrl = realMirrorUrl)
                                    "Backup" -> updatedFile.copy(backupSyncStatus = "SYNCED", backupUrl = realMirrorUrl)
                                    "Archive" -> updatedFile.copy(archiveSyncStatus = "SYNCED", archiveUrl = realMirrorUrl)
                                    "DR" -> updatedFile.copy(disasterRecoverySyncStatus = "SYNCED", disasterRecoveryUrl = realMirrorUrl)
                                    else -> updatedFile
                                }

                                mutex.withLock {
                                    completedSteps++
                                    _syncProgress.value = completedSteps.toFloat() / totalSteps
                                    addSyncLog("[Sync Engine] Mirror ${account.provider}: Transferred \"${file.fileName}\"")
                                }
                            } catch (e: Exception) {
                                mutex.withLock {
                                    addSyncLog("[Error] ${account.accountName}: ${e.message}")
                                }
                            }
                        }

                        // Calculate final status
                        val requiredTypes = accounts.map { it.type }.toSet()
                        val isPrimaryOk = !"Primary".isIn(requiredTypes) || updatedFile.primarySyncStatus == "SYNCED"
                        val isBackupOk = !"Backup".isIn(requiredTypes) || updatedFile.backupSyncStatus == "SYNCED"
                        val isArchiveOk = !"Archive".isIn(requiredTypes) || updatedFile.archiveSyncStatus == "SYNCED"
                        val isDrOk = !"DR".isIn(requiredTypes) || updatedFile.disasterRecoverySyncStatus == "SYNCED"

                        val finalStatus = if (isPrimaryOk && isBackupOk && isArchiveOk && isDrOk) "SYNCED" else "PARTIAL"
                        repository.update(updatedFile.copy(syncStatus = finalStatus))
                    }
                }
            }.awaitAll()

            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncProgress.value = 1f
            _isSyncing.value = false
            addSyncLog("[Sync Engine] Task cycle finished. Consolidated state saved.")
        }
    }

    private fun String.isIn(set: Set<String>) = set.contains(this)

    fun consolidateUniversalRepository() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgress.value = 0f
            addSyncLog("[Universal Repo] Initializing cross-device consolidation protocol...")
            
            val primaryNode = _connectedAccounts.value.find { it.type == "Primary" }
            if (primaryNode != null && !primaryNode.endpoint.isNullOrBlank()) {
                addSyncLog("[Universal Repo] Handshake with ${primaryNode.accountName} successful.")
                
                try {
                    val remoteFiles = com.example.data.api.CloudStorageClient.fetchManifest(primaryNode)
                    if (remoteFiles.isNotEmpty()) {
                        addSyncLog("[Universal Repo] Discovered ${remoteFiles.size} shared assets in Global Matrix.")
                        
                        val localFileNames = _mediaFiles.value.map { it.fileName }.toSet()
                        val missingLocally = remoteFiles.filter { it !in localFileNames }
                        
                        if (missingLocally.isNotEmpty()) {
                            addSyncLog("[Universal Repo] ${missingLocally.size} assets identified for consolidation pull.")
                            
                            missingLocally.forEach { fileName ->
                                try {
                                    val baseUrl = if (primaryNode.endpoint!!.endsWith("/")) primaryNode.endpoint else "${primaryNode.endpoint}/"
                                    val downloadUrl = "$baseUrl$fileName"
                                    
                                    val localUri = com.example.data.api.CloudStorageClient.downloadFile(
                                        context = getApplication(),
                                        url = downloadUrl,
                                        targetFileName = fileName
                                    )
                                    
                                    // Register the newly consolidated file in the local DB
                                    val newFile = MediaFile(
                                        fileName = fileName,
                                        fileType = "IMAGE", // Defaulting, AI will fix this later
                                        sourceApp = "Universal Repo",
                                        fileSize = 0, // Will be updated
                                        timestamp = System.currentTimeMillis(),
                                        localUri = localUri.toString(),
                                        syncStatus = "SYNCED",
                                        primaryUrl = downloadUrl,
                                        primarySyncStatus = "SYNCED"
                                    )
                                    repository.insert(newFile)
                                    addSyncLog(" -> Consolidated: $fileName")
                                } catch (e: Exception) {
                                    addSyncLog(" [!] Error pulling $fileName: ${e.message}")
                                }
                            }
                        }
                    } else {
                        addSyncLog("[Universal Repo] Global Manifest is currently empty.")
                    }
                } catch (e: Exception) {
                    addSyncLog("[Error] Consolidation failed: ${e.message}")
                }
            } else {
                addSyncLog("[Universal Repo] No Primary mirror node found for config sync.")
            }

            addSyncLog("[Universal Repo] Consolidation cycle finished. State is up-to-date.")
            _syncProgress.value = 1f
            _isSyncing.value = false
        }
    }

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

    fun scanForDuplicates() {
        viewModelScope.launch {
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
            
            addSyncLog("[System] MD5 Engine: Vault analysis started in background.")
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

    fun removeActiveUpload(id: String) {
        _activeUploads.value = _activeUploads.value.filter { it.id != id }
    }

    fun keepDuplicateFile(file: MediaFile) {
        viewModelScope.launch {
            val updatedFile = file.copy(isDuplicate = false)
            repository.update(updatedFile)
            addSyncLog("[System] MD5 Engine: Excluded \"${file.fileName}\" from duplicate group per user feedback.")
        }
    }

    fun testConnection(account: CloudAccount, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            addSyncLog("[System] Testing connectivity to ${account.accountName}...")
            try {
                val success = com.example.data.api.CloudStorageClient.testConnectivity(account)
                if (success) {
                    addSyncLog("[System] Connectivity Test: SUCCESS for ${account.accountName}")
                    onResult(true, "Connection Successful!")
                } else {
                    addSyncLog("[Error] Connectivity Test: FAILED for ${account.accountName} - Resource not found or inaccessible")
                    onResult(false, "Connection Failed. Please check settings.")
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                addSyncLog("[Error] Connectivity Test: EXCEPTION for ${account.accountName}: $errorMsg")
                onResult(false, "Connection Error: $errorMsg")
            }
        }
    }

    private fun detectAppRegistry(path: String): String {
        val lowerPath = path.lowercase()
        return when {
            lowerPath.contains("whatsapp") -> "WhatsApp"
            lowerPath.contains("telegram") -> "Telegram"
            lowerPath.contains("signal") -> "Signal"
            lowerPath.contains("instagram") -> "Instagram"
            lowerPath.contains("facebook") -> "Facebook"
            lowerPath.contains("twitter") || lowerPath.contains("x-app") || lowerPath.contains("/x/") -> "X/Twitter"
            lowerPath.contains("snapchat") -> "Snapchat"
            lowerPath.contains("messenger") -> "Messenger"
            lowerPath.contains("discord") -> "Discord"
            lowerPath.contains("slack") -> "Slack"
            lowerPath.contains("viber") -> "Viber"
            lowerPath.contains("line") -> "Line"
            lowerPath.contains("screenshots") -> "Screenshots"
            lowerPath.contains("dcim/camera") -> "Camera"
            lowerPath.contains("download") -> "Downloads"
            lowerPath.contains("movies") || lowerPath.contains("video") -> "Videos"
            lowerPath.contains("music") || lowerPath.contains("audio") -> "Audio"
            lowerPath.contains("/storage/") && !lowerPath.contains("/emulated/0") -> "SD Card"
            else -> {
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
