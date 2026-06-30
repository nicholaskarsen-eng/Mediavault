package com.example.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.database.CloudAccount
import com.example.ui.viewmodel.MediaViewModel

@Composable
fun SyncView(
    viewModel: MediaViewModel
) {
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsStateWithLifecycle()
    val isUniversalRepoEnabled by viewModel.isUniversalRepoEnabled.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val mediaFiles by viewModel.mediaFiles.collectAsStateWithLifecycle()
    val connectedAccounts by viewModel.connectedAccounts.collectAsStateWithLifecycle()

    val pendingCount = mediaFiles.count { it.syncStatus != "SYNCED" }

    var accountToEdit by remember { mutableStateOf<CloudAccount?>(null) }
    var showAddAccountDialog by remember { mutableStateOf(false) }

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

        Spacer(modifier = Modifier.height(16.dp))

        // Futuristic Cloud Illustrator Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, Brush.horizontalGradient(listOf(Color(0xFF2196F3), Color(0xFFE91E63))), RoundedCornerShape(16.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.media_vault_banner_1782142258765),
                contentDescription = "Cloud repository artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
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
                        "End-to-end encrypted multi-mirror deployment.",
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
                description = "%.1f MB payload".format(mediaFiles.filter { it.syncStatus != "SYNCED" }.sumOf { it.fileSize } / 1048576f),
                tint = Color(0xFFFF9800)
            )
            SyncMetricCard(
                modifier = Modifier.weight(1f),
                title = "MIRROR NODES",
                count = "${connectedAccounts.size} Active",
                description = connectedAccounts.joinToString(", ") { it.type },
                tint = Color(0xFF00BCD4)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connected Mirror Nodes List
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONNECTED MIRROR NODES",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray
                )
            )
            TextButton(onClick = { showAddAccountDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Link Node")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        connectedAccounts.forEach { account ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when(account.type) {
                                    "Primary" -> Color(0xFF2196F3)
                                    "Backup" -> Color(0xFF4CAF50)
                                    "Archive" -> Color(0xFFFFC107)
                                    "DR" -> Color(0xFFE91E63)
                                    else -> Color.Gray
                                }.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                account.provider.contains("SMB", ignoreCase = true) -> Icons.Default.Router
                                account.provider.contains("SD", ignoreCase = true) -> Icons.Default.SdCard
                                account.type == "Primary" -> Icons.Default.Cloud
                                account.type == "Backup" -> Icons.Default.CloudCircle
                                account.type == "Archive" -> Icons.Default.Storage
                                account.type == "DR" -> Icons.Default.Security
                                else -> Icons.Default.CloudQueue
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = when {
                                account.provider.contains("SMB", ignoreCase = true) -> Color(0xFF607D8B)
                                account.provider.contains("SD", ignoreCase = true) -> Color(0xFF795548)
                                account.type == "Primary" -> Color(0xFF2196F3)
                                account.type == "Backup" -> Color(0xFF4CAF50)
                                account.type == "Archive" -> Color(0xFFFFC107)
                                account.type == "DR" -> Color(0xFFE91E63)
                                else -> Color.Gray
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.accountName,
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${account.provider} • ${account.region} • ${account.type}",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                            if (!account.apiKey.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Encrypted credentials",
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                    
                    IconButton(onClick = { accountToEdit = account }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                    
                    IconButton(onClick = { viewModel.unlinkAccount(account.id) }) {
                        Icon(Icons.Default.Close, contentDescription = "Unlink", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }
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
                        text = if (isCloudSyncEnabled) {
                            if (isSyncing) "Syncing to ${connectedAccounts.size} mirror nodes..." 
                            else "Cloud synchronization is ACTIVE."
                        } else "Cloud synchronization is DISABLED.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = { viewModel.syncNow() },
                        enabled = isCloudSyncEnabled && !isSyncing && pendingCount > 0 && connectedAccounts.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                    ) {
                        if (isSyncing) {
                            Text("Deploying...")
                        } else {
                            Text("Sync Now")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isUniversalRepoEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Universal Repository Consolidation",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Pull shared repository items & config from mirror nodes.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                        )
                    }
                    
                    IconButton(
                        onClick = { viewModel.consolidateUniversalRepository() },
                        enabled = isCloudSyncEnabled && !isSyncing && connectedAccounts.isNotEmpty(),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Consolidate", tint = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

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

    if (showAddAccountDialog || accountToEdit != null) {
        NodeConfigDialog(
            accountToEdit = accountToEdit,
            onDismiss = {
                showAddAccountDialog = false
                accountToEdit = null
            },
            viewModel = viewModel
        )
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
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    fontSize = 9.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontSize = 10.sp)
            )
        }
    }
}
