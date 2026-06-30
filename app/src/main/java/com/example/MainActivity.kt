package com.example

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MediaViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.*
import com.example.ui.components.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = applicationContext as Application
            val viewModel: MediaViewModel = viewModel(
                factory = MediaViewModel.Factory(app)
            )
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            
            MyApplicationTheme(
                darkTheme = when(appTheme) {
                    "Dark" -> true
                    "Light" -> false
                    else -> isSystemInDarkTheme()
                }
            ) {
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
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val barcodeLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            viewModel.pairNewDevice(result.contents)
        }
    }

    // Permissions for Deep Scan
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.scanDeviceMedia()
        } else {
            Toast.makeText(context, "Storage permissions required for Deep Scan.", Toast.LENGTH_SHORT).show()
        }
    }

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

            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Not always possible depending on URI source, but essential for persistent access
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
                    label = { Text("Vault", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("tab_vault")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "Cloud Sync") },
                    label = { Text("Sync", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("tab_sync")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "AI Organizer") },
                    label = { Text("AI", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("tab_ai")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.Build, contentDescription = "App Config") },
                    label = { Text("Config", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("tab_config")
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { viewModel.selectTab(4) },
                    icon = { Icon(Icons.Default.List, contentDescription = "System Logs") },
                    label = { Text("Logs", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("tab_logs")
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

            if (isImporting) {
                Column(modifier = Modifier.align(Alignment.TopCenter)) {
                    LinearProgressIndicator(
                        progress = { importProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = Color.Transparent
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f))
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Importing Media... ${(importProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
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
                        onAutoImportClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.READ_MEDIA_IMAGES,
                                        android.Manifest.permission.READ_MEDIA_VIDEO,
                                        android.Manifest.permission.READ_MEDIA_AUDIO
                                    )
                                )
                            } else {
                                permissionsLauncher.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE))
                            }
                        }
                    )
                    1 -> SyncView(viewModel)
                    2 -> AIOrganizerView(mediaFiles, viewModel)
                    3 -> ConfigView(
                        viewModel = viewModel,
                        onScanClick = {
                            val options = ScanOptions()
                            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            options.setPrompt("Align QR code inside the frame")
                            options.setBeepEnabled(false)
                            options.setBarcodeImageEnabled(true)
                            options.setOrientationLocked(false)
                            barcodeLauncher.launch(options)
                        }
                    )
                    4 -> LogsView(viewModel)
                }
            }
        }
    }
}
