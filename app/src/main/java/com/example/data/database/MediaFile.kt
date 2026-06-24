package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_files")
data class MediaFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val fileType: String, // "IMAGE", "VIDEO", "AUDIO", "DOCUMENT"
    val sourceApp: String, // "WhatsApp", "Telegram", "Screenshots", "Camera", "Downloads"
    val fileSize: Long, // in bytes
    val timestamp: Long, // milliseconds
    val category: String = "Uncategorized", // "Memes", "Finance", "Personal", "Work", etc.
    val tags: String = "", // comma-separated strings
    val syncStatus: String = "PENDING", // Overall status: "PENDING", "PARTIAL", "SYNCED"
    val primaryUrl: String? = null,
    val backupUrl: String? = null,
    val archiveUrl: String? = null,
    val disasterRecoveryUrl: String? = null,
    val primarySyncStatus: String = "PENDING",
    val backupSyncStatus: String = "PENDING",
    val archiveSyncStatus: String = "PENDING",
    val disasterRecoverySyncStatus: String = "PENDING",
    val aiSummary: String? = null,
    val localUri: String? = null
) {
    val cloudUrl: String? get() = primaryUrl ?: backupUrl ?: archiveUrl ?: disasterRecoveryUrl
}
