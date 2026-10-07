package com.fl0w.speye.utils

import android.content.Context
import com.fl0w.speye.data.db.AppDatabase
import com.fl0w.speye.data.settings.AppSettingsManager
import kotlinx.coroutines.flow.first
import java.io.File

object RetentionCleaner {
    private const val TAG = "RetentionCleaner"
    const val ORPHAN_GRACE_PERIOD_MS = 10 * 60 * 1000L // 10 minutes

    suspend fun pruneExpired(context: Context): Int {
        val pruned = try {
            val appSettings = AppSettingsManager(context)
            val days = appSettings.retentionDays.first()
            val cutoff = calculateCutoffTimestamp(days)
            if (cutoff != null) {
                val dao = AppDatabase.getDatabase(context).notificationDao()
                val deletedCount = dao.deleteOlderThan(cutoff)
                if (deletedCount > 0) {
                    SpeyeLogger.d(TAG, "Pruned $deletedCount notifications older than $days days (cutoff: $cutoff)")
                }
                deletedCount
            } else {
                0
            }
        } catch (e: Exception) {
            SpeyeLogger.e(TAG, "Error during retention prune", e)
            0
        }

        // Always sweep unreferenced orphan images and audio files older than grace period
        sweepOrphanImages(context)
        sweepOrphanAudio(context)

        return pruned
    }

    suspend fun sweepOrphanImages(context: Context, gracePeriodMs: Long = ORPHAN_GRACE_PERIOD_MS): Int {
        return try {
            val filesDir = context.filesDir ?: return 0
            val imgFiles = filesDir.listFiles { file ->
                file.isFile && file.name.startsWith("img_")
            } ?: return 0

            val dao = AppDatabase.getDatabase(context).notificationDao()
            val deleted = sweepOrphanFiles(
                files = imgFiles.toList(),
                now = System.currentTimeMillis(),
                gracePeriodMs = gracePeriodMs,
                isReferenced = { path -> dao.countNotificationsUsingImage(path) > 0 }
            )
            if (deleted > 0) {
                SpeyeLogger.d(TAG, "Swept $deleted orphaned image files")
            }
            deleted
        } catch (e: Exception) {
            SpeyeLogger.e(TAG, "Error during orphan image sweep", e)
            0
        }
    }

    suspend fun sweepOrphanAudio(context: Context, gracePeriodMs: Long = ORPHAN_GRACE_PERIOD_MS): Int {
        return try {
            val voiceDir = File(context.filesDir, "voice")
            if (!voiceDir.exists()) return 0
            val audioFiles = voiceDir.listFiles { file ->
                file.isFile && file.name.startsWith("aud_")
            } ?: return 0

            val dao = AppDatabase.getDatabase(context).notificationDao()
            val deleted = sweepOrphanFiles(
                files = audioFiles.toList(),
                now = System.currentTimeMillis(),
                gracePeriodMs = gracePeriodMs,
                isReferenced = { path -> dao.countNotificationsUsingAudio(path) > 0 }
            )
            if (deleted > 0) {
                SpeyeLogger.d(TAG, "Swept $deleted orphaned voice files")
            }
            deleted
        } catch (e: Exception) {
            SpeyeLogger.e(TAG, "Error during orphan audio sweep", e)
            0
        }
    }

    suspend fun sweepOrphanFiles(
        files: List<File>,
        now: Long,
        gracePeriodMs: Long = ORPHAN_GRACE_PERIOD_MS,
        isReferenced: suspend (String) -> Boolean
    ): Int {
        var deleted = 0
        for (file in files) {
            try {
                if (now - file.lastModified() >= gracePeriodMs) {
                    if (!isReferenced(file.absolutePath)) {
                        if (file.delete()) {
                            deleted++
                        }
                    }
                }
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "Failed to inspect/delete file: ${file.name}", e)
            }
        }
        return deleted
    }

    fun calculateCutoffTimestamp(days: Int, now: Long = System.currentTimeMillis()): Long? {
        if (days <= 0) return null
        return now - (days * 24L * 60L * 60L * 1000L)
    }
}

