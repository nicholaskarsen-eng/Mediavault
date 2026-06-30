package com.example.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.database.MediaFile

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
            ),
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
                if (!file.localUri.isNullOrEmpty() && (file.fileType == "IMAGE" || file.fileType == "VIDEO")) {
                    AsyncImage(
                        model = file.localUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
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
                    onClick = onOrganizeClick
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Auto organize file metadata with AI",
                        tint = Color.LightGray
                    )
                }

                IconButton(
                    onClick = onDeleteClick
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

fun getMediaPreviewUrl(file: MediaFile): String? {
    return file.localUri ?: file.cloudUrl
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
            ),
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
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                if (!file.localUri.isNullOrEmpty() && (file.fileType == "IMAGE" || file.fileType == "VIDEO")) {
                    AsyncImage(
                        model = file.localUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (file.fileType == "VIDEO") {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                .padding(4.dp)
                        )
                    }
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.1f),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.1f),
                            start = Offset(size.width, 0f),
                            end = Offset(0f, size.height),
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
                }

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
                            modifier = Modifier.size(24.dp)
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
                            modifier = Modifier.size(24.dp)
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
fun EmptyVaultState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Info,
                contentDescription = "No media files",
                tint = Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No files match active filter.",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
            )
            Text(
                text = "Import actual resources to populate your vault.",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
            )
        }
    }
}
