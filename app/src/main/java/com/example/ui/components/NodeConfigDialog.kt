package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.CloudAccount
import com.example.ui.viewmodel.MediaViewModel

@Composable
fun NodeConfigDialog(
    accountToEdit: CloudAccount?,
    onDismiss: () -> Unit,
    viewModel: MediaViewModel
) {
    var provider by remember { mutableStateOf(accountToEdit?.provider ?: "AWS S3") }
    var name by remember { mutableStateOf(accountToEdit?.accountName ?: "new-mirror-node") }
    var region by remember { mutableStateOf(accountToEdit?.region ?: "us-west-2") }
    var type by remember { mutableStateOf(accountToEdit?.type ?: "Backup") }
    var apiKey by remember { mutableStateOf(accountToEdit?.apiKey ?: "") }
    var secretKey by remember { mutableStateOf(accountToEdit?.secretKey ?: "") }
    var bucketName by remember { mutableStateOf(accountToEdit?.bucketName ?: "") }
    var endpoint by remember { mutableStateOf(accountToEdit?.endpoint ?: "") }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }

    val discoveredNodes by viewModel.discoveredNodes.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val connectedAccounts by viewModel.connectedAccounts.collectAsStateWithLifecycle()

    // Suggested naming logic when provider changes
    LaunchedEffect(provider) {
        testStatus = null
        if (accountToEdit == null) {
            val existingCount = connectedAccounts.count { it.provider == provider }
            val suffix = if (existingCount > 0) " #${existingCount + 1}" else ""
            name = "$provider Mirror$suffix"
        }
    }

    LaunchedEffect(endpoint, apiKey, secretKey, bucketName) {
        testStatus = null
    }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            endpoint = it.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (accountToEdit != null) "Configure Cloud Mirror Node" else "Link Cloud Mirror Node", color = Color.White) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (accountToEdit == null && provider == "SMB Share") {
                    Button(
                        onClick = { viewModel.startNetworkDiscovery() },
                        enabled = !isDiscovering,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                    ) {
                        if (isDiscovering) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scanning Network...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Auto-Discover Local Nodes", fontSize = 12.sp)
                        }
                    }

                    if (discoveredNodes.isNotEmpty()) {
                        Text("DISCOVERED NODES", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        discoveredNodes.forEach { node ->
                            Card(
                                onClick = {
                                    name = node.name
                                    endpoint = node.endpoint
                                    provider = node.provider
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(node.name, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(node.endpoint, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account/Node Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                
                Column {
                    Text("Provider", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { providerDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = provider, style = MaterialTheme.typography.bodyMedium)
                                Icon(
                                    imageVector = if (providerDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        }
                        
                        DropdownMenu(
                            expanded = providerDropdownExpanded,
                            onDismissRequest = { providerDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.8f).background(Color(0xFF1E293B))
                        ) {
                            listOf("AWS S3", "Azure Blob", "Mega", "pCloud", "Dropbox", "Google Drive", "OneDrive", "SMB Share", "External SD").forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p, color = Color.White) },
                                    onClick = {
                                        provider = p
                                        providerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Detailed help text per provider
                Text(
                    text = when(provider) {
                        "AWS S3" -> "Use IAM user with PutObject and GetObject permissions."
                        "Azure Blob" -> "Connection string or Account Key required."
                        "SMB Share" -> "Configure local network path (e.g. 192.168.1.10)."
                        "External SD" -> "Mount point for physical storage expansion."
                        "Google Drive" -> "Requires OAuth2 Client ID and API Access."
                        "OneDrive" -> "Requires Microsoft Graph API credentials."
                        else -> "Standard REST endpoint mirroring."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Column {
                    Text("Node Type", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { typeDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = type, style = MaterialTheme.typography.bodyMedium)
                                Icon(
                                    imageVector = if (typeDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        }
                        
                        DropdownMenu(
                            expanded = typeDropdownExpanded,
                            onDismissRequest = { typeDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.8f).background(Color(0xFF1E293B))
                        ) {
                            listOf("Primary", "Backup", "Archive", "DR").forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t, color = Color.White) },
                                    onClick = {
                                        type = t
                                        typeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                val isExternalSd = provider == "External SD"
                val isSmb = provider == "SMB Share"
                val needsRegion = provider in listOf("AWS S3", "Azure Blob", "pCloud", "SMB Share")

                if (needsRegion) {
                    OutlinedTextField(
                        value = region,
                        onValueChange = { region = it },
                        label = { Text(if (provider == "SMB Share") "Domain / Workgroup (Optional)" else "Region / Location") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }

                if (!isExternalSd) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Text("Security Credentials", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(if (isSmb) "Username" else "API Key / Access Key ID") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    OutlinedTextField(
                        value = secretKey,
                        onValueChange = { secretKey = it },
                        label = { Text(if (isSmb) "Password" else "Secret Key / Token") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Text("Target Mapping", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

                if (!isExternalSd) {
                    OutlinedTextField(
                        value = bucketName,
                        onValueChange = { bucketName = it },
                        label = { Text(if (isSmb) "Share Name" else "Bucket / Container Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }

                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text(if (isSmb) "Host IP/Name" else if (isExternalSd) "Target Path (URI)" else "Custom Endpoint URL") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (isSmb) "192.168.1.100" else if (isExternalSd) "Select folder on SD card..." else "https://s3.custom.com", color = Color.DarkGray) },
                    trailingIcon = {
                        if (isExternalSd) {
                            IconButton(onClick = { dirPickerLauncher.launch(null) }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Browse")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        isTesting = true
                        testStatus = null
                        val tempAccount = CloudAccount(
                            provider = provider,
                            accountName = name,
                            region = region,
                            type = type,
                            apiKey = apiKey,
                            secretKey = secretKey,
                            bucketName = bucketName,
                            endpoint = endpoint
                        )
                        viewModel.testConnection(tempAccount) { success, msg ->
                            isTesting = false
                            testStatus = msg
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Connectivity", fontSize = 12.sp)
                    }
                }

                if (testStatus != null) {
                    Text(
                        text = testStatus!!,
                        color = if (testStatus!!.contains("Success")) Color(0xFF4ADE80) else Color(0xFFEF4444),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.linkAccount(provider, name, region, type, apiKey, secretKey, bucketName, endpoint, id = accountToEdit?.id)
                onDismiss()
            }) {
                Text(if (accountToEdit != null) "Save Configuration" else "Link Node")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}
