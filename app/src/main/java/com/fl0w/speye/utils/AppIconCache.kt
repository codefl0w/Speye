package com.fl0w.speye.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * High-performance in-memory LRU cache for application icons.
 * Avoids main-thread PackageManager Binder IPC and repetitive bitmap allocations during LazyColumn scrolling.
 */
object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(128)

    fun getIcon(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return try {
            val bitmap = context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
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
