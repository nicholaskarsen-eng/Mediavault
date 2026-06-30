package com.example.data.api

import android.content.Context
import android.net.Uri
import com.example.data.database.CloudAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.util.concurrent.TimeUnit

object CloudStorageClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun uploadFile(
        context: Context,
        localUri: String,
        account: CloudAccount,
        fileName: String
    ): String = withContext(Dispatchers.IO) {
        if (account.provider.equals("SMB Share", ignoreCase = true)) {
            return@withContext uploadToSmb(context, localUri, account, fileName)
        }
        if (account.provider.equals("External SD", ignoreCase = true)) {
            return@withContext copyToLocalPath(context, localUri, account, fileName)
        }

        val uri = Uri.parse(localUri)
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            throw IOException("File Access Denied: The system could not open the local file \"$fileName\". It may have been moved, deleted, or permissions were revoked.")
        } ?: throw IOException("File Not Found: Local resource \"$fileName\" is no longer available.")
        
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val requestBody = object : RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()
            override fun contentLength() = try { inputStream.available().toLong() } catch (e: Exception) { 0L }
            override fun writeTo(sink: BufferedSink) {
                inputStream.use { input ->
                    input.source().use { source ->
                        sink.writeAll(source)
                    }
                }
            }
        }

        val targetUrl = when (account.provider.lowercase()) {
            "aws s3", "digitalocean", "backblaze b2" -> {
                val host = account.endpoint?.removePrefix("https://")?.removePrefix("http://")
                "https://${account.bucketName}.$host/$fileName"
            }
            "azure blob" -> {
                "${account.endpoint}/$fileName"
            }
            else -> {
                if (account.endpoint?.endsWith("/") == true) "${account.endpoint}$fileName"
                else "${account.endpoint}/$fileName"
            }
        }

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .put(requestBody)

        if (!account.apiKey.isNullOrBlank()) {
            when (account.provider.lowercase()) {
                "aws s3", "digitalocean" -> {
                    requestBuilder.addHeader("Authorization", "Bearer ${account.apiKey}")
                }
                "azure blob" -> {
                    requestBuilder.addHeader("x-ms-blob-type", "BlockBlob")
                    requestBuilder.addHeader("Authorization", "SharedKey ${account.accountName}:${account.apiKey}")
                }
                else -> {
                    requestBuilder.addHeader("Authorization", "Bearer ${account.apiKey}")
                }
            }
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Upload failed for $fileName to ${account.accountName}: ${response.code} ${response.message}")
            }
            targetUrl
        }
    }

    private fun uploadToSmb(context: Context, localUri: String, account: CloudAccount, fileName: String): String {
        val user = account.apiKey ?: ""
        val pass = account.secretKey ?: ""
        val host = account.endpoint ?: throw IOException("SMB Host missing")
        val share = account.bucketName ?: "shared"
        
        val smbUrl = "smb://$host/$share/$fileName"
        
        val props = java.util.Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.connTimeout", "10000")
            setProperty("jcifs.smb.client.soTimeout", "35000")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
        }
        
        val config = jcifs.config.PropertyConfiguration(props)
        val baseContext = jcifs.context.BaseContext(config)
        
        val auth = if (user.isNotEmpty()) {
            baseContext.withCredentials(jcifs.smb.NtlmPasswordAuthenticator(null, user, pass))
        } else {
            baseContext.withAnonymousCredentials()
        }

        try {
            val smbFile = jcifs.smb.SmbFile(smbUrl, auth)
            context.contentResolver.openInputStream(Uri.parse(localUri))?.use { input ->
                smbFile.openOutputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return smbUrl
        } catch (e: Exception) {
            throw IOException("SMB Upload failed: ${e.message}")
        }
    }

    private fun copyToLocalPath(context: Context, localUri: String, account: CloudAccount, fileName: String): String {
        val targetPath = account.endpoint ?: throw IOException("Local path missing")
        val destDir = java.io.File(targetPath)
        if (!destDir.exists()) destDir.mkdirs()
        
        val destFile = java.io.File(destDir, fileName)
        context.contentResolver.openInputStream(Uri.parse(localUri))?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile.absolutePath
    }

    suspend fun fetchManifest(account: CloudAccount): List<String> = withContext(Dispatchers.IO) {
        if (account.endpoint.isNullOrBlank()) return@withContext emptyList()
        if (account.provider.equals("SMB Share", ignoreCase = true) || account.provider.equals("External SD", ignoreCase = true)) {
            return@withContext emptyList()
        }

        val manifestUrl = if (account.endpoint.endsWith("/")) "${account.endpoint}manifest.json"
        else "${account.endpoint}/manifest.json"

        val request = Request.Builder()
            .url(manifestUrl)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val jsonArray = org.json.JSONArray(body)
                    List(jsonArray.length()) { jsonArray.getString(it) }
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun testConnectivity(account: CloudAccount): Boolean = withContext(Dispatchers.IO) {
        try {
            when (account.provider.lowercase()) {
                "smb share" -> {
                    val user = account.apiKey ?: ""
                    val pass = account.secretKey ?: ""
                    val domain = if (account.region.isBlank()) null else account.region
                    val host = account.endpoint ?: return@withContext false
                    val share = account.bucketName
                    
                    val props = java.util.Properties().apply {
                        setProperty("jcifs.smb.client.minVersion", "SMB202")
                        setProperty("jcifs.smb.client.maxVersion", "SMB311")
                        setProperty("jcifs.smb.client.connTimeout", "10000")
                        setProperty("jcifs.smb.client.soTimeout", "15000")
                    }
                    val config = jcifs.config.PropertyConfiguration(props)
                    val baseContext = jcifs.context.BaseContext(config)
                    val auth = if (user.isNotEmpty()) baseContext.withCredentials(jcifs.smb.NtlmPasswordAuthenticator(domain, user, pass)) else baseContext.withAnonymousCredentials()
                    
                    // If share is provided, test specific share access. Otherwise test host connectivity.
                    val cleanHost = host.trim().removePrefix("smb://").removeSuffix("/")
                    val cleanShare = share?.trim()?.trim('/') ?: ""
                    val smbUrl = if (cleanShare.isEmpty()) "smb://$cleanHost/" else "smb://$cleanHost/$cleanShare/"
                    val smbFile = jcifs.smb.SmbFile(smbUrl, auth)
                    
                    // We check if the resource is reachable.
                    // exists() or canRead() trigger the actual connection handshake.
                    smbFile.exists() || smbFile.canRead() || smbFile.type != 0
                }
                "external sd" -> {
                    val path = account.endpoint ?: return@withContext false
                    val file = java.io.File(path)
                    val exists = file.exists()
                    val canRead = file.canRead()
                    if (!exists || !canRead) {
                        android.util.Log.w("CloudStorageClient", "Test failed for External SD: exists=$exists, canRead=$canRead for path=$path")
                    }
                    exists && canRead
                }
                "google drive", "onedrive" -> {
                    // For these providers, we check if sufficient metadata is provided.
                    // Real verification happens during OAuth which is a separate flow.
                    val hasKey = !account.apiKey.isNullOrBlank()
                    val hasEndpoint = !account.endpoint.isNullOrBlank()
                    if (!hasKey && !hasEndpoint) {
                        android.util.Log.w("CloudStorageClient", "Test failed for ${account.provider}: Both API Key and Endpoint are blank")
                    }
                    hasKey || hasEndpoint
                }
                else -> {
                    if (account.endpoint.isNullOrBlank()) {
                        android.util.Log.w("CloudStorageClient", "Test failed: Endpoint is blank")
                        return@withContext false
                    }
                    // HEAD request to check if server is reachable and responding
                    val targetUrl = if (account.endpoint!!.endsWith("/")) account.endpoint else "${account.endpoint}/"
                    val request = Request.Builder().url(targetUrl).head().build()
                    client.newCall(request).execute().use { response ->
                        val success = response.isSuccessful || response.code == 404 || response.code == 401 || response.code == 403
                        if (!success) {
                            android.util.Log.w("CloudStorageClient", "Test failed: HTTP ${response.code} for $targetUrl")
                        }
                        success
                        // 401/403 means host reached but auth needed (which is fine for a connection test)
                    } 
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CloudStorageClient", "Connectivity test failed for ${account.provider}", e)
            throw e
        }
    }

    suspend fun downloadFile(
        context: Context,
        url: String,
        targetFileName: String
    ): Uri = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to download $url")

            val bytes = response.body?.bytes() ?: throw IOException("Empty response body")
            val directory = java.io.File(context.filesDir, "vault_downloads").apply { if (!exists()) mkdirs() }
            val file = java.io.File(directory, targetFileName)
            file.writeBytes(bytes)
            Uri.fromFile(file)
        }
    }
}
