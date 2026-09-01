package com.fl0w.speye.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fl0w.speye.R
import com.fl0w.speye.data.settings.CloudSettingsManager
import com.fl0w.speye.utils.BackupManager
import com.fl0w.speye.utils.DriveAuthManager
import com.fl0w.speye.utils.DriveServiceHelper
import com.fl0w.speye.utils.SpeyeLogger
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID = "sync_channel"
        private const val NOTIFICATION_ID = 2001
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        
        SpeyeLogger.d("SyncWorker", ">>> WORKER TRIGGERED BY SYSTEM <<<")
        
        // Ensure initialized
        DriveAuthManager.init(context)
        
        val driveService = DriveAuthManager.createDriveService(context) ?: run {
            SpeyeLogger.e("SyncWorker", "No credential found. User might be signed out.")
            return Result.failure()
        }

        val driveHelper = DriveServiceHelper(driveService)
        val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val fileName = "SpeyeBackup-$date.spy"
        val tempFile = File(context.cacheDir, fileName)

        return try {
            com.fl0w.speye.utils.RetentionCleaner.pruneExpired(context)
            BackupManager.exportToFile(context, tempFile)
            SpeyeLogger.d("SyncWorker", "Local backup generated: ${tempFile.length()} bytes")

            val fileId = driveHelper.uploadBackup(tempFile)
            if (fileId != null) {
                SpeyeLogger.d("SyncWorker", "Cloud upload successful. File ID: $fileId")
                CloudSettingsManager(context).setLastBackupTime(System.currentTimeMillis())
                showNotification(
                    context.getString(R.string.sync_success_title),
                    context.getString(R.string.sync_success_msg)
                )
                Result.success()
            } else {
                SpeyeLogger.e("SyncWorker", "Cloud upload failed (returned null ID)")
                showNotification(
                    context.getString(R.string.sync_failed_title),
                    context.getString(R.string.sync_failed_msg)
                )
                Result.retry()
            }
        } catch (e: Exception) {
            SpeyeLogger.e("SyncWorker", "Sync operation failed", e)
            showNotification(
                context.getString(R.string.sync_failed_title),
                context.getString(R.string.sync_failed_msg)
            )
            Result.retry()
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.sync_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
