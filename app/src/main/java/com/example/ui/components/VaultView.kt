package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.MediaFile
import com.example.ui.viewmodel.MediaViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultView(
    mediaFiles: List<MediaFile>,
    viewModel: MediaViewModel,
    onImportClick: () -> Unit,
    onAutoImportClick: () -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    val showUndoToast by viewModel.showUndoToast.collectAsStateWithLifecycle()
    val lastDeletedFiles by viewModel.lastDeletedFiles.collectAsStateWithLifecycle()
    var selectedAppFilter by remember { mutableStateOf("All") }
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
    
    var startDateMills by remember { mutableStateOf<Long?>(null) }
    var endDateMills by remember { mutableStateOf<Long?>(null) }

    val tagSearchFrequency = remember {
        mutableStateMapOf<String, Int>()
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

    val headerContent = @Composable {
        Column {
            // Upper Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Secure Vault",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "${mediaFiles.size} Items Indexed",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onImportClick,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Import", tint = Color.White)
                    }

                    IconButton(
                        onClick = onAutoImportClick,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF673AB7))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Auto Import", tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "APP REGISTRIES",
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val monitoredApps = listOf("All", "WhatsApp", "Telegram", "Screenshots", "Camera", "Downloads", "Discord", "Slack", "SD Card")
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
                        "SD Card" -> Color(0xFF4CAF50)
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Card(
                        modifier = Modifier
                            .width(100.dp)
                            .clickable { selectedAppFilter = app },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) appColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) appColor else Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(appColor)
                                )
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = appColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.sp
                                    )
                                )
                            }

                            Text(
                                text = app,
                                style = MaterialTheme.typography.labelMedium.copy(
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
                                    fontSize = 9.sp
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
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "API warning",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Offline Mode (No API Key)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Update .env file with your API key.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontSize = 10.sp)
                            )
                        }
                    }
                }
            }

            // Search Input Component
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search vault...", color = Color.Gray, style = MaterialTheme.typography.bodySmall) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
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
                        label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
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

            Spacer(modifier = Modifier.height(4.dp))

            // Sorting & Toggle toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact sort picker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("Relevance", "Newest", "Size").forEach { opt ->
                        val isSelected = sortBy == opt
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .clickable { sortBy = opt }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = opt,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                // View mode toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (viewMode == "list") MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewMode = "list" }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "List",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (viewMode == "grid") MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewMode = "grid" }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Grid",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
            
            Text(
                text = "${filteredFiles.size} items matching",
                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (viewMode == "grid") {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    headerContent()
                }

                if (filteredFiles.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyVaultState()
                    }
                } else {
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
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    headerContent()
                }

                if (filteredFiles.isEmpty()) {
                    item {
                        EmptyVaultState()
                    }
                } else {
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
                .widthIn(max = 500.dp),
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
                        modifier = Modifier.size(28.dp)
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

    // Detail Dialog Sheet ...
    selectedFileForDetail?.let { fileDetail ->
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
}
}
