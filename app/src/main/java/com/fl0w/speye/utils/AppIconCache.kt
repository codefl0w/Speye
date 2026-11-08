package com.fl0w.speye.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * High-performance in-memory LRU cache for application icons.
 * Avoids main-thread PackageManager Binder IPC and repetitive bitmap allocations during LazyColumn scrolling.
 */
object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(256)

    fun getIcon(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
            val bitmap = drawable.toBitmap(width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
            cache.put(packageName, bitmap)
            bitmap
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        cache.evictAll()
    }
}
