package com.example

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.database.MediaFile
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MediaViewModel
import com.example.ui.viewmodel.UploadTask
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val app = applicationContext as Application
                val viewModel: MediaViewModel = viewModel(
                    factory = MediaViewModel.Factory(app)
                )
                MediaVaultApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaVaultApp(viewModel: MediaViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val mediaFiles by viewModel.mediaFiles.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Media importer launcher (Storage Access Framework)
    val contentResolver = context.contentResolver
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            var name = "imported_${UUID.randomUUID().toString().take(6)}"
            var size = 0L
            try {
                val cursor = contentResolver.query(it, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (nameIndex != -1) name = c.getString(nameIndex)
                        if (sizeIndex != -1) size = c.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {
                // Heuristic mapping if metadata query fails
            }

            val lowerName = name.lowercase()
            val type = when {
                lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif") -> "IMAGE"
                lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".avi") || lowerName.endsWith(".mov") -> "VIDEO"
                lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") || lowerName.endsWith(".ogg") -> "AUDIO"
                else -> "DOCUMENT"
            }

            viewModel.addImportedFile(
                fileName = name,
                fileType = type,
                sourceApp = "Device Storage",
                sizeBytes = if (size > 0L) size else 1024L * 150, // Default 150kb
                uri = it.toString()
            )
            Toast.makeText(context, "$name registered in Secure Repository!", Toast.LENGTH_SHORT).show()
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0F1A35),
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
                windowInsets = WindowInsets(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Drawer Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Safe Lock Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "CIPHER VAULT",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "Zero-Knowledge Sandbox",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.LightGray.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "SECURE DECK NAVIGATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            letterSpacing = 1.2.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Navigation Options
                    val tabs = listOf(
                        Triple(0, "Vault Files", Icons.Default.Menu),
                        Triple(1, "Cloud Sync", Icons.Default.Refresh),
                        Triple(2, "AI Organizer", Icons.Default.Settings)
                    )

                    tabs.forEach { (index, title, icon) ->
                        val isSelected = selectedTab == index
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = if (isSelected) Color.White else Color.LightGray
                                )
                            },
                            label = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color.LightGray
                                    )
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                viewModel.selectTab(index)
                                scope.launch { drawerState.close() }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                unselectedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag("drawer_tab_$index")
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Storage Quota Indicator Section
                    val totalCapacityBytes = 512 * 1024 * 1024L // 512 MB
                    val usedBytes = mediaFiles.sumOf { it.fileSize }
                    val usedFraction = (usedBytes.toFloat() / totalCapacityBytes).coerceIn(0f, 1f)
                    val percentUsedStr = "%.1f".format(usedFraction * 100f)
                    
                    val formattedUsed = "%.1f MB".format(usedBytes / (1024f * 1024f))
                    val formattedCapacity = "512.0 MB"

                    val quotaColor = when {
                        usedFraction > 0.9f -> Color(0xFFEF4444)
                        usedFraction > 0.7f -> Color(0xFFF59E0B)
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("storage_quota_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Storage Status",
                                        tint = quotaColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "STORAGE QUOTA",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.LightGray,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        )
                                    )
                                }
                                Text(
                                    text = "$percentUsedStr%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = quotaColor,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.testTag("quota_percentage_text")
                                )
                            }

                            LinearProgressIndicator(
                                progress = { usedFraction },
                                color = quotaColor,
                                trackColor = Color.White.copy(alpha = 0.1f),
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .testTag("quota_progress_bar")
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Used:",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 11.sp)
                                    )
                                    Text(
                                        text = formattedUsed,
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                        modifier = Modifier.testTag("quota_used_text")
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Limit:",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 11.sp)
                                    )
                                    Text(
                                        text = formattedCapacity,
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                        modifier = Modifier.testTag("quota_limit_text")
                                    )
                                }
                            }

                            if (usedFraction > 0.9f) {
                                Text(
                                    text = "⚠️ Storage almost full! Sync uploads or purge locally cached directories.",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFFEF08A),
                                        fontSize = 9.sp,
                                        lineHeight = 11.sp
                                    )
                                )
                            } else {
                                Text(
                                    text = "Automated high-fidelity sandbox encryption allocates isolated local sectors dynamically.",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.Gray,
                                        fontSize = 9.sp,
                                        lineHeight = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_navigation_bar"),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        icon = { Icon(Icons.Default.Menu, contentDescription = "Vault Files") },
                        label = { Text("Vault Files") },
                        modifier = Modifier.testTag("tab_vault")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        icon = { Icon(Icons.Default.Refresh, contentDescription = "Cloud Sync") },
                        label = { Text("Cloud Sync") },
                        modifier = Modifier.testTag("tab_sync")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "AI Organizer") },
                        label = { Text("AI Organizer") },
                        modifier = Modifier.testTag("tab_ai")
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Background cosmic gradients
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF0F1A35),
                                Color(0xFF070B19)
                            )
                        )
                    )
                }

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        slideInHorizontally { width -> if (targetState > initialState) width else -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> if (targetState > initialState) -width else width } + fadeOut()
                    },
                    label = "tab_fade"
                ) { tab ->
                    when (tab) {
                        0 -> VaultView(
                            mediaFiles = mediaFiles,
                            viewModel = viewModel,
                            onImportClick = {
                                try {
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "File picking not supported under this architecture.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onMenuClick = {
                                scope.launch { drawerState.open() }
                            }
                        )
                        1 -> SyncView(
                            viewModel = viewModel,
                            onMenuClick = {
                                scope.launch { drawerState.open() }
                            }
                        )
                        2 -> AIOrganizerView(
                            mediaFiles = mediaFiles,
                            viewModel = viewModel,
                            onMenuClick = {
                                scope.launch { drawerState.open() }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultView(
    mediaFiles: List<MediaFile>,
    viewModel: MediaViewModel,
    onImportClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    val activeUploads by viewModel.activeUploads.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showUndoToast by viewModel.showUndoToast.collectAsStateWithLifecycle()
    val lastDeletedFiles by viewModel.lastDeletedFiles.collectAsStateWithLifecycle()
    var selectedAppFilter by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFileForDetailId by remember { mutableStateOf<Int?>(null) }
    val selectedFileForDetail = remember(selectedFileForDetailId, mediaFiles) {
        mediaFiles.find { it.id == selectedFileForDetailId }
    }
    var fullScreenViewerFileId by remember { mutableStateOf<Int?>(null) }
    val fullScreenViewerFile = remember(fullScreenViewerFileId, mediaFiles) {
        mediaFiles.find { it.id == fullScreenViewerFileId }
    }
    var viewMode by remember { mutableStateOf("grid") }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Relevance") }
    
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedFileIds = remember { mutableStateListOf<Int>() }
    var showBulkTagsDialog by remember { mutableStateOf(false) }
    var bulkTagsInput by remember { mutableStateOf("") }
    var showBulkMoveDialog by remember { mutableStateOf(false) }
    var bulkCategoryInput by remember { mutableStateOf("") }
    
    var startDateMills by remember { mutableStateOf<Long?>(null) }
    var endDateMills by remember { mutableStateOf<Long?>(null) }
    var selectedDatePresetText by remember { mutableStateOf("All Time") }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showSearchFiltersHelp by remember { mutableStateOf(false) }

    val tagSearchFrequency = remember {
        mutableStateMapOf<String, Int>().apply {
            put("receipt", 6)
            put("funny", 8)
            put("music", 4)
            put("statement", 5)
            put("invoice", 7)
            put("meme", 9)
            put("diagram", 3)
            put("payment", 4)
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            val q = searchQuery.trim().lowercase()
            mediaFiles.forEach { file ->
                file.tags.split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .forEach { t ->
                        if (t == q || t.contains(q)) {
                            tagSearchFrequency[t] = (tagSearchFrequency[t] ?: 0) + 1
                        }
                    }
            }
        }
    }

    // Categories derived dynamically from file registry to display horizontal filter chips
    val categories = listOf("All") + mediaFiles.map { it.category }.distinct()

    val aiTags = remember(mediaFiles) {
        mediaFiles.flatMap { file ->
            file.tags.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }.distinct().sorted()
    }

    val filteredFiles = remember(mediaFiles, selectedCategoryFilter, selectedAppFilter, searchQuery, startDateMills, endDateMills) {
        mediaFiles.filter { file ->
            val matchCategory = selectedCategoryFilter == "All" || file.category == selectedCategoryFilter
            val matchApp = selectedAppFilter == "All" || file.sourceApp.equals(selectedAppFilter, ignoreCase = true)
            
            val matchDateRange = when {
                startDateMills != null && endDateMills != null -> {
                    file.timestamp in startDateMills!!..endDateMills!!
                }
                startDateMills != null -> {
                    file.timestamp >= startDateMills!!
                }
                endDateMills != null -> {
                    file.timestamp <= endDateMills!!
                }
                else -> true
            }
            
            val matchSearchQuery = matchMetadataSearch(file, searchQuery)
            matchCategory && matchApp && matchSearchQuery && matchDateRange
        }
    }

    val sortedFiles = remember(filteredFiles, sortBy, tagSearchFrequency, searchQuery) {
        when (sortBy) {
            "Relevance" -> {
                filteredFiles.sortedWith(
                    compareByDescending<MediaFile> { calculateRelevance(it, searchQuery, tagSearchFrequency) }
                        .thenByDescending { it.timestamp }
                )
            }
            "Newest" -> {
                filteredFiles.sortedByDescending { it.timestamp }
            }
            "Size" -> {
                filteredFiles.sortedByDescending { it.fileSize }
            }
            else -> filteredFiles
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
        // Upper Title Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("vault_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Sidebar Navigation Menu",
                        tint = Color.White
                    )
                }
                Column {
                    Text(
                        text = "Secure Vault",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "${mediaFiles.size} Items Indexed Locally",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onImportClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("import_file_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Import file", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import")
                }

                FilledIconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("show_add_dialog_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Simulate Inbound File")
                }

                FilledIconButton(
                    onClick = { viewModel.refreshMedia() },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("refresh_media_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh & Fetch Media")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "CONNECTED APP REGISTRIES",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                letterSpacing = 1.2.sp
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Source Connected Apps Hub
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val monitoredApps = listOf("All", "WhatsApp", "Telegram", "Screenshots", "Camera", "Downloads", "Discord", "Slack")
            monitoredApps.forEach { app ->
                val count = if (app == "All") mediaFiles.size else mediaFiles.count { it.sourceApp.equals(app, ignoreCase = true) }
                val sizeBytes = if (app == "All") mediaFiles.sumOf { it.fileSize } else mediaFiles.filter { it.sourceApp.equals(app, ignoreCase = true) }.sumOf { it.fileSize }
                val sizeMb = sizeBytes / (1024f * 1024f)
                val isSelected = selectedAppFilter == app

                val appColor = when (app) {
                    "WhatsApp" -> Color(0xFF25D366)
                    "Telegram" -> Color(0xFF0088CC)
                    "Screenshots" -> Color(0xFF9C27B0)
                    "Camera" -> Color(0xFFFF9800)
                    "Downloads" -> Color(0xFF78909C)
                    "Discord" -> Color(0xFF5865F2)
                    "Slack" -> Color(0xFFE01E5A)
                    else -> MaterialTheme.colorScheme.primary
                }

                Card(
                    modifier = Modifier
                        .width(130.dp)
                        .clickable { selectedAppFilter = app }
                        .testTag("source_app_card_$app"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) appColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
                    ),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (isSelected) appColor else Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(appColor)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(appColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = appColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }

                        Text(
                            text = app,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "%.1f MB".format(sizeMb),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }

        // Secure Media Upload & AI Tagging Component
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("secure_upload_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF16223F).copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, Color(0xFF2C3E6B))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secure Encryption Lock Icon",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SECURE VAULT UPLOADER & EYE-WITNESS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2196F3).copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "GEMINI AI INTEGRATED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF2196F3),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Upload click-zone
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onImportClick() }
                        .padding(vertical = 14.dp, horizontal = 12.dp)
                        .testTag("tap_to_upload_zone"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Upload Plus Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Tap to securely upload/import media",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Locally encrypted via AES-256 and automatically auto-tagged by Gemini AI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                // If any active uploads, list them!
                if (activeUploads.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ACTIVE UPLOAD & TAG QUEUE (${activeUploads.size}):",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeUploads.forEach { task ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.05f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                                modifier = Modifier.fillMaxWidth().testTag("upload_task_${task.id}")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.fileName,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Size: %.2f MB".format(task.totalSize / (1024f * 1024f)),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.Gray,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }

                                        // Status badge
                                        val statusColor = when (task.status) {
                                            "ENCRYPTING" -> Color(0xFFFFA726)
                                            "UPLOADING" -> Color(0xFF29B6F6)
                                            "ANALYZING_AI" -> Color(0xFFAB47BC)
                                            "COMPLETED" -> Color(0xFF66BB6A)
                                            else -> Color(0xFFEF5350)
                                        }
                                        val statusText = when (task.status) {
                                            "ENCRYPTING" -> "🛡️ ENCRYPTING..."
                                            "UPLOADING" -> "📤 BLOCK TRANSFER..."
                                            "ANALYZING_AI" -> "🧠 GEMINI TAGGING..."
                                            "COMPLETED" -> "✅ SECURELY ARCHIVED"
                                            else -> "❌ ERROR"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(statusColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = statusText,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = statusColor,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }

                                        // Close/remove button only if completed
                                        if (task.status == "COMPLETED" || task.status == "FAILED") {
                                            IconButton(
                                                onClick = { viewModel.removeActiveUpload(task.id) },
                                                modifier = Modifier.size(24.dp).padding(start = 4.dp).testTag("dismiss_upload_${task.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Dismiss Active Upload View",
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Progress bar
                                    LinearProgressIndicator(
                                        progress = { task.progress },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).testTag("upload_progress_${task.id}"),
                                        color = if (task.status == "COMPLETED") Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                        trackColor = Color.White.copy(alpha = 0.1f)
                                    )

                                    if (task.status == "COMPLETED" && task.tags.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "AI tags generated:",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.LightGray,
                                                    fontSize = 10.sp
                                                )
                                            )
                                            task.tags.take(3).forEach { tag ->
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color.White.copy(alpha = 0.1f))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "#$tag",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Color.LightGray,
                                                            fontSize = 9.sp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Warning banner if API key is not configured
        if (!viewModel.isApiKeyConfigured) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFD32F2F).copy(alpha = 0.2f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFD32F2F)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "API warning key missing",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Offline Mode Active (No API Key)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Simulate real Gemini categorization or configure your GEMINI_API_KEY inside the Secrets Panel.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                        )
                    }
                }
            }
        }

        // Search Input Component
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search tags, categories, filenames, metadata...", color = Color.Gray) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Icon",
                    tint = Color.LightGray
                )
            },
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    IconButton(
                        onClick = { showSearchFiltersHelp = !showSearchFiltersHelp },
                        modifier = Modifier.testTag("search_help_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Search Guidelines & Metadata Help",
                            tint = if (showSearchFiltersHelp) MaterialTheme.colorScheme.primary else Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.testTag("clear_search_input_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Search",
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("media_search_input"),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
            )
        )

        // Interactive Advanced Search Guide / Help Card
        if (showSearchFiltersHelp) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF141F39)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .animateContentSize()
                    .testTag("search_metadata_guide_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "ADVANCED SEARCH METADATA FILTERS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                        TextButton(
                            onClick = { showSearchFiltersHelp = false },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp).testTag("hide_search_guide_button")
                        ) {
                            Text("HIDE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.Gray))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Query your library by searching for filename terms, AI tags, or enter structured metadata tokens to narrow down files. Click an example below to insert it:",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    val shortcuts = listOf(
                        "type:image" to "Filter library files by Category Image (or 'type:video')",
                        "app:WhatsApp" to "Search originating source application (WhatsApp, Camera, etc.)",
                        "ext:jpg" to "Filter precise file formats/extensions (jpg, png, mp4)",
                        "size>1MB" to "Logical file size comparison (e.g. size<500KB)",
                        "#receipt" to "Seek specific AI classified tag names"
                    )
                    
                    shortcuts.forEach { (query, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    searchQuery = query
                                }
                                .testTag("search_shortcut_$query"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = query,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 11.sp),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Dynamic AI Tag Quick-Filters Row
        if (aiTags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "AI Tags",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "AI Tags Filter:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
                aiTags.forEach { tag ->
                    val isTagActive = searchQuery == "#$tag"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isTagActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .border(
                                1.dp,
                                if (isTagActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                searchQuery = if (isTagActive) "" else "#$tag"
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("ai_tag_chip_$tag")
                    ) {
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isTagActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }

        // Horizontal filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = cat == selectedCategoryFilter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategoryFilter = cat },
                    label = { Text(cat) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        labelColor = Color.White,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chronological Date-Range Preset Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Date:",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
            )
            
            val datePresets = listOf("All Time", "Today", "Last 7 Days", "Last 30 Days", "Custom Range")
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                datePresets.forEach { preset ->
                    val isSelected = selectedDatePresetText == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .clickable {
                                selectedDatePresetText = preset
                                when (preset) {
                                    "All Time" -> {
                                        startDateMills = null
                                        endDateMills = null
                                    }
                                    "Today" -> {
                                        val cal = Calendar.getInstance()
                                        startDateMills = getStartOfDay(cal)
                                        endDateMills = getEndOfDay(cal)
                                    }
                                    "Last 7 Days" -> {
                                        val cal = Calendar.getInstance()
                                        endDateMills = getEndOfDay(cal)
                                        cal.add(Calendar.DAY_OF_YEAR, -7)
                                        startDateMills = getStartOfDay(cal)
                                    }
                                    "Last 30 Days" -> {
                                        val cal = Calendar.getInstance()
                                        endDateMills = getEndOfDay(cal)
                                        cal.add(Calendar.DAY_OF_YEAR, -30)
                                        startDateMills = getStartOfDay(cal)
                                    }
                                    "Custom Range" -> {
                                        showDatePickerDialog = true
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("date_preset_$preset")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (preset == "Custom Range") {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Custom Date Range Icon",
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = if (preset == "Custom Range" && startDateMills != null && endDateMills != null) {
                                    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
                                    "${sdf.format(Date(startDateMills!!))} - ${sdf.format(Date(endDateMills!!))}"
                                } else {
                                    preset
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Sorting Option Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Sort by:",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
            )
            listOf("Relevance", "Newest", "Size").forEach { opt ->
                val isSelected = sortBy == opt
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.05f)
                        )
                        .clickable { sortBy = opt }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("sort_opt_$opt")
                ) {
                    Text(
                        text = when (opt) {
                            "Relevance" -> "✦ Relevance"
                            "Newest" -> "🕰 Newest"
                            "Size" -> "💾 Size"
                            else -> opt
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Grid/List toggle toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredFiles.size} items matching",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Multi-select toggle button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isMultiSelectMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f))
                        .border(
                            1.dp, 
                            if (isMultiSelectMode) MaterialTheme.colorScheme.primary else Color.Transparent, 
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            isMultiSelectMode = !isMultiSelectMode
                            if (!isMultiSelectMode) {
                                selectedFileIds.clear()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("toggle_multiselect")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isMultiSelectMode) Icons.Default.CheckCircle else Icons.Default.List,
                            contentDescription = "Multi Select Toggle",
                            tint = if (isMultiSelectMode) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isMultiSelectMode) "Select Mode" else "Select",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewMode == "list") MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewMode = "list" }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("toggle_list_view")
                    ) {
                        Text(
                            text = "List",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewMode == "grid") MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewMode = "grid" }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("toggle_grid_view")
                    ) {
                        Text(
                            text = "Grid",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Files Grid Explorer
        if (isLoading) {
            if (viewMode == "grid") {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(6) {
                        MediaFileGridCardSkeleton()
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(5) {
                        MediaFileRowSkeleton()
                    }
                }
            }
        } else if (filteredFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "No media files in repository",
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No files match active filter.",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                    )
                    Text(
                        text = "Add simulated files or import actual resources.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                    )
                }
            }
        } else {
            if (viewMode == "grid") {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(sortedFiles) { index, file ->
                        val isSelected = selectedFileIds.contains(file.id)
                        AnimatedEntranceContainer(index = index) {
                            MediaFileGridCard(
                                file = file,
                                onOrganizeClick = { viewModel.organizeSingleFile(file) },
                                onDeleteClick = { viewModel.deleteFile(file.id) },
                                onCardClick = {
                                    if (isMultiSelectMode) {
                                        if (isSelected) {
                                            selectedFileIds.remove(file.id)
                                        } else {
                                            selectedFileIds.add(file.id)
                                        }
                                    } else {
                                        fullScreenViewerFileId = file.id
                                    }
                                },
                                onTagClick = { tag ->
                                    searchQuery = tag
                                    val key = tag.lowercase().trim()
                                    tagSearchFrequency[key] = (tagSearchFrequency[key] ?: 0) + 1
                                },
                                relevanceScore = calculateRelevance(file, searchQuery, tagSearchFrequency),
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = isSelected,
                                onLongClick = {
                                    if (!isMultiSelectMode) {
                                        isMultiSelectMode = true
                                        selectedFileIds.clear()
                                        selectedFileIds.add(file.id)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(sortedFiles) { index, file ->
                        val isSelected = selectedFileIds.contains(file.id)
                        AnimatedEntranceContainer(index = index) {
                            MediaFileRow(
                                file = file,
                                onOrganizeClick = { viewModel.organizeSingleFile(file) },
                                onDeleteClick = { viewModel.deleteFile(file.id) },
                                onCardClick = {
                                    if (isMultiSelectMode) {
                                        if (isSelected) {
                                            selectedFileIds.remove(file.id)
                                        } else {
                                            selectedFileIds.add(file.id)
                                        }
                                    } else {
                                        fullScreenViewerFileId = file.id
                                    }
                                },
                                onTagClick = { tag ->
                                    searchQuery = tag
                                    val key = tag.lowercase().trim()
                                    tagSearchFrequency[key] = (tagSearchFrequency[key] ?: 0) + 1
                                },
                                relevanceScore = calculateRelevance(file, searchQuery, tagSearchFrequency),
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = isSelected,
                                onLongClick = {
                                    if (!isMultiSelectMode) {
                                        isMultiSelectMode = true
                                        selectedFileIds.clear()
                                        selectedFileIds.add(file.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Animated Batch Action Bar Overlay
        AnimatedVisibility(
            visible = isMultiSelectMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("batch_action_bar")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // TOP Row: Title & count, selection toggle, close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Selection Count",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${selectedFileIds.size} files selected",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Select All / Deselect All Toggle button
                            TextButton(
                                onClick = {
                                    if (selectedFileIds.size == sortedFiles.size) {
                                        selectedFileIds.clear()
                                    } else {
                                        selectedFileIds.clear()
                                        selectedFileIds.addAll(sortedFiles.map { it.id })
                                    }
                                },
                                modifier = Modifier.testTag("batch_select_all_toggle")
                            ) {
                                Text(
                                    text = if (selectedFileIds.size == sortedFiles.size) "Deselect All" else "Select All",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            // Done / Cancel button
                            IconButton(
                                onClick = {
                                    isMultiSelectMode = false
                                    selectedFileIds.clear()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .testTag("batch_cancel_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Multi-select",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

                    // BOTTOM Row: List of operations (Delete / Move to folder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "Delete Selected" Button
                        Button(
                            onClick = {
                                if (selectedFileIds.isNotEmpty()) {
                                    viewModel.deleteMultipleFiles(selectedFileIds.toList())
                                    selectedFileIds.clear()
                                    isMultiSelectMode = false
                                }
                            },
                            enabled = selectedFileIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444),
                                disabledContainerColor = Color(0xFFEF4444).copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(0.9f)
                                .testTag("batch_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Delete", 
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        // Dropdown Menu state to trigger "Move to Folder" dropdown
                        var showMoveMenu by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Button(
                                onClick = { showMoveMenu = true },
                                enabled = selectedFileIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("batch_move_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Move to folder",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Move To...", 
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            val standardCategories = listOf("Finance", "Memes", "Work", "Recordings", "Screenshots", "Personal", "Uncategorized")
                            DropdownMenu(
                                expanded = showMoveMenu,
                                onDismissRequest = { showMoveMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    .testTag("batch_move_dropdown")
                            ) {
                                standardCategories.forEach { categoryName ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                categoryName, 
                                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                            ) 
                                        },
                                        onClick = {
                                            viewModel.moveMultipleFilesToCategory(selectedFileIds.toList(), categoryName)
                                            selectedFileIds.clear()
                                            isMultiSelectMode = false
                                            showMoveMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Share, 
                                                contentDescription = null,
                                                tint = when (categoryName) {
                                                    "Finance" -> Color(0xFF4CAF50)
                                                    "Memes" -> Color(0xFFFF9800)
                                                    "Work" -> Color(0xFF2196F3)
                                                    "Recordings" -> Color(0xFFE91E63)
                                                    "Screenshots" -> Color(0xFF9C27B0)
                                                    else -> Color.LightGray
                                                },
                                                modifier = Modifier.size(16.dp)
                                              )
                                        },
                                        modifier = Modifier.testTag("batch_move_destination_$categoryName")
                                    )
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            "+ Custom Category...", 
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.primary, 
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) 
                                    },
                                    onClick = {
                                        bulkCategoryInput = ""
                                        showMoveMenu = false
                                        showBulkMoveDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Add, 
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("batch_move_destination_custom")
                                )
                            }
                        }

                        // "Add Custom Tags" Button
                        Button(
                            onClick = { 
                                bulkTagsInput = ""
                                showBulkTagsDialog = true 
                            },
                            enabled = selectedFileIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.0f)
                                .testTag("batch_tags_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Add tags in bulk",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Add Tags", 
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            // Undo deletion Toast notification overlay
            AnimatedVisibility(
                visible = showUndoToast && lastDeletedFiles.isNotEmpty(),
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it }
                ) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                    .zIndex(100f)
            ) {
                val count = lastDeletedFiles.size
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 500.dp)
                        .testTag("undo_deletion_toast"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E2A4A)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    border = BorderStroke(1.dp, Color(0xFF3B5B9E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Deleted items indicator",
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (count == 1) {
                                    "Deleted \"${lastDeletedFiles.first().fileName}\""
                                } else {
                                    "Deleted $count secure items"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { viewModel.undoDelete() },
                                modifier = Modifier.testTag("undo_delete_button"),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Color(0xFF81C784)
                                )
                            ) {
                                Text(
                                    text = "UNDO",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                            IconButton(
                                onClick = { viewModel.dismissUndoToast() },
                                modifier = Modifier.size(28.dp).testTag("dismiss_undo_toast")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss deletion feedback",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog Sheet / Immersive Custom Overlay Modal Component
    if (selectedFileForDetail != null) {
        val fileDetail = selectedFileForDetail!!
        val isOrganizing by viewModel.isOrganizing.collectAsStateWithLifecycle()
        MediaDetailOverlayModal(
            file = fileDetail,
            isOrganizing = isOrganizing,
            onDismissRequest = { selectedFileForDetailId = null },
            onOrganizeClick = { 
                viewModel.organizeSingleFile(fileDetail)
            },
            onDeleteClick = {
                viewModel.deleteFile(fileDetail.id)
                selectedFileForDetailId = null
            },
            onTagClick = { tag ->
                searchQuery = tag
                val key = tag.lowercase().trim()
                tagSearchFrequency[key] = (tagSearchFrequency[key] ?: 0) + 1
                selectedFileForDetailId = null
            },
            onCategoryClick = { category ->
                selectedCategoryFilter = category
                selectedFileForDetailId = null
            },
            relevanceScore = calculateRelevance(fileDetail, searchQuery, tagSearchFrequency),
            onSaveTagsClick = { fileId, newTags ->
                viewModel.updateFileTags(fileId, newTags)
            }
        )
    }

    // Immersive Fullscreen Lightbox / Media Viewer Component
    if (fullScreenViewerFile != null) {
        val fileViewer = fullScreenViewerFile!!
        val isOrganizing by viewModel.isOrganizing.collectAsStateWithLifecycle()
        FullScreenMediaViewer(
            file = fileViewer,
            sortedFiles = sortedFiles,
            onDismissRequest = { fullScreenViewerFileId = null },
            onFileSelected = { fullScreenViewerFileId = it },
            onOrganizeClick = { viewModel.organizeSingleFile(fileViewer) },
            onDeleteClick = {
                viewModel.deleteFile(fileViewer.id)
                fullScreenViewerFileId = null
            },
            onTagClick = { tag ->
                searchQuery = tag
                val key = tag.lowercase().trim()
                tagSearchFrequency[key] = (tagSearchFrequency[key] ?: 0) + 1
            },
            onCategoryClick = { category ->
                selectedCategoryFilter = category
            },
            isOrganizing = isOrganizing
        )
    }

    // Add Simulated File Dialog
    if (showAddDialog) {
        var addName by remember { mutableStateOf("project_chart_analysis.png") }
        var addType by remember { mutableStateOf("IMAGE") }
        var addApp by remember { mutableStateOf("WhatsApp") }
        var addSizeMb by remember { mutableStateOf("3.5") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Simulate Inbound App Media", color = Color.White) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = addName,
                        onValueChange = { addName = it },
                        label = { Text("Filename + Extension") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Type selectors
                    Text("Media Type Identifier", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("IMAGE", "VIDEO", "AUDIO", "DOCUMENT").forEach { type ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (addType == type) MaterialTheme.colorScheme.primary else Color.White.copy(
                                            alpha = 0.08f
                                        )
                                    )
                                    .clickable { addType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Source app selector
                    Text("Inbound App Context Source", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("WhatsApp", "Telegram", "Screenshots", "Camera", "Downloads", "Discord", "Slack").forEach { app ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (addApp == app) MaterialTheme.colorScheme.secondary else Color.White.copy(
                                            alpha = 0.08f
                                        )
                                    )
                                    .clickable { addApp = app }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = app,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = addSizeMb,
                        onValueChange = { addSizeMb = it },
                        label = { Text("Sizing (MB)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sizeVal = addSizeMb.toDoubleOrNull() ?: 1.0
                        viewModel.addSimulatedFile(addName, addType, addApp, sizeVal)
                        showAddDialog = false
                    },
                    modifier = Modifier.testTag("confirm_add_button")
                ) {
                    Text("Add Registry")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF141F39),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showBulkTagsDialog) {
        AlertDialog(
            onDismissRequest = { showBulkTagsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Bulk Add Custom Tags", color = Color.White)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Adding custom tags to ${selectedFileIds.size} selected file(s). Enter multiple tags separated by commas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    OutlinedTextField(
                        value = bulkTagsInput,
                        onValueChange = { bulkTagsInput = it },
                        placeholder = { Text("e.g. invoice, project-alpha, q2-report") },
                        label = { Text("Custom Tags") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bulk_tags_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (bulkTagsInput.isNotBlank()) {
                            viewModel.addTagsToMultipleFiles(selectedFileIds.toList(), bulkTagsInput)
                            selectedFileIds.clear()
                            isMultiSelectMode = false
                        }
                        showBulkTagsDialog = false
                    },
                    enabled = bulkTagsInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("confirm_bulk_tags_button")
                ) {
                    Text("Apply Tags")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBulkTagsDialog = false },
                    modifier = Modifier.testTag("cancel_bulk_tags_button")
                ) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF141F39),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showBulkMoveDialog) {
        AlertDialog(
            onDismissRequest = { showBulkMoveDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Move to Custom Category", color = Color.White)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Move ${selectedFileIds.size} selected file(s) to a custom category name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    OutlinedTextField(
                        value = bulkCategoryInput,
                        onValueChange = { bulkCategoryInput = it },
                        placeholder = { Text("e.g. Legal Documents, Vacation 2026") },
                        label = { Text("Category Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bulk_category_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanedCategory = bulkCategoryInput.trim()
                        if (cleanedCategory.isNotEmpty()) {
                            viewModel.moveMultipleFilesToCategory(selectedFileIds.toList(), cleanedCategory)
                            selectedFileIds.clear()
                            isMultiSelectMode = false
                        }
                        showBulkMoveDialog = false
                    },
                    enabled = bulkCategoryInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("confirm_bulk_move_button")
                ) {
                    Text("Move Files")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBulkMoveDialog = false },
                    modifier = Modifier.testTag("cancel_bulk_move_button")
                ) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF141F39),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showDatePickerDialog) {
        CustomDatePickerDialog(
            initialStartMillis = startDateMills,
            initialEndMillis = endDateMills,
            onDismissRequest = { showDatePickerDialog = false },
            onDateRangeSelected = { start, end ->
                startDateMills = start
                endDateMills = end
                if (start == null && end == null) {
                    selectedDatePresetText = "All Time"
                } else {
                    selectedDatePresetText = "Custom Range"
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaFileRow(
    file: MediaFile,
    onOrganizeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCardClick: () -> Unit,
    onTagClick: ((String) -> Unit)? = null,
    relevanceScore: Int = 0,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onCardClick() },
                onLongClick = { onLongClick?.invoke() }
            )
            .testTag("item_media_${file.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isMultiSelectMode && isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                Color.White.copy(alpha = 0.05f)
            }
        ),
        shape = RoundedCornerShape(14.dp),
        border = if (isMultiSelectMode && isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f))
                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f), CircleShape)
                        .clickable { onCardClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            // Context icon indicator
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (file.fileType) {
                        "IMAGE" -> Icons.Default.Share
                        "VIDEO" -> Icons.Default.PlayArrow
                        "AUDIO" -> Icons.Default.Share
                        else -> Icons.Default.Info
                    },
                    contentDescription = file.fileType,
                    tint = when (file.category) {
                        "Finance" -> Color(0xFF4CAF50)
                        "Memes" -> Color(0xFFFF9800)
                        "Work" -> Color(0xFF2196F3)
                        "Recordings" -> Color(0xFFE91E63)
                        "Screenshots" -> Color(0xFF9C27B0)
                        else -> Color.White
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = file.sourceApp,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                    Text(
                        text = "%.1f MB".format(file.fileSize / (1048576f)),
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when (file.syncStatus) {
                                        "SYNCED" -> Color(0xFF10B981)
                                        "FAILED" -> Color(0xFFEF4444)
                                        else -> Color(0xFFF59E0B)
                                    }
                                )
                        )
                        Text(
                            text = when (file.syncStatus) {
                                "SYNCED" -> "Synced"
                                "FAILED" -> "Failed"
                                else -> "Unsynced"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = when (file.syncStatus) {
                                    "SYNCED" -> Color(0xFF10B981)
                                    "FAILED" -> Color(0xFFEF4444)
                                    else -> Color.Gray
                                },
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                if (file.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        file.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { onTagClick?.invoke(tag) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (file.category != "Uncategorized") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = file.category,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    if (relevanceScore > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Score: $relevanceScore",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFFFEB3B),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }
            }

            // End actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOrganizeClick,
                    modifier = Modifier.testTag("ai_organize_item_${file.id}")
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Auto organize file metadata with AI",
                        tint = Color.LightGray
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("delete_item_${file.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove file record",
                        tint = Color(0xFFFF5252)
                    )
                }
            }
        }
    }
}

fun getMediaAestheticUrl(file: MediaFile): String {
    if (!file.localUri.isNullOrEmpty()) {
        if (file.localUri.startsWith("http") || file.localUri.startsWith("content")) {
            return file.localUri
        }
    }
    val name = file.fileName.lowercase()
    val category = file.category.lowercase()
    return when {
        category == "finance" || name.contains("invoice") || name.contains("tax") || name.contains("payment") -> {
            "https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?auto=format&fit=crop&w=400&q=80"
        }
        category == "memes" || name.contains("cat") || name.contains("laugh") || name.contains("funny") || name.contains("meme") -> {
            "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?auto=format&fit=crop&w=400&q=80"
        }
        category == "work" || name.contains("diagram") || name.contains("statement") || name.contains("report") -> {
            "https://images.unsplash.com/photo-1531403009284-440f080d1e12?auto=format&fit=crop&w=400&q=80"
        }
        category == "recordings" || file.fileType == "AUDIO" || name.contains("audio") || name.contains("music") || name.contains("memo") -> {
            "https://images.unsplash.com/photo-1484755560693-a4074577af3a?auto=format&fit=crop&w=400&q=80"
        }
        category == "screenshots" || name.contains("screenshot") -> {
            "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=400&q=80"
        }
        category == "personal" || name.contains("holiday") || name.contains("trip") || name.contains("sunset") || name.contains("vacation") -> {
            "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=400&q=80"
        }
        else -> {
            when (file.fileType) {
                "IMAGE" -> "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=400&q=80"
                "VIDEO" -> "https://images.unsplash.com/photo-1536440136628-849c177e76a1?auto=format&fit=crop&w=400&q=80"
                "AUDIO" -> "https://images.unsplash.com/photo-1487180142328-054b783fc471?auto=format&fit=crop&w=400&q=80"
                else -> "https://images.unsplash.com/photo-1457369804613-52c61a468e7d?auto=format&fit=crop&w=400&q=80"
            }
        }
    }
}

@Composable
fun AnimatedEntranceContainer(
    index: Int = 0,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 35L) // Staggered delays for sequential appearance
        isVisible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "entranceAlpha"
    )

    val slideUp by animateFloatAsState(
        targetValue = if (isVisible) 0f else 60f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "entranceSlideUp"
    )

    Box(
        modifier = Modifier
            .alpha(alpha)
            .graphicsLayer {
                translationY = slideUp
            }
    ) {
        content()
    }
}

@Composable
fun shimmerBrush(
    showShimmer: Boolean = true,
    targetValue: Float = 1000f
): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.05f)
        )

        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation = transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerTranslation"
        )

        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnimation.value, y = translateAnimation.value)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }
}

@Composable
fun MediaFileGridCardSkeleton() {
    val brush = shimmerBrush()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("media_grid_item_skeleton"),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.03f)
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thumbnail Image Box skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(brush)
            )

            // Content details Column skeleton
            Column(modifier = Modifier.padding(10.dp)) {
                // Title line skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tag lines skeleton
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(brush)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Footer row skeleton
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(55.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(brush)
                    )

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(brush)
                    )
                }
            }
        }
    }
}

@Composable
fun MediaFileRowSkeleton() {
    val brush = shimmerBrush()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("media_row_item_skeleton"),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.03f)
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle symbol placeholder skeleton
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(brush)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title line skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(brush)
                    )

                    Box(
                        modifier = Modifier
                            .width(35.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(brush)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Action placeholder skeleton
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaFileGridCard(
    file: MediaFile,
    onOrganizeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCardClick: () -> Unit,
    onTagClick: ((String) -> Unit)? = null,
    relevanceScore: Int = 0,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onCardClick() },
                onLongClick = { onLongClick?.invoke() }
            )
            .testTag("item_grid_media_${file.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isMultiSelectMode && isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                Color.White.copy(alpha = 0.05f)
            }
        ),
        shape = RoundedCornerShape(14.dp),
        border = if (isMultiSelectMode && isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = getMediaAestheticUrl(file),
                    contentDescription = "Thumbnail for ${file.fileName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top shadow gradient/overlay to ensure high text contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.5f)
                                )
                            )
                        )
                )

                if (file.fileType == "VIDEO") {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play preview",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (file.fileType == "AUDIO") {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share, // Audio music note/share symbol
                            contentDescription = "Audio track file",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (isMultiSelectMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = file.sourceApp,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Small Sync Status Indicator Icon
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (file.syncStatus) {
                                "SYNCED" -> Color(0xFF10B981).copy(alpha = 0.9f) // Vivid Emerald Green
                                "FAILED" -> Color(0xFFEF4444).copy(alpha = 0.9f) // Crimson Red
                                else -> Color(0xFFF59E0B).copy(alpha = 0.9f) // Amber Orange
                            }
                        )
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = when (file.syncStatus) {
                            "SYNCED" -> Icons.Default.Check
                            "FAILED" -> Icons.Default.Warning
                            else -> Icons.Default.Refresh
                        },
                        contentDescription = "Sync Status: ${file.syncStatus}",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "%.1fM".format(file.fileSize / (1048576f)),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (file.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        file.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { onTagClick?.invoke(tag) }
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }

                if (relevanceScore > 0) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Score: $relevanceScore",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFFFEB3B),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (file.category == "Uncategorized") Color.White.copy(alpha = 0.1f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = file.category,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (file.category == "Uncategorized") Color.LightGray else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onOrganizeClick,
                            modifier = Modifier.size(24.dp).testTag("ai_organize_grid_${file.id}")
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "AI Organize",
                                tint = Color.LightGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(24.dp).testTag("delete_item_grid_${file.id}")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyncView(
    viewModel: MediaViewModel,
    onMenuClick: () -> Unit
) {
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val mediaFiles by viewModel.mediaFiles.collectAsStateWithLifecycle()
    val isBackgroundSyncScheduled by viewModel.isBackgroundSyncScheduled.collectAsStateWithLifecycle()
    val syncIntervalMinutes by viewModel.syncIntervalMinutes.collectAsStateWithLifecycle()
    val isScanningDuplicates by viewModel.isScanningDuplicates.collectAsStateWithLifecycle()

    val googleDriveEmail by viewModel.googleDriveEmail.collectAsStateWithLifecycle()
    val googleDriveFolder by viewModel.googleDriveFolder.collectAsStateWithLifecycle()
    val dropboxEmail by viewModel.dropboxEmail.collectAsStateWithLifecycle()
    val dropboxFolder by viewModel.dropboxFolder.collectAsStateWithLifecycle()
    val preferredCloudRepository by viewModel.preferredCloudRepository.collectAsStateWithLifecycle()

    var showGoogleLinkDialog by remember { mutableStateOf(false) }
    var showDropboxLinkDialog by remember { mutableStateOf(false) }

    val pendingCount = mediaFiles.count { it.syncStatus == "PENDING" }
    val syncedCount = mediaFiles.count { it.syncStatus == "SYNCED" }
    val totalSize = mediaFiles.sumOf { it.fileSize }
    val syncedSize = mediaFiles.filter { it.syncStatus == "SYNCED" }.sumOf { it.fileSize }

    if (showGoogleLinkDialog) {
        CloudLinkDialog(
            providerName = "Google Drive",
            accentColor = Color(0xFF34A853),
            onDismiss = { showGoogleLinkDialog = false },
            onLinkSuccess = { email, folder ->
                viewModel.linkGoogleDrive(email, folder)
                showGoogleLinkDialog = false
            }
        )
    }

    if (showDropboxLinkDialog) {
        CloudLinkDialog(
            providerName = "Dropbox",
            accentColor = Color(0xFF0061FE),
            onDismiss = { showDropboxLinkDialog = false },
            onLinkSuccess = { email, folder ->
                viewModel.linkDropbox(email, folder)
                showDropboxLinkDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("sync_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Sidebar Navigation Menu",
                        tint = Color.White
                    )
                }
                Column {
                    Text(
                        text = "Cloud Sync Hub",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Master Repository Synchronization Node",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Futuristic Cloud Illustrator Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, Brush.horizontalGradient(listOf(Color(0xFF2196F3), Color(0xFFE91E63))), RoundedCornerShape(16.dp))
        ) {
            // Load custom generated image from drawable resources
            Image(
                painter = painterResource(id = R.drawable.media_vault_banner_1782142258765),
                contentDescription = "Cloud repository vector artwork decor",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Text overlays
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .padding(16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        "Secure Storage Matrix",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        "256-bit AES client-to-cloud security enabled.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sync Statistics Matrix Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SyncMetricCard(
                modifier = Modifier.weight(1f),
                title = "PENDING CLOUD",
                count = "$pendingCount Files",
                description = "%.1f MB payload".format(mediaFiles.filter { it.syncStatus == "PENDING" }.sumOf { it.fileSize } / 1048576f),
                tint = Color(0xFFFF9800)
            )
            SyncMetricCard(
                modifier = Modifier.weight(1f),
                title = "REMOTE STORAGE",
                count = "$syncedCount Files",
                description = "%.1f MB storage".format(syncedSize / 1048576f),
                tint = Color(0xFF4CAF50)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress bar container
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ACTIVE DEPLOYMENT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { syncProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSyncing) "Uploading secure payload chunks... (${(syncProgress * 100).toInt()}%)" else "Sync Consolidated",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                    )

                    Button(
                        onClick = { viewModel.syncNow() },
                        enabled = !isSyncing && pendingCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("sync_now_button")
                    ) {
                        if (isSyncing) {
                            Text("Syncing...")
                        } else {
                            Text("Sync Repository")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Background Sync Scheduler Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AUTOMATED BACKGROUND SYNC",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isBackgroundSyncScheduled) Color(0xFF4CAF50) else Color(0xFFFF9800))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBackgroundSyncScheduled) "Active (Every $syncIntervalMinutes mins)" else "Deactivated",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isBackgroundSyncScheduled) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                )
                            )
                        }
                    }

                    // Toggle Switch
                    Switch(
                        checked = isBackgroundSyncScheduled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                viewModel.scheduleBackgroundSync(syncIntervalMinutes)
                            } else {
                                viewModel.cancelBackgroundSync()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.testTag("background_sync_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Sync Frequency Interval",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                )
                Spacer(modifier = Modifier.height(8.dp))

                val intervals = listOf(15, 60, 360, 1440)
                val labels = listOf("15 mins", "1 hour", "6 hours", "Daily")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    intervals.forEachIndexed { idx, min ->
                        val label = labels[idx]
                        val isSelected = syncIntervalMinutes == min
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
                                .border(
                                    BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    viewModel.scheduleBackgroundSync(min)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Standard helper to force execution instantly
                OutlinedButton(
                    onClick = { viewModel.testBackgroundSyncImmediately() },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trigger_background_sync_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Trigger background sync worker",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Trigger Worker Demonstration",
                        style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Secure Cloud Storage Repository Section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cloud_repositories_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SECURE CLOUD STORAGE PROVIDERS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        letterSpacing = 1.sp
                    )
                )
                Text(
                    text = "Configure and link your external staging databases / folders",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Google Drive Provider
                ProviderRow(
                    name = "Google Drive",
                    isLinked = googleDriveEmail != null,
                    email = googleDriveEmail,
                    folderName = googleDriveFolder,
                    isPreferred = preferredCloudRepository == "Google Drive",
                    onLinkClick = { showGoogleLinkDialog = true },
                    onDisconnectClick = { viewModel.disconnectGoogleDrive() },
                    onSetPreferredClick = { viewModel.setPreferredCloudRepository("Google Drive") },
                    accentColor = Color(0xFF34A853),
                    testTagPrefix = "google_drive"
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                Spacer(modifier = Modifier.height(12.dp))

                // Dropbox Provider
                ProviderRow(
                    name = "Dropbox Link Node",
                    isLinked = dropboxEmail != null,
                    email = dropboxEmail,
                    folderName = dropboxFolder,
                    isPreferred = preferredCloudRepository == "Dropbox",
                    onLinkClick = { showDropboxLinkDialog = true },
                    onDisconnectClick = { viewModel.disconnectDropbox() },
                    onSetPreferredClick = { viewModel.setPreferredCloudRepository("Dropbox") },
                    accentColor = Color(0xFF0061FE),
                    testTagPrefix = "dropbox"
                )
                
                if (googleDriveEmail == null && dropboxEmail == null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEF08A).copy(alpha = 0.08f))
                            .border(1.dp, Color(0xFFFEF08A).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Security Alert",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Using isolated offline local storage. Link a cloud provider to unlock background master backups.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFFEF08A),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Secure Vault Duplicate Scanner Section
        val duplicateGroups = mediaFiles
            .filter { it.isDuplicate && !it.md5Hash.isNullOrBlank() }
            .groupBy { it.md5Hash!! }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("duplicate_detection_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SECURE VAULT DUPLICATE SCANNER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray,
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = "Analyze check-sum signatures to clean up redundant copies",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }
                    
                    if (isScanningDuplicates) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Button(
                            onClick = { viewModel.scanForDuplicates() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("scan_duplicates_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Scan",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (duplicateGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.08f))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Vault Safe",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Your encrypted vault is clean. No duplicated uploads detected.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF10B981))
                            )
                        }
                    }
                } else {
                    Text(
                        text = "${duplicateGroups.size} Redundant groups flagged for review:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.LightGray.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        duplicateGroups.forEach { (md5, files) ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "MD5: ${md5.substring(0, kotlin.math.min(md5.length, 12))}...",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${files.size} DUPES",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color(0xFFF87171),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 8.sp
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        files.forEach { file ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.White.copy(alpha = 0.02f))
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = file.fileName,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "%.1f MB".format(file.fileSize / 1048576f),
                                                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 10.sp)
                                                        )
                                                        Text(
                                                            text = file.sourceApp,
                                                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 10.sp)
                                                        )
                                                    }
                                                }

                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // "Keep" button
                                                    TextButton(
                                                        onClick = { viewModel.keepDuplicateFile(file) },
                                                        colors = ButtonDefaults.textButtonColors(
                                                            contentColor = Color.LightGray.copy(alpha = 0.8f)
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier
                                                            .height(28.dp)
                                                            .testTag("keep_duplicate_${file.id}")
                                                    ) {
                                                        Text(
                                                            text = "Keep Both",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                                        )
                                                    }

                                                    // Trash/Delete Button
                                                    IconButton(
                                                        onClick = { viewModel.deleteFile(file.id) },
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .testTag("delete_duplicate_${file.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete Duplicate",
                                                            tint = Color(0xFFEF4444),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Terminal Protocol Logs Console
        Text(
            text = "PROTOCOLS VERIFICATION ENGINE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(syncLogs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                log.contains("[System]") -> Color(0xFF03A9F4)
                                log.contains("[Sync Engine]") -> Color(0xFFFFEB3B)
                                log.contains("Error") -> Color(0xFFFF5252)
                                else -> Color(0xFF4CAF50)
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SyncMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    count: String,
    description: String,
    tint: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
            )
        }
    }
}

@Composable
fun ProviderRow(
    name: String,
    isLinked: Boolean,
    email: String?,
    folderName: String,
    isPreferred: Boolean,
    onLinkClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onSetPreferredClick: () -> Unit,
    accentColor: Color,
    testTagPrefix: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Icon Container
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isLinked) accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLinked) Icons.Default.CheckCircle else Icons.Default.Share,
                    contentDescription = "$name Icon",
                    tint = if (isLinked) accentColor else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    if (isPreferred) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PREFERRED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = accentColor,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                }
                if (isLinked && email != null) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.LightGray.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("${testTagPrefix}_email_text")
                    )
                    Text(
                        text = "Remote Sync folder: /$folderName",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "Authentication required",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Interaction Buttons Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLinked) {
                if (!isPreferred) {
                    IconButton(
                        onClick = onSetPreferredClick,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("${testTagPrefix}_preferred_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Set $name Preferred",
                            tint = Color.LightGray.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDisconnectClick,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("${testTagPrefix}_disconnect_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Disconnect $name",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Button(
                    onClick = onLinkClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor.copy(alpha = 0.2f),
                        contentColor = accentColor
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("${testTagPrefix}_link_button")
                ) {
                    Text(
                        text = "Link",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun CloudLinkDialog(
    providerName: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onLinkSuccess: (email: String, folder: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var folderName by remember { mutableStateOf("") }
    var isSimulatingOAuth by remember { mutableStateOf(false) }
    var simulationStep by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { if (!isSimulatingOAuth) onDismiss() }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A35)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Link $providerName",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                if (!isSimulatingOAuth) {
                    Text(
                        text = "Please authenticate and configure your secure cloud directory to initiate automated zero-knowledge synchronizations.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Email field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Cloud Account Email",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontWeight = FontWeight.Bold)
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                errorMsg = ""
                            },
                            placeholder = { Text("e.g. user@gmail.com", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cloud_email_input")
                        )
                    }

                    // Directory subfolder field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Backup Directory Subfolder",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontWeight = FontWeight.Bold)
                        )
                        OutlinedTextField(
                            value = folderName,
                            onValueChange = {
                                folderName = it
                                errorMsg = ""
                            },
                            placeholder = { Text("e.g. CipherVault_Sync", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cloud_folder_input")
                        )
                    }

                    if (errorMsg.isNotEmpty()) {
                        Text(
                            text = errorMsg,
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                    errorMsg = "Please supply a valid email address."
                                    return@Button
                                }
                                val folder = folderName.ifBlank { "CipherVault_Backup" }
                                isSimulatingOAuth = true
                                scope.launch {
                                    simulationStep = "Contacting OAuth2 Provider Auth Handlers..."
                                    delay(1000)
                                    simulationStep = "Acquiring access_token callback payloads..."
                                    delay(1200)
                                    simulationStep = "Provisioning isolated backup subfolder: /$folder..."
                                    delay(1000)
                                    simulationStep = "Zero-Knowledge Sandbox handshake complete."
                                    delay(800)
                                    onLinkSuccess(email, folder)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("authorize_cloud_button")
                        ) {
                            Text("Authorize & Link", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Simulation progress indicators
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = accentColor,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "SECURE OAUTH SIMULATION FLOW",
                            style = MaterialTheme.typography.labelSmall.copy(color = accentColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = simulationStep,
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray),
                            modifier = Modifier.testTag("simulation_step_text")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AIOrganizerView(
    mediaFiles: List<MediaFile>,
    viewModel: MediaViewModel,
    onMenuClick: () -> Unit
) {
    val isOrganizing by viewModel.isOrganizing.collectAsStateWithLifecycle()
    val aiLogs by viewModel.aiLogs.collectAsStateWithLifecycle()
    val customRule by viewModel.customRule.collectAsStateWithLifecycle()
    val isAutoTaggingEnabled by viewModel.isAutoTaggingEnabled.collectAsStateWithLifecycle()
    val taggingGranularity by viewModel.taggingGranularity.collectAsStateWithLifecycle()
    val recentTaggingEvents by viewModel.recentTaggingEvents.collectAsStateWithLifecycle()

    val uncategorizedCount = mediaFiles.count { it.category == "Uncategorized" }
    
    // Dynamic Stats categorization counts for bar chart visualisation
    val allCategories = listOf("Memes", "Work", "Personal", "Finance", "Recordings", "Screenshots", "Documents")
    val distribution = remember(mediaFiles) {
        allCategories.associateWith { cat -> mediaFiles.count { it.category == cat } }
    }
    val maxCountInAnyCat = remember(distribution) {
        distribution.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("ai_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Sidebar Navigation Menu",
                        tint = Color.White
                    )
                }
                Column {
                    Text(
                        text = "AI Organizer",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Dynamic content cataloging matching user intentions",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI Agent custom instruction box
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🧠 CUSTOM CLASSIFICATION RULES For Gemini",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customRule,
                    onValueChange = { viewModel.updateCustomRule(it) },
                    placeholder = {
                        Text(
                            "e.g. Put costco in Work, tag screenshots as #captures and move WAV/MP3 recordings to 'Audios'...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedPlaceholderColor = Color.Gray,
                        unfocusedPlaceholderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$uncategorizedCount Unorganized files found.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                    )

                    Button(
                        onClick = { viewModel.autoOrganizeAll() },
                        enabled = !isOrganizing && uncategorizedCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            disabledContainerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("auto_organize_all_button")
                    ) {
                        if (isOrganizing) {
                            Text("Agent cataloging...")
                        } else {
                            Text("AI Auto-Organize")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI-Driven Auto-Tagging Service Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "⚡ AUTOMATED COGNITIVE TAGGING ENGINE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isAutoTaggingEnabled) Color(0xFF00E676) else Color(0xFFFF9100))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAutoTaggingEnabled) "Live Service (Active)" else "Bypassed (Standby)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAutoTaggingEnabled) Color(0xFF00E676) else Color(0xFFFF9100)
                                )
                            )
                        }
                    }

                    // Tagger toggle Switch
                    Switch(
                        checked = isAutoTaggingEnabled,
                        onCheckedChange = { viewModel.setAutoTaggingEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.testTag("auto_tagging_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Granularity Depth Target Mode",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                )
                Spacer(modifier = Modifier.height(8.dp))

                val granularities = listOf("STANDARD", "NARRATIVE", "TECHNICAL")
                val granLabels = listOf("Standard labels", "Vibe/Context", "Cam Specs/Colors")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    granularities.forEachIndexed { index, mode ->
                        val label = granLabels[index]
                        val isSelected = taggingGranularity == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
                                .border(
                                    BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = isAutoTaggingEnabled) {
                                    viewModel.setTaggingGranularity(mode)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = mode,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else if (isAutoTaggingEnabled) Color.White else Color.Gray
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.Gray
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "📁 AUTO-TAGGING TRANSACTIONS HISTORY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (recentTaggingEvents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tagging transactions recorded yet.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            )
                        }
                    } else {
                        recentTaggingEvents.forEach { event ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.02f))
                                    .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = event.fileName,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )

                                    val statusColor = when (event.status) {
                                        "SUCCESS" -> Color(0xFF00E676)
                                        "OFFLINE_FALLBACK" -> Color(0xFFFFD600)
                                        "BYPASSED" -> Color(0xFF90A4AE)
                                        else -> Color(0xFFFF1744)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(statusColor.copy(alpha = 0.15f))
                                            .border(BorderStroke(0.5.dp, statusColor.copy(alpha = 0.4f)), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = event.status,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = statusColor
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = event.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                )

                                if (event.tags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        event.tags.forEach { t ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "#$t",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Visual Custom Native Bar Chart in Compose
        Text(
            text = "MEDIA VAULT DISTRIBUTION MAP",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    distribution.forEach { (catName, count) ->
                        val barHeightFactor = count.toFloat() / maxCountInAnyCat
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // Bar visual graphics
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(0.8f * barHeightFactor)
                                    .width(18.dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = catName.take(3).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.LightGray,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI Agent Decisive logs terminal Console
        Text(
            text = "AI DECISION PROTOCOLS TERMINAL",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(aiLogs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                log.contains("[AI]") -> Color(0xFFE91E63)
                                log.contains("Successfully") || log.contains("Complete") -> Color(0xFF4CAF50)
                                log.contains("Error") -> Color(0xFFFF5252)
                                else -> Color(0xFF03A9F4)
                            }
                        )
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Reset Database helper button to satisfy ease-of-use requirements
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { viewModel.clearAll() },
                colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray),
                modifier = Modifier.testTag("reset_vault_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset database info")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Restore Vault Presets")
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = Color.White
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun calculateRelevance(file: MediaFile, query: String, tagFreq: Map<String, Int>): Int {
    var score = 0
    
    // 1. Tag search frequency weight (always bubbles up frequently queried subjects)
    val fileTags = file.tags.split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        
    fileTags.forEach { t ->
        score += (tagFreq[t] ?: 0) * 8
    }
    
    // 2. Active Query Matching Contribution
    if (query.isNotEmpty()) {
        val q = query.trim().lowercase()
        
        // Exact tag match adds huge relevance
        fileTags.forEach { t ->
            if (t == q) {
                score += 50
            } else if (t.contains(q)) {
                score += 24
            }
        }
        
        // Match with category names
        val cat = file.category.trim().lowercase()
        if (cat == q) {
            score += 35
        } else if (cat.contains(q)) {
            score += 15
        }
        
        // Match with filename
        val name = file.fileName.trim().lowercase()
        if (name == q) {
            score += 45
        } else if (name.contains(q)) {
            score += 20
        }
        
        // Match with source connected app
        val app = file.sourceApp.trim().lowercase()
        if (app == q) {
            score += 30
        } else if (app.contains(q)) {
            score += 10
        }
        
        // Match with AI Summary/explanation
        val summary = (file.aiSummary ?: "").trim().lowercase()
        if (summary.contains(q)) {
            score += 12
        }
    }
    
    return score
}

@Composable
fun MediaDetailOverlayModal(
    file: MediaFile,
    isOrganizing: Boolean,
    onDismissRequest: () -> Unit,
    onOrganizeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    relevanceScore: Int,
    onSaveTagsClick: (Int, String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    var isEditingTags by remember { mutableStateOf(false) }
    var editedTagsText by remember(file.tags) { mutableStateOf(file.tags) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .testTag("media_detail_overlay_modal"),
            color = Color(0xFF0F172A),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. High Resolution Preview Area (Hero Card)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1E293B),
                                    Color(0xFF0F172A)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (file.fileType) {
                        "IMAGE" -> {
                            if (!file.localUri.isNullOrEmpty()) {
                                AsyncImage(
                                    model = file.localUri,
                                    contentDescription = "Image preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(8.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                        .background(Color(0xFF1E1E2F), RoundedCornerShape(16.dp))
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val steps = 8
                                        for (i in 1 until steps) {
                                            val x = size.width * (i.toFloat() / steps)
                                            val y = size.height * (i.toFloat() / steps)
                                            drawLine(
                                                color = Color.White.copy(alpha = 0.03f),
                                                start = androidx.compose.ui.geometry.Offset(x, 0f),
                                                end = androidx.compose.ui.geometry.Offset(x, size.height),
                                                strokeWidth = 1f
                                            )
                                            drawLine(
                                                color = Color.White.copy(alpha = 0.03f),
                                                start = androidx.compose.ui.geometry.Offset(0f, y),
                                                end = androidx.compose.ui.geometry.Offset(size.width, y),
                                                strokeWidth = 1f
                                            )
                                        }
                                        drawCircle(
                                            color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                                            radius = size.minDimension / 4,
                                            center = center
                                        )
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Image preview",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "HIGH-RES COMPOSITING PREVIEW",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.Gray,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                        )
                                        Text(
                                            text = "1920×1080 • PNG • sRGB Grid",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color.DarkGray,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        "VIDEO" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .background(Color.Black, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Mock Video Player",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(
                                                brush = Brush.radialGradient(listOf(Color(0xFF3B82F6), Color.Transparent)),
                                                shape = CircleShape
                                            )
                                            .padding(12.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "STREAM READY • 60 FPS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF60A5FA),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp
                                        )
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "00:43",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.LightGray,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.35f)
                                                .height(4.dp)
                                                .background(Color(0xFF3B82F6), RoundedCornerShape(2.dp))
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "02:18",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.LightGray,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                }
                            }
                        }
                        "AUDIO" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .background(Color(0xFF0B132B), RoundedCornerShape(16.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        val heights = listOf(14, 28, 40, 20, 32, 24, 12, 36, 44, 30, 20, 38, 24, 16, 28, 42, 18, 10, 25, 38, 15)
                                        heights.forEach { h ->
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(h.dp)
                                                    .clip(RoundedCornerShape(1.5.dp))
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color(0xFF10B981),
                                                                Color(0xFF0284C7)
                                                            )
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "WAVEFORM REAL-TIME SYNTHESIS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 8.sp,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    Text(
                                        text = "320 KBPS • STEREO • 44.1 KHZ",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.Gray,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .background(Color(0xFF1E293B), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Document Scan System",
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "SANDBOX COMPONENT INDEX",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.LightGray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(modifier = Modifier.width(130.dp).height(4.dp).background(Color.White.copy(alpha = 0.15f)))
                                        Box(modifier = Modifier.width(100.dp).height(4.dp).background(Color.White.copy(alpha = 0.1f)))
                                        Box(modifier = Modifier.width(120.dp).height(4.dp).background(Color.White.copy(alpha = 0.08f)))
                                    }
                                }
                            }
                        }
                    }

                    // Floating close button
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp)
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("overlay_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss overlay",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Connected app floating badge on preview
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                color = when (file.sourceApp.lowercase()) {
                                    "whatsapp" -> Color(0xFF25D366)
                                    "telegram" -> Color(0xFF0088CC)
                                    "signal" -> Color(0xFF3A5BB4)
                                    "slack" -> Color(0xFF4A154B)
                                    "discord" -> Color(0xFF5865F2)
                                    else -> Color(0xFF475569)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = file.sourceApp.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 8.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }

                // 2. Metadata details content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Category Bubble
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { onCategoryClick(file.category) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "✦ ${file.category}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        // File type Bubble
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = file.fileType,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.LightGray,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        // Relevance Score (if any)
                        if (relevanceScore > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEAB308).copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Relevance Index: $relevanceScore",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFFACC15),
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // AI Synopsis Segment
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("ai_synopsis_card"),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🧠 COGNITIVE SUMMARY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFA5B4FC),
                                        letterSpacing = 1.sp
                                    )
                                )
                                if (!file.aiSummary.isNullOrEmpty()) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(file.aiSummary))
                                            Toast.makeText(context, "AI Summary copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp).testTag("copy_ai_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Copy Summary",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = file.aiSummary ?: "Awaiting Cognitive analysis. Tap \"AI Analyze\" below to trigger Gemini model cataloging.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.LightGray,
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // AI Tags block
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI CLASSIFIED TOPICS / ONTOLOGIES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                letterSpacing = 0.5.sp
                            )
                        )
                        
                        Text(
                            text = if (isEditingTags) "DONE" else "EDIT TAGS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            ),
                            modifier = Modifier
                                .clickable {
                                    if (isEditingTags) {
                                        onSaveTagsClick(file.id, editedTagsText)
                                    }
                                    isEditingTags = !isEditingTags
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    if (isEditingTags) {
                        OutlinedTextField(
                            value = editedTagsText,
                            onValueChange = { editedTagsText = it },
                            placeholder = { Text("Enter tags separated by commas...", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("edit_tags_input_field"),
                            maxLines = 2,
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        if (file.tags.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                file.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .clickable { onTagClick(tag) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "No AI tags assigned yet",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 11.sp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Technical Specs segment
                    Text(
                        text = "TECHNICAL ATTRIBUTES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpecsDetailRow(label = "Sandbox Path", value = "/sandbox/vault/media/${file.fileName}")
                            SpecsDetailRow(label = "Internal ID", value = file.id.toString())
                            SpecsDetailRow(label = "Precision Size", value = "${file.fileSize} Bytes")
                            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.timestamp))
                            SpecsDetailRow(label = "Indexed Date", value = formattedDate)
                            SpecsDetailRow(label = "Registry Location", value = file.cloudUrl ?: "PENDING CLOUD SYNC")
                        }
                    }
                }

                // 3. Bottom controls
                Divider(color = Color.White.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .background(Color(0xFFEF4444).copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .size(44.dp)
                            .testTag("overlay_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete from store",
                            tint = Color(0xFFEF4444)
                        )
                    }

                    Button(
                        onClick = onOrganizeClick,
                        enabled = !isOrganizing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                            .height(44.dp)
                            .testTag("overlay_ai_button")
                    ) {
                        if (isOrganizing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Re-analyzing...")
                        } else {
                            Text("✦ Run AI Re-Analysis")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpecsDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            modifier = Modifier.widthIn(max = 200.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
fun FullScreenMediaViewer(
    file: MediaFile,
    sortedFiles: List<MediaFile>,
    onDismissRequest: () -> Unit,
    onFileSelected: (Int) -> Unit,
    onOrganizeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    isOrganizing: Boolean
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // Find current index to support prev/next navigation
    val currentIndex = remember(file.id, sortedFiles) {
        sortedFiles.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
    }

    // Slideshow control state
    var isSlideshowPlaying by remember { mutableStateOf(false) }
    
    // Toggle active details/AI summary pane
    var isInfoPanelVisible by remember { mutableStateOf(false) }

    // Multi-touch Zoom/Pan States
    var zoomScale by remember(file.id) { mutableStateOf(1f) }
    var panOffset by remember(file.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // Slideshow transition logic
    LaunchedEffect(isSlideshowPlaying, file.id) {
        if (isSlideshowPlaying && sortedFiles.isNotEmpty()) {
            delay(3500) // Transition slide every 3.5 seconds
            val nextIndex = (currentIndex + 1) % sortedFiles.size
            onFileSelected(sortedFiles[nextIndex].id)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("fullscreen_media_lightbox"),
            color = Color(0xFF030712), // Deep ultimate space black cinema background
            contentColor = Color.White
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Gesture Interactive Media Viewer Area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(file.id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                if (zoomScale > 1f) {
                                    panOffset += pan
                                } else {
                                    panOffset = androidx.compose.ui.geometry.Offset.Zero
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale,
                            translationX = panOffset.x,
                            translationY = panOffset.y
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (file.fileType) {
                        "IMAGE" -> {
                            AsyncImage(
                                model = getMediaAestheticUrl(file),
                                contentDescription = "Full-screen high quality view of ${file.fileName}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(12.dp)
                            )
                        }
                        "VIDEO" -> {
                            // Immersive high-fidelity visual player with dynamic video progress metrics
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.6f)
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = getMediaAestheticUrl(file),
                                    contentDescription = "Video backdrop",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().alpha(0.45f)
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Simulated Video playback",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        Color.Transparent
                                                    )
                                                ),
                                                shape = CircleShape
                                            )
                                            .padding(16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "STREAMING IN SECURE SANDBOX",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF60A5FA),
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 2.5.sp
                                        )
                                    )
                                    Text(
                                        text = "AES-256 local decrypt active",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 10.sp)
                                    )
                                }

                                // Interactive video player overlay slider bar
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                            )
                                        )
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "01:05",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.LightGray,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.45f)
                                                .height(4.dp)
                                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "02:24",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.LightGray,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                        "AUDIO" -> {
                            // Immersive waveform dynamic audio component
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.55f)
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share, // Audio wave / sound symbol
                                        contentDescription = "Disc player",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Bouncing Equalizer Wave Simulation
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.height(60.dp)
                                    ) {
                                        val heights = listOf(15, 30, 48, 24, 38, 55, 20, 44, 58, 35, 22, 45, 52, 28, 18, 38, 48, 25, 12, 32, 44, 18)
                                        heights.forEach { h ->
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .height(h.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color(0xFF10B981),
                                                                Color(0xFF0EA5E9)
                                                            )
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "AURAL DECRYPT DECK PLAYING",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.5.sp,
                                            fontSize = 9.sp
                                        )
                                    )
                                    Text(
                                        text = "WAV • 24-bit Stereo Master",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.Gray,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                        else -> {
                            // Document Scanner preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .fillMaxHeight(0.7f)
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(24.dp),
                                contentAlignment = Alignment.TopStart
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Document",
                                            tint = Color.DarkGray,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.LightGray)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "SECURE PDF",
                                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Black, fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = file.fileName,
                                        style = MaterialTheme.typography.titleMedium.copy(color = Color.Black, fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = Color.LightGray)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "CLASSIFIED REPORT METADATA SUMMARY",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontWeight = FontWeight.ExtraBold)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = file.aiSummary ?: "This document has not been processed. Trigger Gemini Analysis to reconstruct index ontologies.",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.DarkGray, lineHeight = 20.sp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Lightbox HUD Top Controls Bar
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .padding(top = 18.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .testTag("lightbox_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit cinema mode",
                                tint = Color.White
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(14.dp))
                        
                        Column {
                            Text(
                                text = "Cinema View",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                            if (sortedFiles.isNotEmpty()) {
                                Text(
                                    text = "Item ${currentIndex + 1} of ${sortedFiles.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray, fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play Slideshow Button
                        IconButton(
                            onClick = { isSlideshowPlaying = !isSlideshowPlaying },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isSlideshowPlaying) MaterialTheme.colorScheme.primary
                                    else Color.White.copy(alpha = 0.12f)
                                )
                                .testTag("lightbox_slideshow_button")
                        ) {
                            Icon(
                                imageVector = if (isSlideshowPlaying) Icons.Default.Settings /* pause mock */ else Icons.Default.PlayArrow,
                                contentDescription = if (isSlideshowPlaying) "Pause Slideshow" else "Play Slideshow",
                                tint = Color.White
                            )
                        }

                        // Info / AI drawer toggle button
                        IconButton(
                            onClick = { isInfoPanelVisible = !isInfoPanelVisible },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isInfoPanelVisible) MaterialTheme.colorScheme.secondary
                                    else Color.White.copy(alpha = 0.12f)
                                )
                                .testTag("lightbox_info_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Toggle cognitive telemetry",
                                tint = Color.White
                            )
                        }

                        // Share simulator
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(file.cloudUrl ?: file.fileName))
                                Toast.makeText(context, "Copied file reference to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share reference",
                                tint = Color.White
                            )
                        }
                    }
                }

                // 3. Side Swiping Helper Navigation Arrows
                if (sortedFiles.size > 1) {
                    // Left Arrow (Previous item)
                    if (currentIndex > 0) {
                        IconButton(
                            onClick = { onFileSelected(sortedFiles[currentIndex - 1].id) },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(12.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .testTag("lightbox_prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Previous File",
                                tint = Color.White
                            )
                        }
                    }

                    // Right Arrow (Next item)
                    if (currentIndex < sortedFiles.size - 1) {
                        IconButton(
                            onClick = { onFileSelected(sortedFiles[currentIndex + 1].id) },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(12.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .testTag("lightbox_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Next File",
                                tint = Color.White
                            )
                        }
                    }
                }

                // 4. Cognitive Telemetry / AI Sidebar info panel (Floating blur drawer)
                AnimatedVisibility(
                    visible = isInfoPanelVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp) // Leave safety gap for carousel strip
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.94f)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🧠 COGNITIVE TELEMETRY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFA5B4FC),
                                        letterSpacing = 1.sp
                                    )
                                )
                                IconButton(
                                    onClick = { isInfoPanelVisible = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss telemetry",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = file.fileName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = file.aiSummary ?: "Awaiting Cognitive analysis. Tap \"AI Re-Analysis\" to trigger Gemini model cataloging.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, lineHeight = 16.sp)
                            )
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            // Badges strip
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable { onCategoryClick(file.category) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "✦ ${file.category}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = file.fileType,
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray, fontWeight = FontWeight.Bold)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "%.2f MB".format(file.fileSize / 1048576f),
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            if (file.tags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    file.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.1f))
                                                .clickable { onTagClick(tag) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "#$tag",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Technical Specifications
                            Divider(color = Color.White.copy(alpha = 0.06f))
                            Spacer(modifier = Modifier.height(10.dp))
                            SpecsDetailRow(label = "Local Storage URI", value = file.localUri ?: "Unspecified local mapping")
                            SpecsDetailRow(label = "Vault Signature ID", value = "SHA-256#${file.id}")
                            SpecsDetailRow(label = "Cloud Sync Registration", value = file.cloudUrl ?: "Awaiting Sync Trigger")

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Actions inside Sidebar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onOrganizeClick,
                                    enabled = !isOrganizing,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    if (isOrganizing) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                    } else {
                                        Text("AI Re-Analyze", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                
                                Button(
                                    onClick = onDeleteClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Delete File", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // 5. Filmstrip Horizontal Thumbnails Index (Carousel Bottom Bar)
                if (sortedFiles.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(vertical = 10.dp)
                    ) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().testTag("lightbox_thumbnails_carousel"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(sortedFiles) { item ->
                                val isCurrent = item.id == file.id
                                val selectionBorder = if (isCurrent) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                }
                                
                                Card(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clickable { onFileSelected(item.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = selectionBorder,
                                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = getMediaAestheticUrl(item),
                                            contentDescription = "Go to file ${item.fileName}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (isCurrent) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Chronological Date-Range Helper Functions
fun getStartOfDay(calendar: Calendar): Long {
    val cal = calendar.clone() as Calendar
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun getEndOfDay(calendar: Calendar): Long {
    val cal = calendar.clone() as Calendar
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.timeInMillis
}

fun isSameDay(cal: Calendar, timeMillis: Long): Boolean {
    val otherCal = Calendar.getInstance().apply { timeInMillis = timeMillis }
    return cal.get(Calendar.YEAR) == otherCal.get(Calendar.YEAR) &&
           cal.get(Calendar.DAY_OF_YEAR) == otherCal.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun CustomDatePickerDialog(
    initialStartMillis: Long?,
    initialEndMillis: Long?,
    onDismissRequest: () -> Unit,
    onDateRangeSelected: (Long?, Long?) -> Unit
) {
    var selectedStart by remember { mutableStateOf(initialStartMillis) }
    var selectedEnd by remember { mutableStateOf(initialEndMillis) }
    
    var currentMonthCalendar by remember { 
        mutableStateOf(
            Calendar.getInstance().apply {
                initialStartMillis?.let { timeInMillis = it }
            }
        ) 
    }
    
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dayOfMonthFormat = remember { SimpleDateFormat("d", Locale.getDefault()) }
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHRONOLOGICAL VAULT INDEX",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close Date Range Picker", tint = Color.LightGray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Visual Indicator of selected range
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("START DATE", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray))
                        Text(
                            text = selectedStart?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it)) } ?: "Not selected",
                            style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "to",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text("END DATE", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray))
                        Text(
                            text = selectedEnd?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it)) } ?: "Not selected",
                            style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Month Selector Navigation Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val newCal = currentMonthCalendar.clone() as Calendar
                            newCal.add(Calendar.MONTH, -1)
                            currentMonthCalendar = newCal
                        }
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Prev Month", tint = Color.White)
                    }
                    
                    Text(
                        text = monthFormat.format(currentMonthCalendar.time),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    
                    IconButton(
                        onClick = {
                            val newCal = currentMonthCalendar.clone() as Calendar
                            newCal.add(Calendar.MONTH, 1)
                            currentMonthCalendar = newCal
                        }
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Month", tint = Color.White)
                    }
                }

                // Days Header Row (Su, Mo, Tu, We, Th, Fr, Sa)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val daysHeader = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                    daysHeader.forEach { dayName ->
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.width(36.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Calendar Days Grid Column list
                val cal = (currentMonthCalendar.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 7 = Saturday
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                
                // Construct rows
                val dayItems = mutableListOf<Calendar?>()
                // Leading paddings/empty days
                for (i in 1 until firstDayOfWeek) {
                    dayItems.add(null)
                }
                // Days of current month
                for (i in 1..daysInMonth) {
                    val dayCal = cal.clone() as Calendar
                    dayCal.set(Calendar.DAY_OF_MONTH, i)
                    dayItems.add(dayCal)
                }
                
                // Chunk into weeks (7 days per row)
                val weeks = dayItems.chunked(7)
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    weeks.forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            week.forEach { dayCal ->
                                if (dayCal == null) {
                                    Spacer(modifier = Modifier.size(36.dp))
                                } else {
                                    val dayTime = dayCal.timeInMillis
                                    val isStart = selectedStart?.let { isSameDay(dayCal, it) } ?: false
                                    val isEnd = selectedEnd?.let { isSameDay(dayCal, it) } ?: false
                                    val isBetween = selectedStart != null && selectedEnd != null && 
                                            dayTime > selectedStart!! && dayTime < selectedEnd!!
                                            
                                    val cellColor = when {
                                        isStart || isEnd -> MaterialTheme.colorScheme.primary
                                        isBetween -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    }
                                    
                                    val contentColor = when {
                                        isStart || isEnd -> Color.White
                                        isBetween -> MaterialTheme.colorScheme.primary
                                        else -> Color.White
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(cellColor)
                                            .clickable {
                                                val clickedTime = dayCal.timeInMillis
                                                if (selectedStart == null) {
                                                    selectedStart = getStartOfDay(dayCal)
                                                } else if (selectedEnd == null) {
                                                    if (clickedTime < selectedStart!!) {
                                                        selectedStart = getStartOfDay(dayCal)
                                                    } else {
                                                        selectedEnd = getEndOfDay(dayCal)
                                                    }
                                                } else {
                                                    selectedStart = getStartOfDay(dayCal)
                                                    selectedEnd = null
                                                }
                                            }
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayOfMonthFormat.format(dayCal.time),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (isStart || isEnd) FontWeight.Bold else FontWeight.Normal,
                                                color = contentColor
                                            )
                                        )
                                    }
                                }
                            }
                            // Fill remaining slots in week
                            if (week.size < 7) {
                                repeat(7 - week.size) {
                                    Spacer(modifier = Modifier.size(36.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDateRangeSelected(selectedStart, selectedEnd)
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("apply_custom_date_range_button")
            ) {
                Text("Apply Range")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    selectedStart = null
                    selectedEnd = null
                    onDateRangeSelected(null, null)
                    onDismissRequest()
                },
                modifier = Modifier.testTag("reset_custom_date_range_button")
            ) {
                Text("Reset", color = MaterialTheme.colorScheme.error)
            }
        },
        containerColor = Color(0xFF141F39),
        shape = RoundedCornerShape(16.dp)
    )
}

// Multi-attribute and metadata matching function for search query
fun matchMetadataSearch(file: MediaFile, query: String): Boolean {
    if (query.isBlank()) return true
    
    // Split the query by spaces. To support spaces within tags/filenames, we split by space.
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return true
    
    // All space-separated terms must match (logical AND)
    return terms.all { term ->
        when {
            // Precise tag search when query starts with '#'
            term.startsWith("#") -> {
                val tagSearch = term.removePrefix("#").trim()
                file.tags.split(",").any { it.trim().contains(tagSearch, ignoreCase = true) }
            }
            
            // Key-value metadata search when query contains ':'
            term.contains(":") -> {
                val parts = term.split(":", limit = 2)
                val key = parts[0].lowercase().trim()
                val value = parts[1].trim()
                
                when (key) {
                    "type", "category" -> {
                        file.fileType.contains(value, ignoreCase = true) ||
                        file.category.contains(value, ignoreCase = true)
                    }
                    "app", "source" -> {
                        file.sourceApp.contains(value, ignoreCase = true)
                    }
                    "ext" -> {
                        file.fileName.endsWith(value, ignoreCase = true) ||
                        file.fileType.contains(value, ignoreCase = true)
                    }
                    "tag" -> {
                        file.tags.split(",").any { it.trim().contains(value, ignoreCase = true) }
                    }
                    else -> {
                        // fallback if colon was typed but key doesn't match predefined metadata fields
                        file.fileName.contains(term, ignoreCase = true) ||
                        file.tags.contains(term, ignoreCase = true) ||
                        (file.aiSummary ?: "").contains(term, ignoreCase = true) ||
                        file.sourceApp.contains(term, ignoreCase = true)
                    }
                }
            }
            
            // Numeric comparison for size, width, or height
            term.contains(">") || term.contains("<") -> {
                val isGreater = term.contains(">")
                val operator = if (isGreater) ">" else "<"
                val parts = term.split(operator, limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].lowercase().trim()
                    val valueStr = parts[1].trim()
                    
                    when (key) {
                        "size" -> {
                            val bytes = parseSizeStringToBytes(valueStr)
                            if (bytes != null) {
                                if (isGreater) file.fileSize > bytes else file.fileSize < bytes
                            } else true
                        }
                        else -> {
                            file.fileName.contains(term, ignoreCase = true) ||
                            file.tags.contains(term, ignoreCase = true) ||
                            (file.aiSummary ?: "").contains(term, ignoreCase = true)
                        }
                    }
                } else {
                    file.fileName.contains(term, ignoreCase = true) ||
                    file.tags.contains(term, ignoreCase = true) ||
                    (file.aiSummary ?: "").contains(term, ignoreCase = true)
                }
            }
            
            // Default free text search across many fields
            else -> {
                file.fileName.contains(term, ignoreCase = true) ||
                file.tags.split(",").any { it.trim().contains(term, ignoreCase = true) } ||
                file.category.contains(term, ignoreCase = true) ||
                (file.aiSummary ?: "").contains(term, ignoreCase = true) ||
                file.sourceApp.contains(term, ignoreCase = true)
            }
        }
    }
}

fun parseSizeStringToBytes(sizeStr: String): Long? {
    val clean = sizeStr.lowercase().trim()
    val numberPart = clean.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return null
    return when {
        clean.endsWith("gb") || clean.endsWith("g") -> (numberPart * 1024 * 1024 * 1024).toLong()
        clean.endsWith("mb") || clean.endsWith("m") -> (numberPart * 1024 * 1024).toLong()
        clean.endsWith("kb") || clean.endsWith("k") -> (numberPart * 1024).toLong()
        else -> numberPart.toLong()
    }
}

