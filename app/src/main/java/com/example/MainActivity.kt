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
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.database.MediaFile
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MediaViewModel
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
                        }
                    )
                    1 -> SyncView(viewModel)
                    2 -> AIOrganizerView(mediaFiles, viewModel)
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
    onImportClick: () -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedAppFilter by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFileForDetailId by remember { mutableStateOf<Int?>(null) }
    val selectedFileForDetail = remember(selectedFileForDetailId, mediaFiles) {
        mediaFiles.find { it.id == selectedFileForDetailId }
    }
    var viewMode by remember { mutableStateOf("grid") }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Relevance") }

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

    val filteredFiles = remember(mediaFiles, selectedCategoryFilter, selectedAppFilter, searchQuery) {
        mediaFiles.filter { file ->
            val matchCategory = selectedCategoryFilter == "All" || file.category == selectedCategoryFilter
            val matchApp = selectedAppFilter == "All" || file.sourceApp.equals(selectedAppFilter, ignoreCase = true)
            val matchSearchQuery = searchQuery.isEmpty() ||
                    file.fileName.contains(searchQuery, ignoreCase = true) ||
                    file.tags.contains(searchQuery, ignoreCase = true) ||
                    file.category.contains(searchQuery, ignoreCase = true) ||
                    (file.aiSummary ?: "").contains(searchQuery, ignoreCase = true) ||
                    file.sourceApp.contains(searchQuery, ignoreCase = true)
            matchCategory && matchApp && matchSearchQuery
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
            placeholder = { Text("Search tags, categories, filenames...", color = Color.Gray) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Icon",
                    tint = Color.LightGray
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Search",
                            tint = Color.LightGray
                        )
                    }
                }
            } else null,
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

        Spacer(modifier = Modifier.height(4.dp))

        // Files Grid Explorer
        if (filteredFiles.isEmpty()) {
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
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedFiles) { file ->
                        MediaFileGridCard(
                            file = file,
                            onOrganizeClick = { viewModel.organizeSingleFile(file) },
                            onDeleteClick = { viewModel.deleteFile(file.id) },
                            onCardClick = { selectedFileForDetailId = file.id },
                            onTagClick = { tag ->
                                searchQuery = tag
                                val key = tag.lowercase().trim()
                                tagSearchFrequency[key] = (tagSearchFrequency[key] ?: 0) + 1
                            },
                            relevanceScore = calculateRelevance(file, searchQuery, tagSearchFrequency)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedFiles) { file ->
                        MediaFileRow(
                            file = file,
                            onOrganizeClick = { viewModel.organizeSingleFile(file) },
                            onDeleteClick = { viewModel.deleteFile(file.id) },
                            onCardClick = { selectedFileForDetailId = file.id },
                            onTagClick = { tag ->
                                searchQuery = tag
                                val key = tag.lowercase().trim()
                                tagSearchFrequency[key] = (tagSearchFrequency[key] ?: 0) + 1
                            },
                            relevanceScore = calculateRelevance(file, searchQuery, tagSearchFrequency)
                        )
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
            relevanceScore = calculateRelevance(fileDetail, searchQuery, tagSearchFrequency)
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
}

@Composable
fun MediaFileRow(
    file: MediaFile,
    onOrganizeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCardClick: () -> Unit,
    onTagClick: ((String) -> Unit)? = null,
    relevanceScore: Int = 0
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("item_media_${file.id}"),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

@Composable
fun MediaFileGridCard(
    file: MediaFile,
    onOrganizeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCardClick: () -> Unit,
    onTagClick: ((String) -> Unit)? = null,
    relevanceScore: Int = 0
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("item_grid_media_${file.id}"),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = when (file.category) {
                                "Finance" -> listOf(Color(0xFF2E7D32), Color(0xFF4CAF50))
                                "Memes" -> listOf(Color(0xFFE65100), Color(0xFFFF9800))
                                "Work" -> listOf(Color(0xFF1565C0), Color(0xFF2196F3))
                                "Recordings" -> listOf(Color(0xFFC2185B), Color(0xFFE91E63))
                                "Screenshots" -> listOf(Color(0xFF7B1FA2), Color(0xFF9C27B0))
                                "Personal" -> listOf(Color(0xFF00796B), Color(0xFF009688))
                                else -> listOf(Color(0xFF37474F), Color(0xFF607D8B))
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        end = androidx.compose.ui.geometry.Offset(0f, size.height),
                        strokeWidth = 2f
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (file.fileType) {
                            "IMAGE" -> Icons.Default.Share
                            "VIDEO" -> Icons.Default.PlayArrow
                            "AUDIO" -> Icons.Default.Share
                            else -> Icons.Default.Info
                        },
                        contentDescription = file.fileType,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = file.fileType,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }

                if (file.fileType == "VIDEO") {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play preview",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

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
fun SyncView(viewModel: MediaViewModel) {
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val mediaFiles by viewModel.mediaFiles.collectAsStateWithLifecycle()

    val pendingCount = mediaFiles.count { it.syncStatus == "PENDING" }
    val syncedCount = mediaFiles.count { it.syncStatus == "SYNCED" }
    val totalSize = mediaFiles.sumOf { it.fileSize }
    val syncedSize = mediaFiles.filter { it.syncStatus == "SYNCED" }.sumOf { it.fileSize }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
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
fun AIOrganizerView(mediaFiles: List<MediaFile>, viewModel: MediaViewModel) {
    val isOrganizing by viewModel.isOrganizing.collectAsStateWithLifecycle()
    val aiLogs by viewModel.aiLogs.collectAsStateWithLifecycle()
    val customRule by viewModel.customRule.collectAsStateWithLifecycle()

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
    relevanceScore: Int
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

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
                    Text(
                        text = "AI CLASSIFIED TOPICS / ONTOLOGIES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
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
