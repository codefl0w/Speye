package com.fl0w.speye.utils

import android.content.Context
import android.net.Uri
import com.fl0w.speye.data.db.AppDatabase
import com.fl0w.speye.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    private const val TAG = "BackupManager"
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true 
        encodeDefaults = true
    }

    suspend fun exportData(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                SpeyeLogger.d(TAG, "Starting export to $uri")
                val outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Could not open output stream for URI: $uri")
                performExportToStream(context, outputStream)
                SpeyeLogger.d(TAG, "Export successful to $uri")
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "Export failed", e)
                throw e
            }
        }
    }

    suspend fun importFromFile(context: Context, file: File) {
        importData(context, Uri.fromFile(file))
    }

    suspend fun importData(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                SpeyeLogger.d(TAG, "Starting import from $uri")
                val db = AppDatabase.getDatabase(context)
                val tempDir = File(context.cacheDir, "import_temp")
                if (tempDir.exists()) tempDir.deleteRecursively()
                tempDir.mkdirs()

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Could not open input stream for URI: $uri")

                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    val canonicalTempDirPath = tempDir.canonicalPath
                    while (entry != null) {
                        val file = File(tempDir, entry.name)
                        if (!file.canonicalPath.startsWith(canonicalTempDirPath)) {
                            throw SecurityException("Zip entry outside target directory: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            file.outputStream().use { zis.copyTo(it) }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                val dataFile = File(tempDir, "data.json")
                if (!dataFile.exists()) throw Exception("Invalid .spy file: data.json missing")

                val jsonData = dataFile.readText()
                val backupData = json.decodeFromString<BackupData>(jsonData)

                SpeyeLogger.d(TAG, "Importing ${backupData.notifications.size} notifications and ${backupData.ignoredApps.size} ignored apps")

                for (pkg in backupData.ignoredApps) {
                    db.ignoredAppDao().ignoreApp(IgnoredAppEntity(pkg))
                }

                var importedCount = 0
                for (sNotif in backupData.notifications) {
                    val sk = sNotif.sbnKey
                    val existing = if (sk != null) db.notificationDao().getNotificationBySbnKey(sk) else null
                    
                    var finalImagePath: String? = null
                    val sPath = sNotif.imagePath
                    if (!sPath.isNullOrBlank()) {
                        val fileName = File(sPath).name
                        val srcFile = File(tempDir, "attachments/$fileName")
                        if (srcFile.exists() && srcFile.isFile) {
                            val destFile = File(context.filesDir, fileName)
                            srcFile.copyTo(destFile, overwrite = true)
                            finalImagePath = destFile.absolutePath
                        }
                    }

                    if (existing == null) {
                        val nId = db.notificationDao().insert(
                            NotificationEntity(
                                sbnKey = sNotif.sbnKey,
                                packageName = sNotif.packageName,
                                title = sNotif.title,
                                text = sNotif.text,
                                timestamp = sNotif.timestamp,
                                appName = sNotif.appName,
                                isSystemRemoved = sNotif.isSystemRemoved,
                                imagePath = finalImagePath,
                                contentIntentUri = sNotif.contentIntentUri
                            )
                        )
                        for (h in sNotif.history) {
                            db.notificationDao().insertHistory(
                                NotificationHistoryEntity(
                                    notificationId = nId,
                                    oldText = h.oldText,
                                    timestamp = h.timestamp
                                )
                            )
                        }
                        importedCount++
                    }
                }
                SpeyeLogger.d(TAG, "Import successful: $importedCount new notifications added")
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "Import failed", e)
                throw e
            } finally {
                val tempDir = File(context.cacheDir, "import_temp")
                if (tempDir.exists()) tempDir.deleteRecursively()
            }
        }
    }

    suspend fun exportToFile(context: Context, file: File) {
        withContext(Dispatchers.IO) {
            try {
                FileOutputStream(file).use { fos ->
                    performExportToStream(context, fos)
                }
                SpeyeLogger.d(TAG, "exportToFile: Success. File size: ${file.length()} bytes")
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "exportToFile: Failed", e)
                throw e
            }
        }
    }

    private suspend fun performExportToStream(context: Context, outputStream: OutputStream) {
        val db = AppDatabase.getDatabase(context)
        val notifications = db.notificationDao().getAllNotificationsWithHistoryList()
        val ignoredApps = db.ignoredAppDao().getAllIgnoredApps().first().map { it.packageName }

        SpeyeLogger.d(TAG, "Exporting ${notifications.size} notifications and ${ignoredApps.size} ignored apps")

        val serializableNotifications = notifications.map { item ->
            SerializableNotification(
                sbnKey = item.notification.sbnKey,
                packageName = item.notification.packageName,
                title = item.notification.title,
                text = item.notification.text,
                timestamp = item.notification.timestamp,
                appName = item.notification.appName,
                isSystemRemoved = item.notification.isSystemRemoved,
                imagePath = item.notification.imagePath?.let { File(it).name },
                history = item.history.map { SerializableHistory(it.oldText, it.timestamp) },
                contentIntentUri = item.notification.contentIntentUri
            )
        }

        val backupData = BackupData(serializableNotifications, ignoredApps)
        val jsonData = json.encodeToString(backupData)

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(jsonData.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            var attachmentsCount = 0
            val packedImages = HashSet<String>()
            for (item in notifications) {
                val path = item.notification.imagePath
                if (!path.isNullOrBlank()) {
                    val file = File(path)
                    val fileName = file.name
                    if (file.exists() && file.isFile && file.length() > 0 && packedImages.add(fileName)) {
                        try {
                            file.inputStream().use { input ->
                                zos.putNextEntry(ZipEntry("attachments/$fileName"))
                                input.copyTo(zos)
                                zos.closeEntry()
                                attachmentsCount++
                            }
                        } catch (e: Exception) {
                            SpeyeLogger.e(TAG, "Failed to pack attachment $fileName", e)
                        }
                    }
                }
            }
            SpeyeLogger.d(TAG, "Packed $attachmentsCount attachments")
            zos.finish()
            zos.flush()
        }
    }
}
