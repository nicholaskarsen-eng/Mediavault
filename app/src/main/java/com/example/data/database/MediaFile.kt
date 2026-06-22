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
    val syncStatus: String = "PENDING", // "PENDING", "SYNCED", "FAILED"
    val cloudUrl: String? = null,
    val aiSummary: String? = null,
    val localUri: String? = null
)
