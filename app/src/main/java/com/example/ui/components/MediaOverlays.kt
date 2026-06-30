package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.database.MediaFile
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

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
                ),
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
                        "IMAGE", "VIDEO" -> {
                            if (!file.localUri.isNullOrEmpty()) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = file.localUri,
                                        contentDescription = "Media preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(8.dp)
                                    )
                                    
                                    if (file.fileType == "VIDEO") {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Video",
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier
                                                .size(64.dp)
                                                .align(Alignment.Center)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                .clickable {
                                                    try {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                            setDataAndType(Uri.parse(file.localUri!!), "video/*")
                                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .padding(12.dp)
                                        )
                                    }
                                }
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
                                                start = Offset(x, 0f),
                                                end = Offset(x, size.height),
                                                strokeWidth = 1f
                                            )
                                            drawLine(
                                                color = Color.White.copy(alpha = 0.03f),
                                                start = Offset(0f, y),
                                                end = Offset(size.width, y),
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
                                            imageVector = if (file.fileType == "IMAGE") Icons.Default.Share else Icons.Default.PlayArrow,
                                            contentDescription = "Preview placeholder",
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
                                            text = if (file.fileType == "IMAGE") "1920×1080 • PNG • sRGB Grid" else "4K • HEVC • STREAMING READY",
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
                        modifier = Modifier.fillMaxWidth(),
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
                                            clipboardManager?.setText(AnnotatedString(file.aiSummary))
                                            Toast.makeText(context, "AI Summary copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
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
                            modifier = Modifier.fillMaxWidth(),
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
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "MIRROR ENDPOINTS",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontWeight = FontWeight.Bold)
                            )
                            SpecsDetailRow(label = "Primary", value = if(file.primarySyncStatus == "SYNCED") file.primaryUrl ?: "SECURED" else "PENDING")
                            SpecsDetailRow(label = "Backup", value = if(file.backupSyncStatus == "SYNCED") file.backupUrl ?: "SECURED" else "PENDING")
                            SpecsDetailRow(label = "Archive", value = if(file.archiveSyncStatus == "SYNCED") file.archiveUrl ?: "SECURED" else "PENDING")
                            SpecsDetailRow(label = "Disaster Recovery", value = if(file.disasterRecoverySyncStatus == "SYNCED") file.disasterRecoveryUrl ?: "SECURED" else "PENDING")
                        }
                    }
                }

                // 3. Bottom controls
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
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
fun VideoPlayer(uri: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.6f)
    )
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
    var zoomScale by remember(file.id) { mutableFloatStateOf(1f) }
    var panOffset by remember(file.id) { mutableStateOf(Offset.Zero) }

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
                .fillMaxSize(),
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
                                    panOffset = Offset.Zero
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
                            val previewUrl = getMediaPreviewUrl(file)
                            if (previewUrl != null) {
                                AsyncImage(
                                    model = previewUrl,
                                    contentDescription = "Full-screen view of ${file.fileName}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(12.dp)
                                )
                            } else {
                                Icon(Icons.Default.Image, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                            }
                        }
                        "VIDEO" -> {
                            // High-fidelity production ready video player
                            if (!file.localUri.isNullOrEmpty()) {
                                VideoPlayer(uri = file.localUri)
                            } else {
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
                                    val previewUrl = getMediaPreviewUrl(file)
                                    if (previewUrl != null) {
                                        AsyncImage(
                                            model = previewUrl,
                                            contentDescription = "Video backdrop",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().alpha(0.45f)
                                        )
                                    }

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
                                    HorizontalDivider(color = Color.LightGray)
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
                                clipboardManager?.setText(AnnotatedString(file.cloudUrl ?: file.fileName))
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
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
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
                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
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
                            modifier = Modifier.fillMaxWidth(),
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
                                        val previewUrl = getMediaPreviewUrl(item)
                                        if (previewUrl != null) {
                                            AsyncImage(
                                                model = previewUrl,
                                                contentDescription = "Go to file ${item.fileName}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Info, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                                            }
                                        }
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
