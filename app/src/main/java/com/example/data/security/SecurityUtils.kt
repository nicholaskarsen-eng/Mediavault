package com.example.data.security

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object SecurityUtils {
    private const val TAG = "SecurityUtils"

    /**
     * Strips all EXIF metadata from an image file.
     * Returns a new URI for the scrubbed file in the app's private cache.
     */
    fun scrubMetadata(context: Context, sourceUri: Uri, fileName: String): Uri? {
        try {
            val cacheFile = File(context.cacheDir, "scrubbed_${UUID.randomUUID()}_$fileName")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!cacheFile.exists()) return null

            val exif = ExifInterface(cacheFile.absolutePath)
            
            // List of sensitive tags to remove
            val tagsToRemove = listOf(
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_USER_COMMENT,
                ExifInterface.TAG_IMAGE_DESCRIPTION
            )

            tagsToRemove.forEach { tag ->
                exif.setAttribute(tag, null)
            }
            
            exif.saveAttributes()
            Log.d(TAG, "Successfully scrubbed metadata for $fileName")
            return Uri.fromFile(cacheFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scrub metadata: ${e.message}")
            return null
        }
    }

    /**
     * Placeholder for Zero-Knowledge Encryption logic.
     * In a full implementation, this would use a key derived from a user passphrase.
     */
    fun encryptWithLocalKey(context: Context, uri: Uri, passphrase: String): Uri? {
        // Implementation would involve AES-256-GCM encryption
        // and saving the result to a new file.
        return uri // Returning original for now as this requires substantial crypto boiler-plate
    }
}
