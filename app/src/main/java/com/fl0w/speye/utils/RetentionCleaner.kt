package com.fl0w.speye.utils

import android.content.Context
import com.fl0w.speye.data.db.AppDatabase
import com.fl0w.speye.data.settings.AppSettingsManager
import kotlinx.coroutines.flow.first
import java.io.File

object RetentionCleaner {
    suspend fun pruneExpired(context: Context): Int {
        return try {
            val appSettings = AppSettingsManager(context)
            val days = appSettings.retentionDays.first()
            if (days <= 0) return 0

            val cutoff = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
            val dao = AppDatabase.getDatabase(context).notificationDao()

            val expiredImages = dao.getImagePathsOlderThan(cutoff)
            expiredImages.forEach { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    SpeyeLogger.e("RetentionCleaner", "Failed to delete image: $path", e)
                }
            }

            val deletedCount = dao.deleteOlderThan(cutoff)
            if (deletedCount > 0) {
                SpeyeLogger.d("RetentionCleaner", "Pruned $deletedCount notifications older than $days days (cutoff: $cutoff)")
            }
            deletedCount
        } catch (e: Exception) {
            SpeyeLogger.e("RetentionCleaner", "Error during retention prune", e)
            0
        }
    }
}
