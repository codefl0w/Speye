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
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptedBackupException(
    message: String,
    val needsPassword: Boolean = false,
    val isInvalidPassword: Boolean = false
) : Exception(message)

object BackupManager {
    private const val TAG = "BackupManager"
    private val MAGIC_HEADER = "SPYE_ENC1".toByteArray(Charsets.UTF_8)
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val PBKDF2_ITERATIONS = 65536
    private const val KEY_LENGTH_BITS = 256

    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true 
        encodeDefaults = true
    }

    suspend fun exportData(context: Context, uri: Uri, password: String? = null) {
        withContext(Dispatchers.IO) {
            try {
                SpeyeLogger.d(TAG, "Starting export to $uri (password protected: ${!password.isNullOrBlank()})")
                val outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Could not open output stream for URI: $uri")
                if (password.isNullOrBlank()) {
                    performExportToStream(context, outputStream)
                } else {
                    outputStream.use { os ->
                        createEncryptingStream(os, password).use { cos ->
                            performExportToStream(context, cos)
                        }
                    }
                }
                SpeyeLogger.d(TAG, "Export successful to $uri")
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "Export failed", e)
                throw e
            }
        }
    }

    suspend fun importFromFile(context: Context, file: File, password: String? = null) {
        importData(context, Uri.fromFile(file), password)
    }

    suspend fun importData(context: Context, uri: Uri, password: String? = null) {
        withContext(Dispatchers.IO) {
            try {
                SpeyeLogger.d(TAG, "Starting import from $uri")
                val db = AppDatabase.getDatabase(context)
                val tempDir = File(context.cacheDir, "import_temp")
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Could not open input stream for URI: $uri")

                val backupData = extractBackupZip(inputStream, tempDir, password)

                SpeyeLogger.d(TAG, "Importing ${backupData.notifications.size} notifications and ${backupData.ignoredApps.size} ignored apps")

                for (pkg in backupData.ignoredApps) {
                    db.ignoredAppDao().ignoreApp(IgnoredAppEntity(pkg))
                }

                var importedCount = 0
                for (sNotif in backupData.notifications) {
                    val sk = sNotif.sbnKey
                    val existing = (sk?.let { db.notificationDao().getNotificationBySbnKeyAndTimestamp(it, sNotif.timestamp) })
                        ?: db.notificationDao().findDuplicate(
                            packageName = sNotif.packageName,
                            timestamp = sNotif.timestamp,
                            title = sNotif.title,
                            text = sNotif.text
                        )
                    
                    var finalImagePath: String? = null
                    val sPath = sNotif.imagePath
                    if (!sPath.isNullOrBlank()) {
                        val fileName = File(sPath).name
                        val srcFile = File(tempDir, "attachments/$fileName")
                        val destFile = File(context.filesDir, fileName)
                        if (srcFile.exists() && srcFile.isFile) {
                            srcFile.copyTo(destFile, overwrite = true)
                            finalImagePath = destFile.absolutePath
                        } else {
                            finalImagePath = destFile.absolutePath
                        }
                    }

                    var finalAudioPath: String? = null
                    val sAudio = sNotif.audioPath
                    if (!sAudio.isNullOrBlank()) {
                        val fileName = File(sAudio).name
                        val srcFile = File(tempDir, "attachments/$fileName")
                        val voiceDir = File(context.filesDir, "voice")
                        if (!voiceDir.exists()) voiceDir.mkdirs()
                        val destFile = File(voiceDir, fileName)
                        if (srcFile.exists() && srcFile.isFile) {
                            srcFile.copyTo(destFile, overwrite = true)
                            finalAudioPath = destFile.absolutePath
                        } else {
                            finalAudioPath = destFile.absolutePath
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
                                contentIntentUri = sNotif.contentIntentUri,
                                progress = sNotif.progress,
                                progressMax = sNotif.progressMax,
                                isIndeterminate = sNotif.isIndeterminate,
                                isMedia = sNotif.isMedia,
                                mediaTitle = sNotif.mediaTitle,
                                mediaArtist = sNotif.mediaArtist,
                                mediaAlbum = sNotif.mediaAlbum,
                                mediaDurationMs = sNotif.mediaDurationMs,
                                mediaPositionMs = sNotif.mediaPositionMs,
                                mediaPlaybackState = sNotif.mediaPlaybackState,
                                audioPath = finalAudioPath,
                                isVoiceMessage = sNotif.isVoiceMessage,
                                voiceSender = sNotif.voiceSender
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
                    } else {
                        val updated = existing.copy(
                            imagePath = finalImagePath ?: existing.imagePath,
                            audioPath = finalAudioPath ?: existing.audioPath,
                            isMedia = if (sNotif.isMedia) true else existing.isMedia,
                            mediaTitle = sNotif.mediaTitle ?: existing.mediaTitle,
                            mediaArtist = sNotif.mediaArtist ?: existing.mediaArtist,
                            mediaAlbum = sNotif.mediaAlbum ?: existing.mediaAlbum,
                            mediaDurationMs = sNotif.mediaDurationMs ?: existing.mediaDurationMs,
                            mediaPositionMs = sNotif.mediaPositionMs ?: existing.mediaPositionMs,
                            mediaPlaybackState = sNotif.mediaPlaybackState ?: existing.mediaPlaybackState,
                            isVoiceMessage = if (sNotif.isVoiceMessage) true else existing.isVoiceMessage,
                            voiceSender = sNotif.voiceSender ?: existing.voiceSender
                        )
                        if (updated != existing) {
                            db.notificationDao().update(updated)
                        }
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

    suspend fun exportToFile(context: Context, file: File, password: String? = null) {
        withContext(Dispatchers.IO) {
            try {
                if (password.isNullOrBlank()) {
                    FileOutputStream(file).use { fos ->
                        performExportToStream(context, fos)
                    }
                } else {
                    FileOutputStream(file).use { fos ->
                        createEncryptingStream(fos, password).use { cos ->
                            performExportToStream(context, cos)
                        }
                    }
                }
                SpeyeLogger.d(TAG, "exportToFile: Success. File size: ${file.length()} bytes")
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "exportToFile: Failed", e)
                throw e
            }
        }
    }

    fun deriveKey(password: String, salt: ByteArray): ByteArray {
        return try {
            val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            secretKeyFactory.generateSecret(keySpec).encoded
        } catch (e: NoSuchAlgorithmException) {
            deriveKeyFallback(password, salt)
        }
    }

    internal fun deriveKeyFallback(password: String, salt: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256"))

        val block1 = ByteArray(salt.size + 4)
        System.arraycopy(salt, 0, block1, 0, salt.size)
        block1[salt.size + 3] = 1

        var u = mac.doFinal(block1)
        val result = u.clone()

        for (iter in 1 until PBKDF2_ITERATIONS) {
            u = mac.doFinal(u)
            for (j in result.indices) {
                result[j] = (result[j].toInt() xor u[j].toInt()).toByte()
            }
        }
        return result
    }

    fun createEncryptingStream(destination: OutputStream, password: String): OutputStream {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).apply { random.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH).apply { random.nextBytes(this) }

        val secretKeyBytes = deriveKey(password, salt)
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

        destination.write(MAGIC_HEADER)
        destination.write(salt)
        destination.write(iv)

        return CipherOutputStream(destination, cipher)
    }

    fun encryptZipStream(rawStreamWriter: (OutputStream) -> Unit, destination: OutputStream, password: String) {
        val cos = createEncryptingStream(destination, password)
        rawStreamWriter(cos)
        cos.close()
    }

    fun encryptZipBytes(rawBytes: ByteArray, password: String): ByteArray {
        val out = ByteArrayOutputStream()
        encryptZipStream({ it.write(rawBytes) }, out, password)
        return out.toByteArray()
    }

    fun decryptZipBytes(encryptedBytes: ByteArray, password: String?): ByteArray {
        val headerLen = MAGIC_HEADER.size
        if (encryptedBytes.size < headerLen + SALT_LENGTH + IV_LENGTH + 16) {
            throw EncryptedBackupException("Invalid encrypted backup: file too short")
        }
        if (password.isNullOrEmpty()) {
            throw EncryptedBackupException("Password required for encrypted backup", needsPassword = true)
        }
        val salt = encryptedBytes.copyOfRange(headerLen, headerLen + SALT_LENGTH)
        val iv = encryptedBytes.copyOfRange(headerLen + SALT_LENGTH, headerLen + SALT_LENGTH + IV_LENGTH)
        val ciphertext = encryptedBytes.copyOfRange(headerLen + SALT_LENGTH + IV_LENGTH, encryptedBytes.size)

        val secretKeyBytes = deriveKey(password, salt)
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw EncryptedBackupException("Invalid password or corrupted backup file", isInvalidPassword = true)
        }
    }

    fun writeBackupZip(
        backupData: BackupData,
        attachmentFiles: List<File>,
        outputStream: OutputStream
    ) {
        val jsonData = json.encodeToString(backupData)

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(jsonData.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            var attachmentsCount = 0
            val packedImages = HashSet<String>()
            for (file in attachmentFiles) {
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
            SpeyeLogger.d(TAG, "Packed $attachmentsCount attachments")
            zos.finish()
            zos.flush()
        }
    }

    fun extractBackupZip(
        inputStream: InputStream,
        targetDir: File,
        password: String? = null
    ): BackupData {
        val pushback = PushbackInputStream(inputStream, MAGIC_HEADER.size)
        val header = ByteArray(MAGIC_HEADER.size)
        var totalHeaderRead = 0
        while (totalHeaderRead < MAGIC_HEADER.size) {
            val r = pushback.read(header, totalHeaderRead, MAGIC_HEADER.size - totalHeaderRead)
            if (r == -1) break
            totalHeaderRead += r
        }
        val isEncrypted = (totalHeaderRead == MAGIC_HEADER.size && header.contentEquals(MAGIC_HEADER))
        if (!isEncrypted && totalHeaderRead > 0) {
            pushback.unread(header, 0, totalHeaderRead)
        }

        var tempDecryptedFile: File? = null

        val effectiveStream: InputStream = if (isEncrypted) {
            if (password.isNullOrEmpty()) {
                throw EncryptedBackupException("Password required for encrypted backup", needsPassword = true)
            }
            val salt = ByteArray(SALT_LENGTH)
            var saltRead = 0
            while (saltRead < SALT_LENGTH) {
                val r = pushback.read(salt, saltRead, SALT_LENGTH - saltRead)
                if (r == -1) break
                saltRead += r
            }
            if (saltRead != SALT_LENGTH) throw EncryptedBackupException("Invalid encrypted backup: missing salt")

            val iv = ByteArray(IV_LENGTH)
            var ivRead = 0
            while (ivRead < IV_LENGTH) {
                val r = pushback.read(iv, ivRead, IV_LENGTH - ivRead)
                if (r == -1) break
                ivRead += r
            }
            if (ivRead != IV_LENGTH) throw EncryptedBackupException("Invalid encrypted backup: missing iv")

            val secretKeyBytes = deriveKey(password, salt)
            val secretKey = SecretKeySpec(secretKeyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

            val tempFile = File.createTempFile("speye_decrypted_", ".zip", targetDir.parentFile ?: targetDir)
            tempDecryptedFile = tempFile

            try {
                FileOutputStream(tempFile).use { fos ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (pushback.read(buffer).also { bytesRead = it } != -1) {
                        val outputChunk = cipher.update(buffer, 0, bytesRead)
                        if (outputChunk != null && outputChunk.isNotEmpty()) {
                            fos.write(outputChunk)
                        }
                    }
                    val finalChunk = cipher.doFinal()
                    if (finalChunk != null && finalChunk.isNotEmpty()) {
                        fos.write(finalChunk)
                    }
                }
                FileInputStream(tempFile)
            } catch (e: Exception) {
                tempFile.delete()
                throw EncryptedBackupException("Invalid password or corrupted backup file", isInvalidPassword = true)
            }
        } else {
            pushback
        }

        try {
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            ZipInputStream(effectiveStream).use { zis ->
                var entry = zis.nextEntry
                val canonicalTargetDirPath = targetDir.canonicalPath
                while (entry != null) {
                    val file = File(targetDir, entry.name)
                    if (!file.canonicalPath.startsWith(canonicalTargetDirPath)) {
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

            val dataFile = File(targetDir, "data.json")
            if (!dataFile.exists()) throw IOException("Invalid .spy file: data.json missing")

            val jsonData = dataFile.readText()
            return json.decodeFromString<BackupData>(jsonData)
        } finally {
            try { effectiveStream.close() } catch (_: Exception) {}
            tempDecryptedFile?.delete()
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
                contentIntentUri = item.notification.contentIntentUri,
                progress = item.notification.progress,
                progressMax = item.notification.progressMax,
                isIndeterminate = item.notification.isIndeterminate,
                isMedia = item.notification.isMedia,
                mediaTitle = item.notification.mediaTitle,
                mediaArtist = item.notification.mediaArtist,
                mediaAlbum = item.notification.mediaAlbum,
                mediaDurationMs = item.notification.mediaDurationMs,
                mediaPositionMs = item.notification.mediaPositionMs,
                mediaPlaybackState = item.notification.mediaPlaybackState,
                audioPath = item.notification.audioPath?.let { File(it).name },
                isVoiceMessage = item.notification.isVoiceMessage,
                voiceSender = item.notification.voiceSender
            )
        }

        val backupData = BackupData(serializableNotifications, ignoredApps)
        val attachmentFiles = mutableListOf<File>()
        val seenFileNames = HashSet<String>()
        for (item in notifications) {
            item.notification.imagePath?.let { path ->
                val f = File(path)
                if (f.exists() && f.isFile && f.length() > 0 && seenFileNames.add(f.name)) {
                    attachmentFiles.add(f)
                }
            }
            item.notification.audioPath?.let { path ->
                val f = File(path)
                if (f.exists() && f.isFile && f.length() > 0 && seenFileNames.add(f.name)) {
                    attachmentFiles.add(f)
                }
            }
        }
        writeBackupZip(backupData, attachmentFiles, outputStream)
    }
}
