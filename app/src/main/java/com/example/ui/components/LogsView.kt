package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.MediaViewModel

@Composable
fun LogsView(viewModel: MediaViewModel) {
    val aiLogs by viewModel.aiLogs.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    var selectedLogType by remember { mutableStateOf("All") }

    val combinedLogs = remember(aiLogs, syncLogs, selectedLogType) {
        val all = (aiLogs + syncLogs).sortedByDescending { it.takeWhile { char -> char != ']' } } // Heuristic sort
        when (selectedLogType) {
            "AI" -> aiLogs.reversed()
            "Sync" -> syncLogs.reversed()
            else -> all
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "System Protocols",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Text(
            text = "Real-time execution telemetry and decision logs",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "AI", "Sync").forEach { type ->
                val isSelected = selectedLogType == type
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedLogType = type },
                    label = { Text(type) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.05f),
                        labelColor = Color.Gray,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    ),
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f))
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(combinedLogs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                log.contains("[AI]") -> Color(0xFFA5B4FC)
                                log.contains("[Sync Engine]") -> Color(0xFFFDE047)
                                log.contains("[System]") -> Color(0xFF38BDF8)
                                log.contains("Error", ignoreCase = true) -> Color(0xFFEF4444)
                                log.contains("Success", ignoreCase = true) -> Color(0xFF4ADE80)
                                else -> Color.LightGray
                            }
                        )
                    )
                }
            }
        }
    }
}
