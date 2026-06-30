package com.example.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = MediaRepository(database.mediaFileDao())
        val cloudAccountDao = database.cloudAccountDao()

        val pendingFiles = repository.getPendingSyncFiles()
        if (pendingFiles.isEmpty()) {
            return Result.success()
        }

        val accounts = cloudAccountDao.getAllAccountsDirect()
        if (accounts.isEmpty()) {
            return Result.success()
        }

        val semaphore = Semaphore(3) // Concurrency limit for background sync

        // Secure parallel background synchronization
        coroutineScope {
            pendingFiles.map { file ->
                async {
                    semaphore.withPermit {
                        val localUri = file.localUri ?: return@async
                        var updatedFile = file
                        
                        for (account in accounts) {
                            try {
                                val remoteUrl = com.example.data.api.CloudStorageClient.uploadFile(
                                    context = applicationContext,
                                    localUri = localUri,
                                    account = account,
                                    fileName = file.fileName
                                )
                                
                                updatedFile = when (account.type) {
                                    "Primary" -> updatedFile.copy(primarySyncStatus = "SYNCED", primaryUrl = remoteUrl)
                                    "Backup" -> updatedFile.copy(backupSyncStatus = "SYNCED", backupUrl = remoteUrl)
                                    "Archive" -> updatedFile.copy(archiveSyncStatus = "SYNCED", archiveUrl = remoteUrl)
                                    "DR" -> updatedFile.copy(disasterRecoverySyncStatus = "SYNCED", disasterRecoveryUrl = remoteUrl)
                                    else -> updatedFile
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        
                        val finalStatus = if (updatedFile.primarySyncStatus == "SYNCED") "SYNCED" else "PARTIAL"
                        repository.update(updatedFile.copy(syncStatus = finalStatus))
                    }
                }
            }.awaitAll()
        }

        return Result.success()
    }
}
