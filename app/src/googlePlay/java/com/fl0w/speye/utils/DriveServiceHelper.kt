package com.fl0w.speye.utils

import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException

class DriveServiceHelper(private val driveService: Drive) {

    suspend fun uploadBackup(localFile: java.io.File): String? = withContext(Dispatchers.IO) {
        try {
            val metadata = File()
                .setName(localFile.name)
                .setParents(listOf("appDataFolder"))

            val content = FileContent("application/octet-stream", localFile)

            // Check for existing file to update
            val existing = findBackupFile(localFile.name)
            if (existing != null) {
                // The parents field is not directly writable in update requests.
                // We just update the content.
                driveService.files().update(existing.id, null, content).execute().id
            } else {
                driveService.files().create(metadata, content).execute().id
            }
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            val errorMsg = when (e.statusCode) {
                403 -> "ERROR: Google Drive API Access Denied (Check if enabled in Console)"
                404 -> "ERROR: File not found on Drive"
                else -> "ERROR: Drive API ${e.statusCode}: ${e.message}"
            }
            SpeyeLogger.e("DriveServiceHelper", errorMsg, e)
            null
        } catch (e: Exception) {
            SpeyeLogger.e("DriveServiceHelper", "Upload failed with unexpected error", e)
            null
        }
    }

    suspend fun downloadLatestBackup(destFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        try {
            val result: FileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name, size, modifiedTime)")
                .setOrderBy("modifiedTime desc")
                .setPageSize(1)
                .execute()

            val latest = result.files?.firstOrNull() ?: run {
                SpeyeLogger.e("DriveServiceHelper", "No files found in appDataFolder")
                return@withContext false
            }

            SpeyeLogger.d("DriveServiceHelper", "Downloading latest: ${latest.name} (${latest.getSize() ?: 0} bytes), ID: ${latest.id}")

            destFile.outputStream().use { outputStream ->
                driveService.files().get(latest.id).executeMediaAndDownloadTo(outputStream)
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            SpeyeLogger.e("DriveServiceHelper", "Download failed", e)
            false
        }
    }

    private fun findBackupFile(name: String): File? {
        try {
            val safeName = name.replace("'", "\\'")
            val result: FileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$safeName' and trashed = false")
                .setFields("files(id, name, modifiedTime)")
                .setOrderBy("modifiedTime desc")
                .execute()
            
            val match = result.files?.firstOrNull()
            if (match != null) {
                SpeyeLogger.d("DriveServiceHelper", "Found existing file to update: ${match.id}")
            }
            return match
        } catch (e: Exception) {
            SpeyeLogger.e("DriveServiceHelper", "Error searching for backup file: $name", e)
            return null
        }
    }

    suspend fun deleteAllCloudData(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result: FileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id)")
                .execute()

            result.files?.forEach { file ->
                driveService.files().delete(file.id).execute()
            }
            true
        } catch (e: Exception) {
            SpeyeLogger.e("DriveServiceHelper", "Cloud wipe failed", e)
            false
        }
    }

    suspend fun getStorageInfo(): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        try {
            val about = driveService.about().get().setFields("storageQuota").execute()
            val quota = about.storageQuota
            Pair(quota.limit ?: 0L, quota.usage ?: 0L)
        } catch (e: Exception) {
            SpeyeLogger.e("DriveServiceHelper", "Failed to fetch storage info", e)
            null
        }
    }
}
