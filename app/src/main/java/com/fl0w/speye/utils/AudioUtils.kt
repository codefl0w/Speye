package com.fl0w.speye.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.abs

object AudioUtils {
    private const val TAG = "AudioUtils"
    private const val MAX_AUDIO_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB
    const val EXPORT_DIRECTORY_PATH = "/sdcard/Speye/saved/voice"

    fun saveAudioFromUri(context: Context, uri: Uri, mimeType: String?): Pair<String, Long>? {
        return try {
            val voiceDir = File(context.filesDir, "voice")
            if (!voiceDir.exists()) {
                voiceDir.mkdirs()
            }

            val extension = determineExtension(mimeType)
            val fileName = "aud_${System.currentTimeMillis()}_${abs(uri.toString().hashCode())}.$extension"
            val targetFile = File(voiceDir, fileName)

            var totalBytesCopied = 0L
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                SpeyeLogger.e(TAG, "Cannot open InputStream for audio URI: $uri")
                return null
            }

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        totalBytesCopied += bytesRead
                        if (totalBytesCopied > MAX_AUDIO_SIZE_BYTES) {
                            SpeyeLogger.w(TAG, "Audio file exceeded maximum size ($MAX_AUDIO_SIZE_BYTES bytes), aborting")
                            targetFile.delete()
                            return null
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            val durationMs = extractDuration(targetFile.absolutePath)
            SpeyeLogger.d(TAG, "Audio saved to ${targetFile.absolutePath} (Duration: ${durationMs}ms, Size: $totalBytesCopied bytes)")
            Pair(targetFile.absolutePath, durationMs)
        } catch (e: Exception) {
            SpeyeLogger.e(TAG, "Failed to save audio from URI: $uri", e)
            null
        }
    }

    fun saveAudioFromBytes(context: Context, bytes: ByteArray, mimeType: String? = "audio/ogg"): Pair<String, Long>? {
        return try {
            val voiceDir = File(context.filesDir, "voice")
            if (!voiceDir.exists()) {
                voiceDir.mkdirs()
            }

            val extension = determineExtension(mimeType)
            val fileName = "aud_${System.currentTimeMillis()}_${abs(bytes.contentHashCode())}.$extension"
            val targetFile = File(voiceDir, fileName)

            FileOutputStream(targetFile).use { it.write(bytes) }
            val durationMs = extractDuration(targetFile.absolutePath)
            SpeyeLogger.d(TAG, "Audio bytes saved to ${targetFile.absolutePath} (Duration: ${durationMs}ms)")
            Pair(targetFile.absolutePath, durationMs)
        } catch (e: Exception) {
            SpeyeLogger.e(TAG, "Failed to save audio from bytes", e)
            null
        }
    }

    fun exportVoiceMessage(context: Context, audioPath: String): File? {
        return try {
            val srcFile = File(audioPath)
            if (!srcFile.exists() || !srcFile.isFile) {
                SpeyeLogger.e(TAG, "Export failed: source audio file does not exist: $audioPath")
                return null
            }

            var exportDir = File(EXPORT_DIRECTORY_PATH)
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                exportDir = File(android.os.Environment.getExternalStorageDirectory(), "Speye/saved/voice")
                if (!exportDir.exists() && !exportDir.mkdirs()) {
                    exportDir = File(context.getExternalFilesDir(null), "Speye/saved/voice")
                    if (!exportDir.exists()) {
                        exportDir.mkdirs()
                    }
                }
            }

            val destFile = File(exportDir, srcFile.name)
            srcFile.copyTo(destFile, overwrite = true)
            SpeyeLogger.d(TAG, "Exported voice message to ${destFile.absolutePath}")

            try {
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(destFile.absolutePath),
                    null,
                    null
                )
            } catch (e: Exception) {
                SpeyeLogger.w(TAG, "MediaScanner failed for ${destFile.absolutePath}: ${e.message}")
            }

            destFile
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
