package com.fl0w.speye.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.fl0w.speye.data.settings.ImageFormatSetting
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

object ImageUtils {
    const val MAX_BITMAP_DIMENSION = 1024

    fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int = MAX_BITMAP_DIMENSION): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDimension && h <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxOf(w, h)
        val targetW = (w * scale).toInt().coerceAtLeast(1)
        val targetH = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    fun computeHash(bytes: ByteArray, width: Int, height: Int, extra: String = ""): String {
        val md = MessageDigest.getInstance("MD5")
        val header = "${width}x${height}_${extra}_".toByteArray(Charsets.UTF_8)
        md.update(header)
        md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeBitmapHash(bitmap: Bitmap): String {
        val isHardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE
        val safeBitmap = if (isHardware) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        val isCopy = safeBitmap !== bitmap
        return try {
            try {
                val byteCount = safeBitmap.byteCount
                val buffer = ByteBuffer.allocate(byteCount)
                safeBitmap.copyPixelsToBuffer(buffer)
                val configName = safeBitmap.config?.name ?: "UNKNOWN"
                computeHash(buffer.array(), safeBitmap.width, safeBitmap.height, configName)
            } catch (oom: OutOfMemoryError) {
                System.gc()
                computeSampledHash(safeBitmap)
            }
        } finally {
            if (isCopy) {
                safeBitmap.recycle()
            }
        }
    }

    private fun computeSampledHash(bitmap: Bitmap): String {
        val md = MessageDigest.getInstance("MD5")
        val w = bitmap.width
        val h = bitmap.height
        val header = "sampled_${w}x${h}_".toByteArray(Charsets.UTF_8)
        md.update(header)
        val stepX = maxOf(1, w / 16)
        val stepY = maxOf(1, h / 16)
        val sampleBuffer = ByteBuffer.allocate(4)
        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val pixel = bitmap.getPixel(x, y)
                sampleBuffer.clear()
                sampleBuffer.putInt(pixel)
                md.update(sampleBuffer.array(), 0, 4)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        formatSetting: ImageFormatSetting,
        directory: File = context.filesDir
    ): String? {
        return try {
            val scaled = scaleBitmapIfNeeded(bitmap)
            val hash = computeBitmapHash(scaled)

            resolveOrSaveImageFile(directory, hash, formatSetting) { tempFile ->
                val (compressFormat, quality) = when (formatSetting) {
                    ImageFormatSetting.PNG -> Bitmap.CompressFormat.PNG to 100
                    ImageFormatSetting.JPEG_HIGH -> Bitmap.CompressFormat.JPEG to 90
                    ImageFormatSetting.JPEG_BALANCED -> Bitmap.CompressFormat.JPEG to 80
                    ImageFormatSetting.WEBP -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY to 75
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP to 75
                        }
                    }
                }

                FileOutputStream(tempFile).use { out ->
                    scaled.compress(compressFormat, quality, out)
                    out.flush()
                }
            }
        } catch (e: Exception) {
            SpeyeLogger.e("ImageUtils", "Error saving bitmap", e)
            null
        }
    }

    fun exportToGallery(context: Context, filePath: String): String? {
        val srcFile = File(filePath)
        if (!srcFile.exists() || !srcFile.isFile) {
            Toast.makeText(context, context.getString(com.fl0w.speye.R.string.image_not_found), Toast.LENGTH_SHORT).show()
            return null
        }

        return try {
            val extension = srcFile.extension.lowercase().ifEmpty { "png" }
            val mimeType = when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "apng" -> "image/apng"
                else -> "image/png"
            }
            val fileName = "Speye_${System.currentTimeMillis()}.$extension"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Speye")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
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
                    SpeyeLogger.d("ImageUtils", "Exported photo via MediaStore to $uri")
                    Toast.makeText(context, context.getString(com.fl0w.speye.R.string.saved_to_gallery_success), Toast.LENGTH_SHORT).show()
                    "Pictures/Speye/$fileName"
                } else {
                    Toast.makeText(context, context.getString(com.fl0w.speye.R.string.gallery_save_failed), Toast.LENGTH_SHORT).show()
                    null
                }
            } else {
                val hasWritePermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED

                val targetDir: File = if (hasWritePermission) {
                    @Suppress("DEPRECATION")
                    val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val speyeDir = File(publicDir, "Speye")
                    if (speyeDir.exists() || speyeDir.mkdirs()) speyeDir else (context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir)
                } else {
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
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
                    SpeyeLogger.w("ImageUtils", "MediaScanner failed for ${destFile.absolutePath}: ${e.message}")
                }
                Toast.makeText(context, context.getString(com.fl0w.speye.R.string.saved_to_gallery_success), Toast.LENGTH_SHORT).show()
                destFile.absolutePath
            }
        } catch (e: Exception) {
            SpeyeLogger.e("ImageUtils", "Error exporting image: $filePath", e)
            Toast.makeText(context, context.getString(com.fl0w.speye.R.string.gallery_save_failed), Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun resolveOrSaveImageFile(
        directory: File,
        hash: String,
        formatSetting: ImageFormatSetting,
        writeBytes: (File) -> Unit
    ): String? {
        val targetFileName = "img_${hash}.${formatSetting.extension}"
        val targetFile = File(directory, targetFileName)

        // Fast deduplication path: file already exists and is non-empty
        if (targetFile.exists() && targetFile.length() > 0L) {
            targetFile.setLastModified(System.currentTimeMillis())
            return targetFile.absolutePath
        }

        // Check if already stored under another valid extension
        val existingAlternative = ImageFormatSetting.values()
            .map { File(directory, "img_${hash}.${it.extension}") }
            .firstOrNull { it.exists() && it.length() > 0L }
        if (existingAlternative != null) {
            existingAlternative.setLastModified(System.currentTimeMillis())
            return existingAlternative.absolutePath
        }

        // Write to temporary file first for atomic persistence
        val tempFile = File(directory, "${targetFileName}.${System.currentTimeMillis()}.tmp")
        return try {
            writeBytes(tempFile)
            if (tempFile.length() > 0L) {
                if (tempFile.renameTo(targetFile) || targetFile.exists()) {
                    if (tempFile.exists()) tempFile.delete()
                    targetFile.absolutePath
                } else {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                    targetFile.absolutePath
                }
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }
    }

    const val MAX_RAW_IMAGE_SIZE = 15 * 1024 * 1024L // 15 MB cap for animated stickers/GIFs

    fun isAnimatedImage(bytes: ByteArray, mimeType: String? = null): Boolean {
        if (bytes.size < 12) return false
        val lowerMime = mimeType?.lowercase(java.util.Locale.ROOT)
        if (lowerMime == "image/gif" || lowerMime == "image/apng") return true

        // GIF magic: "GIF87a" or "GIF89a"
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) {
            return true
        }

        // WebP: RIFF....WEBP with "ANIM" chunk
        if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes.size >= 16 && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()) {
            val searchLimit = minOf(bytes.size - 4, 1024)
            for (i in 12..searchLimit) {
                if (bytes[i] == 0x41.toByte() && bytes[i + 1] == 0x4E.toByte() && bytes[i + 2] == 0x49.toByte() && bytes[i + 3] == 0x4D.toByte()) { // "ANIM"
                    return true
                }
            }
        }

        // APNG: starts with PNG magic and contains "acTL" chunk
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
            val searchLimit = minOf(bytes.size - 4, 2048)
            for (i in 8..searchLimit) {
                if (bytes[i] == 0x61.toByte() && bytes[i + 1] == 0x63.toByte() && bytes[i + 2] == 0x54.toByte() && bytes[i + 3] == 0x4C.toByte()) { // "acTL"
                    return true
                }
            }
        }

        return false
    }

    fun saveRawImageFromUri(
        context: Context,
        uri: Uri,
        mimeType: String? = null,
        directory: File = context.filesDir,
        onlyAnimated: Boolean = true
    ): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val data = ByteArray(8192)
                var total = 0L
                var read: Int
                while (stream.read(data).also { read = it } != -1) {
                    total += read
                    if (total > MAX_RAW_IMAGE_SIZE) {
                        SpeyeLogger.w("ImageUtils", "Raw image exceeds MAX_RAW_IMAGE_SIZE ($MAX_RAW_IMAGE_SIZE bytes)")
                        return null
                    }
                    buffer.write(data, 0, read)
                }
                buffer.toByteArray()
            } ?: return null

            if (onlyAnimated && !isAnimatedImage(bytes, mimeType)) {
                return null
            }
            saveRawImageFromBytes(bytes, mimeType, directory)
        } catch (e: Exception) {
            SpeyeLogger.e("ImageUtils", "Failed to save raw image from uri $uri", e)
            null
        }
    }

    fun saveRawImageFromBytes(
        bytes: ByteArray,
        mimeType: String? = null,
        directory: File
    ): String? {
        if (bytes.isEmpty()) return null
        val md = MessageDigest.getInstance("MD5")
        val hash = md.digest(bytes).joinToString("") { "%02x".format(it) }

        val ext = when {
            mimeType?.contains("gif", ignoreCase = true) == true -> "gif"
            mimeType?.contains("webp", ignoreCase = true) == true -> "webp"
            mimeType?.contains("png", ignoreCase = true) == true -> "png"
            mimeType?.contains("jpeg", ignoreCase = true) == true || mimeType?.contains("jpg", ignoreCase = true) == true -> "jpg"
            bytes.size >= 4 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() -> "gif"
            bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() -> "webp"
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
            else -> "webp"
        }

        val targetFileName = "img_${hash}.$ext"
        val targetFile = File(directory, targetFileName)

        if (targetFile.exists() && targetFile.length() > 0L) {
            targetFile.setLastModified(System.currentTimeMillis())
            return targetFile.absolutePath
        }

        val existingAlt = directory.listFiles { _, name -> name.startsWith("img_${hash}.") && !name.endsWith(".tmp") }
            ?.firstOrNull { it.length() > 0L }
        if (existingAlt != null) {
            existingAlt.setLastModified(System.currentTimeMillis())
            return existingAlt.absolutePath
        }

        val tempFile = File(directory, "$targetFileName.${System.currentTimeMillis()}.tmp")
        return try {
            tempFile.writeBytes(bytes)
            if (tempFile.renameTo(targetFile) || targetFile.exists()) {
                if (tempFile.exists()) tempFile.delete()
                targetFile.absolutePath
            } else {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
                targetFile.absolutePath
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }
    }
}

