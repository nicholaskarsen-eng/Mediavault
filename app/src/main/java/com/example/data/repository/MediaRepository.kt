package com.example.data.repository

import com.example.data.database.MediaFile
import com.example.data.database.MediaFileDao
import kotlinx.coroutines.flow.Flow

class MediaRepository(private val mediaFileDao: MediaFileDao) {
    val allMediaFiles: Flow<List<MediaFile>> = mediaFileDao.getAllMediaFiles()

    suspend fun insert(mediaFile: MediaFile): Long {
        return mediaFileDao.insertMediaFile(mediaFile)
    }

    suspend fun update(mediaFile: MediaFile) {
        mediaFileDao.updateMediaFile(mediaFile)
    }

    suspend fun deleteById(id: Int) {
        mediaFileDao.deleteMediaFileById(id)
    }

    suspend fun getPendingSyncFiles(): List<MediaFile> {
        return mediaFileDao.getPendingSyncFiles()
    }

    suspend fun clearAll() {
        mediaFileDao.clearAll()
    }
}
