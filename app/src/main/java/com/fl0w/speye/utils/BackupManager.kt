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
    private val MAGIC_HEADER_V1 = "SPYE_ENC1".toByteArray(Charsets.UTF_8)
    private val MAGIC_HEADER_V2 = "SPYE_ENC2".toByteArray(Charsets.UTF_8)
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = 16
    private const val STREAM_CHUNK_SIZE = 64 * 1024
    private const val PBKDF2_ITERATIONS = 65536
    private const val KEY_LENGTH_BITS = 256

    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true 
        encodeDefaults = true
    }

    suspend fun exportData(context: Context, uri: Uri, password: String? = null) {
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            try {
                SpeyeLogger.d(TAG, "Starting export to $uri (password protected: ${!password.isNullOrBlank()})")
                val outputStream = try {
                    context.contentResolver.openOutputStream(uri, "wt")
                } catch (_: Exception) {
                    context.contentResolver.openOutputStream(uri)
                } ?: throw IOException("Could not open output stream for URI: $uri")

                outputStream.use { os ->
                    if (password.isNullOrBlank()) {
                        performExportToStream(context, os)
                    } else {
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
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            try {
                SpeyeLogger.d(TAG, "Starting import from $uri")
                val db = AppDatabase.getDatabase(context)
                val tempDir = File(context.cacheDir, "import_temp")
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Could not open input stream for URI: $uri")

                val backupData = inputStream.use { extractBackupZip(it, tempDir, password) }

                SpeyeLogger.d(TAG, "Importing ${backupData.notifications.size} notifications, ${backupData.ignoredApps.size} ignored apps, ${backupData.appFilters.size} app filters")

                val handledFilterPackages = HashSet<String>()
                for (filter in backupData.appFilters) {
                    handledFilterPackages.add(filter.packageName)
                    val entity = IgnoredAppEntity(
                        packageName = filter.packageName,
                        allowText = filter.allowText,
                        allowLazyAssets = filter.allowLazyAssets,
                        allowStickers = filter.allowStickers,
                        allowMedia = filter.allowMedia,
                        allowVoice = filter.allowVoice,
                        allowProgress = filter.allowProgress,
                        allowActions = filter.allowActions
                    )
                    if (entity.isAllOn) {
                        db.ignoredAppDao().deleteByPackageName(filter.packageName)
                    } else {
                        db.ignoredAppDao().ignoreApp(entity)
                    }
                }

                for (pkg in backupData.ignoredApps) {
                    if (!handledFilterPackages.contains(pkg)) {
                        db.ignoredAppDao().ignoreApp(IgnoredAppEntity.allOff(pkg))
                    }
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
                                voiceSender = sNotif.voiceSender,
                                actionTitles = sNotif.actionTitles
                            )
                        )
                        for (h in sNotif.history) {
                            db.notificationDao().insertHistory(
                                NotificationHistoryEntity(
                                    notificationId = nId,
                                    oldText = h.oldText,
                                    timestamp = h.timestamp,
                                    oldActionTitles = h.oldActionTitles ?: sNotif.actionTitles
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
                            voiceSender = sNotif.voiceSender ?: existing.voiceSender,
                            actionTitles = sNotif.actionTitles ?: existing.actionTitles
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

    internal fun deriveChunkIv(baseIv: ByteArray, chunkIndex: Long): ByteArray {
        val iv = baseIv.clone()
        for (i in 0 until 8) {
            val shift = (7 - i) * 8
            val byteVal = ((chunkIndex ushr shift) and 0xFFL).toInt()
            val pos = iv.size - 8 + i
            iv[pos] = (iv[pos].toInt() xor byteVal).toByte()
        }
        return iv
    }

    internal fun buildChunkAad(chunkIndex: Long, isFinal: Boolean): ByteArray {
        val aad = ByteArray(9)
        for (i in 0 until 8) {
            val shift = (7 - i) * 8
            aad[i] = ((chunkIndex ushr shift) and 0xFFL).toByte()
        }
        aad[8] = if (isFinal) 1 else 0
        return aad
    }

    private class ChunkedAesGcmOutputStream(
        private val destination: OutputStream,
        private val secretKey: SecretKeySpec,
        private val baseIv: ByteArray,
        private val chunkSize: Int = STREAM_CHUNK_SIZE
    ) : OutputStream() {
        private val buffer = ByteArray(chunkSize)
        private var bufferedCount = 0
        private var chunkIndex = 0L
        private var closed = false

        override fun write(b: Int) {
            check(!closed) { "Stream is closed" }
            buffer[bufferedCount++] = b.toByte()
            if (bufferedCount == chunkSize) {
                flushChunk(isFinal = false)
            }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "Stream is closed" }
            var offset = off
            var remaining = len
            while (remaining > 0) {
                val space = chunkSize - bufferedCount
                val toCopy = minOf(space, remaining)
                System.arraycopy(b, offset, buffer, bufferedCount, toCopy)
                bufferedCount += toCopy
                offset += toCopy
                remaining -= toCopy
                if (bufferedCount == chunkSize) {
                    flushChunk(isFinal = false)
                }
            }
        }

        private fun flushChunk(isFinal: Boolean) {
            val chunkIv = deriveChunkIv(baseIv, chunkIndex)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
            cipher.updateAAD(buildChunkAad(chunkIndex, isFinal))
            val encrypted = cipher.doFinal(buffer, 0, bufferedCount)
            writeIntBe(destination, encrypted.size)
            destination.write(encrypted)
            bufferedCount = 0
            chunkIndex++
        }

        override fun flush() {
            if (!closed) {
                destination.flush()
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            try {
                if (bufferedCount > 0) {
                    flushChunk(isFinal = false)
                }
                flushChunk(isFinal = true)
                writeIntBe(destination, 0)
                destination.flush()
            } finally {
                destination.close()
            }
        }

        private fun writeIntBe(out: OutputStream, value: Int) {
            out.write((value ushr 24) and 0xFF)
            out.write((value ushr 16) and 0xFF)
            out.write((value ushr 8) and 0xFF)
            out.write(value and 0xFF)
        }
    }

    private class ChunkedAesGcmInputStream(
        private val source: InputStream,
        private val secretKey: SecretKeySpec,
        private val baseIv: ByteArray,
        private val maxChunkSize: Int = STREAM_CHUNK_SIZE
    ) : InputStream() {
        private var currentPlaintext = ByteArray(0)
        private var currentPos = 0
        private var currentLen = 0
        private var chunkIndex = 0L
        private var sawFinalChunk = false
        private var eof = false

        init {
            loadNextChunk()
        }

        private fun readFully(buf: ByteArray, len: Int): Int {
            var total = 0
            while (total < len) {
                val r = source.read(buf, total, len - total)
                if (r == -1) break
                total += r
            }
            return total
        }

        private fun readIntBe(): Int {
            val b = ByteArray(4)
            val read = readFully(b, 4)
            if (read != 4) {
                throw EncryptedBackupException("Invalid password or corrupted backup file", isInvalidPassword = true)
            }
            return ((b[0].toInt() and 0xFF) shl 24) or
                ((b[1].toInt() and 0xFF) shl 16) or
                ((b[2].toInt() and 0xFF) shl 8) or
                (b[3].toInt() and 0xFF)
        }

        private fun loadNextChunk() {
            if (sawFinalChunk) {
                eof = true
                return
            }
            val encryptedLen = readIntBe()
            if (encryptedLen < GCM_TAG_BYTES || encryptedLen > maxChunkSize + GCM_TAG_BYTES) {
                throw EncryptedBackupException("Invalid password or corrupted backup file", isInvalidPassword = true)
            }
            val encryptedBuf = ByteArray(encryptedLen)
            if (readFully(encryptedBuf, encryptedLen) != encryptedLen) {
                throw EncryptedBackupException("Invalid password or corrupted backup file", isInvalidPassword = true)
            }
            val isFinal = (encryptedLen == GCM_TAG_BYTES)
            val chunkIv = deriveChunkIv(baseIv, chunkIndex)
            val plain = try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
                cipher.updateAAD(buildChunkAad(chunkIndex, isFinal))
                cipher.doFinal(encryptedBuf)
            } catch (e: Exception) {
                throw EncryptedBackupException("Invalid password or corrupted backup file", isInvalidPassword = true)
            }
            chunkIndex++
            if (isFinal) {
                sawFinalChunk = true
                eof = true
                currentPlaintext = ByteArray(0)
                currentPos = 0
                currentLen = 0
            } else {
                currentPlaintext = plain
                currentPos = 0
                currentLen = plain.size
            }
        }

        fun ensureVerifiedToEnd() {
            while (!sawFinalChunk) {
                loadNextChunk()
            }
        }

        override fun read(): Int {
            if (currentPos >= currentLen) {
                if (eof) return -1
                loadNextChunk()
                if (eof) return -1
            }
            return currentPlaintext[currentPos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (currentPos >= currentLen) {
                if (eof) return -1
                loadNextChunk()
                if (eof) return -1
            }
            val available = currentLen - currentPos
            val toCopy = minOf(available, len)
            System.arraycopy(currentPlaintext, currentPos, b, off, toCopy)
            currentPos += toCopy
            return toCopy
        }

        override fun close() {
            source.close()
        }
    }

    fun createEncryptingStream(destination: OutputStream, password: String): OutputStream {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).apply { random.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH).apply { random.nextBytes(this) }

        val secretKeyBytes = deriveKey(password, salt)
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        destination.write(MAGIC_HEADER_V2)
        destination.write(salt)
        destination.write(iv)

        return ChunkedAesGcmOutputStream(destination, secretKey, iv)
    }

    fun encryptZipStream(rawStreamWriter: (OutputStream) -> Unit, destination: OutputStream, password: String) {
        createEncryptingStream(destination, password).use { cos ->
            rawStreamWriter(cos)
        }
    }

    fun encryptZipBytes(rawBytes: ByteArray, password: String): ByteArray {
        val out = ByteArrayOutputStream()
        encryptZipStream({ it.write(rawBytes) }, out, password)
        return out.toByteArray()
    }

    fun decryptZipBytes(encryptedBytes: ByteArray, password: String?): ByteArray {
        val headerLen = MAGIC_HEADER_V2.size
        if (encryptedBytes.size < headerLen + SALT_LENGTH + IV_LENGTH + GCM_TAG_BYTES) {
            throw EncryptedBackupException("Invalid encrypted backup: file too short")
        }
        if (password.isNullOrEmpty()) {
            throw EncryptedBackupException("Password required for encrypted backup", needsPassword = true)
        }
        val header = encryptedBytes.copyOfRange(0, headerLen)
        val isV2 = header.contentEquals(MAGIC_HEADER_V2)
        val isV1 = header.contentEquals(MAGIC_HEADER_V1)
        if (!isV2 && !isV1) {
            throw EncryptedBackupException("Invalid encrypted backup: unrecognized header")
        }

        val salt = encryptedBytes.copyOfRange(headerLen, headerLen + SALT_LENGTH)
        val iv = encryptedBytes.copyOfRange(headerLen + SALT_LENGTH, headerLen + SALT_LENGTH + IV_LENGTH)
        val secretKeyBytes = deriveKey(password, salt)
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        if (isV2) {
            val bodyStream = ByteArrayInputStream(
                encryptedBytes,
                headerLen + SALT_LENGTH + IV_LENGTH,
                encryptedBytes.size - (headerLen + SALT_LENGTH + IV_LENGTH)
            )
            return ChunkedAesGcmInputStream(bodyStream, secretKey, iv).use { cis ->
                val out = ByteArrayOutputStream()
                cis.copyTo(out)
                cis.ensureVerifiedToEnd()
                out.toByteArray()
            }
        } else {
            val ciphertext = encryptedBytes.copyOfRange(headerLen + SALT_LENGTH + IV_LENGTH, encryptedBytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            return try {
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.doFinal(ciphertext)
            } catch (e: Exception) {
                throw EncryptedBackupException("Invalid password or corrupted backup file", isInvalidPassword = true)
            }
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
        val headerSize = MAGIC_HEADER_V2.size
        val pushback = PushbackInputStream(inputStream, headerSize)
        val header = ByteArray(headerSize)
        var totalHeaderRead = 0
        while (totalHeaderRead < headerSize) {
            val r = pushback.read(header, totalHeaderRead, headerSize - totalHeaderRead)
            if (r == -1) break
            totalHeaderRead += r
        }
        val isEncryptedV2 = (totalHeaderRead == headerSize && header.contentEquals(MAGIC_HEADER_V2))
        val isEncryptedV1 = (totalHeaderRead == headerSize && header.contentEquals(MAGIC_HEADER_V1))
        val isEncrypted = isEncryptedV2 || isEncryptedV1
        if (!isEncrypted && totalHeaderRead > 0) {
            pushback.unread(header, 0, totalHeaderRead)
        }

        var tempDecryptedFile: File? = null
        var chunkedDecryptStream: ChunkedAesGcmInputStream? = null

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

            if (isEncryptedV2) {
                val cis = ChunkedAesGcmInputStream(pushback, secretKey, iv)
                chunkedDecryptStream = cis
                cis
            } else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))

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
                chunkedDecryptStream?.ensureVerifiedToEnd()
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
        val ignoredEntities = db.ignoredAppDao().getAllIgnoredApps().first()
        val ignoredApps = ignoredEntities.filter { it.isAllOff }.map { it.packageName }
        val appFilters = ignoredEntities.map {
            SerializableAppFilter(
                packageName = it.packageName,
                allowText = it.allowText,
                allowLazyAssets = it.allowLazyAssets,
                allowStickers = it.allowStickers,
                allowMedia = it.allowMedia,
                allowVoice = it.allowVoice,
                allowProgress = it.allowProgress,
                allowActions = it.allowActions
            )
        }

        SpeyeLogger.d(TAG, "Exporting ${notifications.size} notifications, ${ignoredApps.size} ignored apps, ${appFilters.size} app filters")

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
                history = item.history.map { SerializableHistory(it.oldText, it.timestamp, it.oldActionTitles) },
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
                voiceSender = item.notification.voiceSender,
                actionTitles = item.notification.actionTitles
            )
        }

        val backupData = BackupData(serializableNotifications, ignoredApps, appFilters)
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
