package com.example.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.delay
import java.util.UUID

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = MediaRepository(database.mediaFileDao())

        val pendingFiles = repository.getPendingSyncFiles()
        if (pendingFiles.isEmpty()) {
            return Result.success()
        }

        // Simulate secure background synchronization
        for (file in pendingFiles) {
            delay(1000) // Delay to simulate network transmission
            val simulatedCloudPath = "https://omni-vault.s3.us-west-2.amazonaws.com/u/vault_${UUID.randomUUID().toString().take(6)}_${file.fileName}"
            val updatedFile = file.copy(
                syncStatus = "SYNCED",
                cloudUrl = simulatedCloudPath
            )
            repository.update(updatedFile)
        }

        return Result.success()
    }
}
