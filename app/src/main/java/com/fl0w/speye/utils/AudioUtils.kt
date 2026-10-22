package com.fl0w.speye.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.abs

object AudioUtils {
    private const val TAG = "AudioUtils"
    private const val MAX_AUDIO_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB

    fun saveAudioFromUri(context: Context, uri: Uri, mimeType: String?): Pair<String, Long>? {
        return try {
            val voiceDir = File(context.filesDir, "voice")
            if (!voiceDir.exists()) {
                voiceDir.mkdirs()
            }

            val extension = determineExtension(mimeType)
            val tempFile = File(voiceDir, "aud_${System.currentTimeMillis()}_${abs(uri.hashCode())}.tmp")

            var totalBytesCopied = 0L
            val md5 = MessageDigest.getInstance("MD5")
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                SpeyeLogger.e(TAG, "Cannot open InputStream for audio URI: $uri")
                return null
            }

            try {
                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            totalBytesCopied += bytesRead
                            if (totalBytesCopied > MAX_AUDIO_SIZE_BYTES) {
                                SpeyeLogger.w(TAG, "Audio file exceeded maximum size ($MAX_AUDIO_SIZE_BYTES bytes), aborting")
                                tempFile.delete()
                                return null
                            }
                            md5.update(buffer, 0, bytesRead)
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                }

                if (totalBytesCopied == 0L) {
                    tempFile.delete()
                    return null
                }

                val hash = md5.digest().joinToString("") { "%02x".format(it) }
                val targetFile = File(voiceDir, "aud_${hash}.$extension")

                val finalFile = if (targetFile.exists() && targetFile.length() > 0L) {
                    targetFile.setLastModified(System.currentTimeMillis())
                    tempFile.delete()
                    targetFile
                } else {
                    if (tempFile.renameTo(targetFile) || targetFile.exists()) {
                        if (tempFile.exists()) tempFile.delete()
                        targetFile
                    } else {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                        targetFile
                    }
                }

                val durationMs = extractDuration(finalFile.absolutePath)
                SpeyeLogger.d(TAG, "Audio saved to ${finalFile.absolutePath} (Duration: ${durationMs}ms, Size: $totalBytesCopied bytes)")
                Pair(finalFile.absolutePath, durationMs)
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                throw e
            }
        } catch (e: Exception) {
            SpeyeLogger.e(TAG, "Failed to save audio from URI: $uri", e)
            null
        }
    }

    fun saveAudioFromBytes(context: Context, bytes: ByteArray, mimeType: String? = "audio/ogg"): Pair<String, Long>? {
        return try {
            if (bytes.isEmpty()) return null
            val voiceDir = File(context.filesDir, "voice")
            if (!voiceDir.exists()) {
                voiceDir.mkdirs()
            }

            val extension = determineExtension(mimeType)
            val md5 = MessageDigest.getInstance("MD5")
            md5.update(bytes)
            val hash = md5.digest().joinToString("") { "%02x".format(it) }
            val targetFile = File(voiceDir, "aud_${hash}.$extension")

            val finalFile = if (targetFile.exists() && targetFile.length() > 0L) {
                targetFile.setLastModified(System.currentTimeMillis())
                targetFile
            } else {
                val tempFile = File(voiceDir, "aud_${hash}_${System.currentTimeMillis()}.tmp")
                FileOutputStream(tempFile).use { it.write(bytes) }
                if (tempFile.renameTo(targetFile) || targetFile.exists()) {
                    if (tempFile.exists()) tempFile.delete()
                    targetFile
                } else {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                    targetFile
                }
            }

            val durationMs = extractDuration(finalFile.absolutePath)
            SpeyeLogger.d(TAG, "Audio bytes saved to ${finalFile.absolutePath} (Duration: ${durationMs}ms)")
            Pair(finalFile.absolutePath, durationMs)
        } catch (e: Exception) {
            SpeyeLogger.e(TAG, "Failed to save audio from bytes", e)
            null
        }
    }

    fun exportVoiceMessage(context: Context, audioPath: String): String? {
        val srcFile = File(audioPath)
        if (!srcFile.exists() || !srcFile.isFile) {
            SpeyeLogger.e(TAG, "Export failed: source audio file does not exist: $audioPath")
            return null
        }

        val fileName = srcFile.name
        val extension = srcFile.extension.lowercase().ifEmpty { "ogg" }
        val mimeType = when (extension) {
            "ogg" -> "audio/ogg"
            "m4a", "mp4" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            else -> "audio/ogg"
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Environment.DIRECTORY_RECORDINGS + "/Speye"
                } else {
                    Environment.DIRECTORY_MUSIC + "/Speye"
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                var uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                var resolvedPath = "$relativePath/$fileName"

                if (uri == null) {
                    // Fallback to Downloads if audio directory was rejected by OEM MediaProvider
                    val dlValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Speye")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, dlValues)
                    resolvedPath = "Download/Speye/$fileName"
                }

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        srcFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    val updateValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(uri, updateValues, null, null)
                    SpeyeLogger.d(TAG, "Exported voice message via MediaStore to $uri ($resolvedPath)")
                    resolvedPath
                } else {
                    SpeyeLogger.e(TAG, "Failed to insert MediaStore audio record")
                    null
                }
            } else {
                val hasWritePermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED

                val targetDir: File = if (hasWritePermission) {
                    @Suppress("DEPRECATION")
                    val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    val speyeDir = File(publicDir, "Speye")
                    if (speyeDir.exists() || speyeDir.mkdirs()) speyeDir else (context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir)
                } else {
                    context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
                }

                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                val destFile = File(targetDir, fileName)
                srcFile.copyTo(destFile, overwrite = true)
                try {
                    MediaScannerConnection.scanFile(
                        context.applicationContext,
                        arrayOf(destFile.absolutePath),
                        arrayOf(mimeType),
                        null
                    )
                } catch (e: Exception) {
                    SpeyeLogger.w(TAG, "MediaScanner failed for ${destFile.absolutePath}: ${e.message}")
                }
                destFile.absolutePath
            }
        } catch (e: Exception) {
            SpeyeLogger.e(TAG, "Error exporting voice message: $audioPath", e)
            null
        }
    }

    fun extractDuration(filePath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            SpeyeLogger.w(TAG, "Could not extract duration from $filePath: ${e.message}")
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun parseDurationFromText(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val regex = Regex("""(?:\b|[(])(?:(\d+):)?(\d{1,2}):(\d{2})(?:\b|[)])""")
        val match = regex.find(text) ?: return null
        val groups = match.groupValues
        val hours = groups[1].takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0L
        val minutes = groups[2].toLongOrNull() ?: return null
        val seconds = groups[3].toLongOrNull() ?: return null
        return (hours * 3600L + minutes * 60L + seconds) * 1000L
    }

    private fun determineExtension(mimeType: String?): String {
        return when {
            mimeType == null -> "ogg"
            mimeType.contains("ogg", ignoreCase = true) -> "ogg"
            mimeType.contains("opus", ignoreCase = true) -> "opus"
            mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) -> "m4a"
            mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true) -> "mp3"
            mimeType.contains("amr", ignoreCase = true) -> "amr"
            mimeType.contains("wav", ignoreCase = true) -> "wav"
            mimeType.contains("aac", ignoreCase = true) -> "aac"
            else -> "ogg"
        }
    }
}
