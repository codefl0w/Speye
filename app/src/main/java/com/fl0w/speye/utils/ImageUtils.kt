package com.fl0w.speye.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    fun exportToGallery(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, context.getString(com.fl0w.speye.R.string.image_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = BitmapFactory.decodeFile(filePath) ?: run {
            Toast.makeText(context, context.getString(com.fl0w.speye.R.string.image_load_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "Speye_${System.currentTimeMillis()}.png"
        val contentResolver = context.contentResolver

        val imageUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Speye")
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            @Suppress("DEPRECATION")
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val speyeDir = File(directory, "Speye")
            if (!speyeDir.exists()) speyeDir.mkdirs()
            val destFile = File(speyeDir, fileName)
            Uri.fromFile(destFile)
        }

        try {
            if (imageUri != null) {
                val outputStream: OutputStream? = contentResolver.openOutputStream(imageUri)
                if (outputStream != null) {
                    outputStream.use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    Toast.makeText(context, context.getString(com.fl0w.speye.R.string.saved_to_gallery_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(com.fl0w.speye.R.string.gallery_save_failed), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, context.getString(com.fl0w.speye.R.string.gallery_save_failed), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(com.fl0w.speye.R.string.gallery_save_failed), Toast.LENGTH_SHORT).show()
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
}

