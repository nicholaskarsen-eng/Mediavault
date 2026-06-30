package com.example.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.MediaViewModel

@Composable
fun ConfigView(
    viewModel: MediaViewModel,
    onScanClick: () -> Unit
) {
    val customRule by viewModel.customRule.collectAsStateWithLifecycle()
    val apiKeyField by viewModel.apiKey.collectAsStateWithLifecycle()
    val isWifiOnlySync by viewModel.isWifiOnlySync.collectAsStateWithLifecycle()
    val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsStateWithLifecycle()
    val isUniversalRepoEnabled by viewModel.isUniversalRepoEnabled.collectAsStateWithLifecycle()
    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val pairedDevices by viewModel.pairedDevices.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val autoOrganizeOnImport by viewModel.autoOrganizeOnImport.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val selectedAiModel by viewModel.selectedAiModel.collectAsStateWithLifecycle()
    val maxVaultSizeMb by viewModel.maxVaultSizeMb.collectAsStateWithLifecycle()
    val autoDeleteAfterDays by viewModel.autoDeleteAfterDays.collectAsStateWithLifecycle()
    val redundancyLevel by viewModel.redundancyLevel.collectAsStateWithLifecycle()
    val connectedAccounts by viewModel.connectedAccounts.collectAsStateWithLifecycle()
    val isApiKeyConfigured = viewModel.isApiKeyConfigured

    var nodeToEdit by remember { mutableStateOf<com.example.data.database.CloudAccount?>(null) }
    var showNewNodeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "App Configuration",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Text(
            text = "System preferences and security parameters",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- AI & ENGINE ---
        SettingsSectionHeader("AI & ENGINE")
        
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
                    Text(
                        text = "Gemini API Connectivity",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isApiKeyConfigured) Color(0xFF4CAF50) else Color(0xFFF44336))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isApiKeyConfigured) "CONNECTED" else "MISSING KEY",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                SettingSelectItem(
                    title = "AI Model Engine",
                    options = listOf("gemini-1.5-flash", "gemini-1.5-pro"),
                    selectedOption = selectedAiModel,
                    onOptionSelected = { viewModel.updateSelectedAiModel(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "GEMINI API KEY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKeyField,
                    onValueChange = { viewModel.updateApiKey(it) },
                    placeholder = { Text("Paste your Gemini API key here...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall,
                    visualTransformation = if (apiKeyField.length > 4) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "GLOBAL CLASSIFICATION RULE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customRule,
                    onValueChange = { viewModel.updateCustomRule(it) },
                    placeholder = { Text("e.g. Always categorize dark memes as 'Dark Humour'...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                SettingSwitchItem(
                    title = "Auto-Organize on Import",
                    description = "Trigger AI analysis immediately when new media is detected.",
                    checked = autoOrganizeOnImport,
                    onCheckedChange = { viewModel.updateAutoOrganizeOnImport(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- CLOUD ACCOUNTS ---
        SettingsSectionHeader("CLOUD ACCOUNTS")
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                connectedAccounts.forEach { account ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = account.accountName, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            Text(text = "${account.provider} • ${account.region} • ${account.type}", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray))
                        }
                        Row {
                            IconButton(onClick = { nodeToEdit = account }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit account", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { viewModel.unlinkAccount(account.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove account", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (account != connectedAccounts.last()) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                
                if (connectedAccounts.isEmpty()) {
                    Text(text = "No cloud accounts linked.", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray))
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showNewNodeDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+ Add Account", fontSize = 10.sp)
                    }

                    Button(
                        onClick = { viewModel.autoConfigureRedundancyFromAccounts() },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Auto-Sync Config", fontSize = 10.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { viewModel.syncConfigurationToCloud() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isCloudSyncEnabled && isUniversalRepoEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Push System Config to Matrix", fontSize = 10.sp, color = Color.White)
                }
            }
        }

        if (showNewNodeDialog || nodeToEdit != null) {
            NodeConfigDialog(
                accountToEdit = nodeToEdit,
                onDismiss = {
                    showNewNodeDialog = false
                    nodeToEdit = null
                },
                viewModel = viewModel
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- STORAGE & CLEANUP ---
        SettingsSectionHeader("STORAGE & CLEANUP")
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (maxVaultSizeMb >= 10000) "Vault Size Limit: Cloud Matrix Maximum" else "Vault Size Limit: $maxVaultSizeMb MB",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
                Slider(
                    value = maxVaultSizeMb.toFloat().coerceIn(100f, 10000f),
                    onValueChange = { viewModel.updateMaxVaultSize(it.toInt()) },
                    valueRange = 100f..10000f,
                    steps = 99,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "Auto-Delete Retention: ${if (autoDeleteAfterDays == 0) "Disabled" else "$autoDeleteAfterDays Days"}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
                Slider(
                    value = autoDeleteAfterDays.toFloat(),
                    onValueChange = { viewModel.updateAutoDeleteAfterDays(it.toInt()) },
                    valueRange = 0f..90f,
                    steps = 90,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- NETWORK & SYNC ---
        SettingsSectionHeader("NETWORK & SYNC")
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSwitchItem(
                    title = "Enable Cloud Mirroring",
                    description = "Activate multi-node redundancy across the storage matrix.",
                    checked = isCloudSyncEnabled,
                    onCheckedChange = { viewModel.updateCloudSyncEnabled(it) }
                )
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))

                SettingSwitchItem(
                    title = "Sync over Wi-Fi only",
                    description = "Reduce mobile data usage by restricting cloud mirror uploads to Wi-Fi.",
                    checked = isWifiOnlySync,
                    onCheckedChange = { viewModel.updateWifiOnlySync(it) }
                )
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))

                SettingSwitchItem(
                    title = "Universal Repository Protocol",
                    description = "Allow cross-device asset consolidation and shared metadata indexing.",
                    checked = isUniversalRepoEnabled,
                    onCheckedChange = { viewModel.updateUniversalRepoEnabled(it) }
                )
                
                if (isUniversalRepoEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "PAIRED DEVICES (${pairedDevices.size})",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontWeight = FontWeight.Bold)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    pairedDevices.forEach { devId ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhoneAndroid, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(devId, color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.unpairDevice(devId) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    
                    if (pairedDevices.isEmpty()) {
                        Text("No external devices linked.", color = Color.DarkGray, fontSize = 10.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    var showPairingDialog by remember { mutableStateOf(false) }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showPairingDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pair Device", fontSize = 10.sp)
                        }
                    }
                    
                    if (showPairingDialog) {
                        PairingDialog(
                            deviceId = deviceId,
                            onDismiss = { showPairingDialog = false },
                            onScanClick = {
                                showPairingDialog = false
                                onScanClick()
                            }
                        )
                    }
                }
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                
                Text(
                    text = "Sync Redundancy Nodes: $redundancyLevel",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Number of simultaneous cloud mirrors to maintain.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
                Slider(
                    value = redundancyLevel.toFloat(),
                    onValueChange = { viewModel.updateRedundancyLevel(it.toInt()) },
                    valueRange = 1f..4f,
                    steps = 2,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- PREFERENCES & SECURITY ---
        SettingsSectionHeader("PREFERENCES & SECURITY")
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSelectItem(
                    title = "App Theme",
                    options = listOf("Light", "Dark", "System"),
                    selectedOption = appTheme,
                    onOptionSelected = { viewModel.updateAppTheme(it) }
                )
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                
                SettingSwitchItem(
                    title = "Enable Notifications",
                    description = "Receive alerts for sync completion and AI analysis results.",
                    checked = notificationsEnabled,
                    onCheckedChange = { viewModel.updateNotificationsEnabled(it) }
                )
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                
                SettingSwitchItem(
                    title = "Biometric Lock",
                    description = "Require fingerprint or face unlock to access the Secure Vault.",
                    checked = isBiometricEnabled,
                    onCheckedChange = { viewModel.updateBiometricEnabled(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- DANGER ZONE ---
        SettingsSectionHeader("DANGER ZONE", color = Color(0xFFEF4444))
        
        var showPurgeConfirm by remember { mutableStateOf(false) }
        
        Button(
            onClick = { showPurgeConfirm = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, Color(0xFFEF4444)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Purge All Local Vault Data", color = Color(0xFFEF4444))
        }

        if (showPurgeConfirm) {
            AlertDialog(
                onDismissRequest = { showPurgeConfirm = false },
                title = { Text("Purge Entire Vault?", color = Color.White) },
                text = { Text("This will permanently delete all local file metadata and indexing. This action cannot be undone.", color = Color.LightGray) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAll()
                            showPurgeConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Confirm Purge")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPurgeConfirm = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // --- ABOUT ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.DarkGray),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Media Vault OSync v2.1.0",
                style = MaterialTheme.typography.labelSmall.copy(color = Color.DarkGray, fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Secure Sandbox Architecture • AES-256 Encryption",
                style = MaterialTheme.typography.labelSmall.copy(color = Color.DarkGray, fontSize = 9.sp)
            )
            Text(
                text = "© 2024 AI Studio Hub. All rights reserved.",
                style = MaterialTheme.typography.labelSmall.copy(color = Color.DarkGray, fontSize = 8.sp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PairingDialog(
    deviceId: String,
    onDismiss: () -> Unit,
    onScanClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Pair More Devices",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                
                Text(
                    "Scan this QR code from another device to link them to your Universal Repository.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                // QR Code Placeholder / Generation
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    QRCodeImage(text = deviceId, size = 168)
                }
                
                Text(
                    "Device ID: $deviceId",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontFamily = FontFamily.Monospace)
                )
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                
                Button(
                    onClick = onScanClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.QrCodeScanner, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Other Device")
                }
                
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color.LightGray)
                }
            }
        }
    }
}
