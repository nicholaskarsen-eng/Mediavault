package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaFileDao {
    @Query("SELECT * FROM media_files ORDER BY timestamp DESC")
    fun getAllMediaFiles(): Flow<List<MediaFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaFile(mediaFile: MediaFile): Long

    @Update
    suspend fun updateMediaFile(mediaFile: MediaFile)

    @Query("DELETE FROM media_files WHERE id = :id")
    suspend fun deleteMediaFileById(id: Int)

    @Query("SELECT * FROM media_files WHERE id = :id")
    suspend fun getMediaFileById(id: Int): MediaFile?

    @Query("SELECT * FROM media_files WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncFiles(): List<MediaFile>

    @Query("DELETE FROM media_files")
    suspend fun clearAll()
}
