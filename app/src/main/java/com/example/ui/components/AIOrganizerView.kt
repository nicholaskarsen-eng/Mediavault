package com.example.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.MediaFile
import com.example.ui.viewmodel.MediaViewModel

@Composable
fun AIOrganizerView(
    mediaFiles: List<MediaFile>,
    viewModel: MediaViewModel
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

        Spacer(modifier = Modifier.height(16.dp))

        // AI Agent custom instruction summary box
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🧠 ACTIVE CLASSIFICATION AGENT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (customRule.isBlank()) "Default behavioral logic active." else "Custom ruleset injected.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }

                    Button(
                        onClick = { viewModel.autoOrganizeAll() },
                        enabled = !isOrganizing && uncategorizedCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            disabledContainerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isOrganizing) {
                            Text("Agent cataloging...")
                        } else {
                            Text("AI Auto-Organize")
                        }
                    }
                }
                
                if (customRule.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "\"$customRule\"",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.LightGray,
                                fontStyle = FontStyle.Italic
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$uncategorizedCount Unorganized files found.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                )
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
                        )
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
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontStyle = FontStyle.Italic)
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
    }
}
