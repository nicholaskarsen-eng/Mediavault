package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "cloud_accounts")
data class CloudAccount(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val provider: String, // "AWS S3", "Azure Blob", etc.
    val accountName: String,
    val region: String,
    val type: String, // "Primary", "Backup", "Archive", "DR"
    val apiKey: String? = null,
    val secretKey: String? = null,
    val endpoint: String? = null,
    val bucketName: String? = null
)
