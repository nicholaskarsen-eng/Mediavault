package com.example.data.sync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.database.MediaFile
import java.io.InputStream
import java.security.MessageDigest

class DuplicateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.mediaFileDao()

        try {
            // Fetch all files currently in database
            val allFiles = dao.getAllMediaFilesDirect()
            if (allFiles.isEmpty()) {
                return Result.success()
            }

            // 1. Calculate MD5 for any files that don't have it yet
            val updatedWithHashes = allFiles.map { file ->
                if (file.md5Hash.isNullOrBlank()) {
                    val computedHash = calculateMd5(applicationContext, file)
                    file.copy(md5Hash = computedHash)
                } else {
                    file
                }
            }

            // Commit MD5 changes to DB
            for (file in updatedWithHashes) {
                dao.updateMediaFile(file)
            }

            // 2. Identify matching hashes to flag duplicates
            val validHashGroups = updatedWithHashes
                .filter { !it.md5Hash.isNullOrBlank() }
                .groupBy { it.md5Hash }

            // Extract MD5 keys that appear more than once
            val duplicateHashes = validHashGroups.filter { it.value.size > 1 }.keys

            // Determine dynamic duplicate state of files
            val finalUpdatedFiles = updatedWithHashes.map { file ->
                val isDupe = file.md5Hash != null && file.md5Hash in duplicateHashes
                if (file.isDuplicate != isDupe) {
                    file.copy(isDuplicate = isDupe)
                } else {
                    file
                }
            }

            // Save final state update to Room
            for (file in finalUpdatedFiles) {
                dao.updateMediaFile(file)
            }

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }
    }

    private fun calculateMd5(context: Context, file: MediaFile): String {
        val uriStr = file.localUri
        if (!uriStr.isNullOrBlank()) {
            try {
                val uri = Uri.parse(uriStr)
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val digest = MessageDigest.getInstance("MD5")
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                    }
                    inputStream.close()
                    val md5Bytes = digest.digest()
                    return md5Bytes.joinToString("") { "%02x".format(it) }
                }
            } catch (e: Exception) {
                // If file access or system permissions block input stream, fall back to metadata hash
            }
        }
        
        // Metadata Hash fallback: Used if the file cannot be accessed directly (e.g. permission change)
        return try {
            val digest = MessageDigest.getInstance("MD5")
            // Base hash on size and fileName metadata
            val rawStr = "${file.fileName}:${file.fileSize}:${file.fileType}"
            val md5Bytes = digest.digest(rawStr.toByteArray(Charsets.UTF_8))
            md5Bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "err_hash_${file.id}_${file.timestamp}"
        }
    }
}
