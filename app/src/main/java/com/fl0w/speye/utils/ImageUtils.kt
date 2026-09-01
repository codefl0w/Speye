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
import java.io.File
import java.io.OutputStream

object ImageUtils {
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
}
