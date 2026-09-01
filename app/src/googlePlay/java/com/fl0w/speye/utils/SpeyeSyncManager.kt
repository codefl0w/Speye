package com.fl0w.speye.utils

import android.content.Context
import androidx.work.*
import com.fl0w.speye.data.settings.CloudSettingsManager
import com.fl0w.speye.service.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

object SpeyeSyncManager {
    private const val TAG = "SpeyeSyncManager"
    private const val SYNC_WORK_NAME = "cloud_sync"

    fun init(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val cloudManager = CloudSettingsManager(context)
            val enabled = cloudManager.isAutoBackupEnabled.first()
            val frequency = cloudManager.backupFrequency.first()
            
            if (enabled && frequency != "Manual") {
                scheduleSync(context, frequency, ExistingPeriodicWorkPolicy.KEEP)
            }
        }
    }

    fun scheduleSync(context: Context, frequency: String, policy: ExistingPeriodicWorkPolicy) {
        val workManager = WorkManager.getInstance(context)
        
        if (frequency == "Manual") {
            workManager.cancelUniqueWork(SYNC_WORK_NAME)
            SpeyeLogger.d(TAG, "Background sync cancelled (Manual mode)")
            return
        }

        val intervalDays = when (frequency) {
            "Daily" -> 1L
            "Weekly" -> 7L
            "Monthly" -> 30L
            else -> 1L
        }

        // Calculate initial delay to 00:00 local time
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        val initialDelay = calendar.timeInMillis - now
        
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(intervalDays, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            policy,
            syncRequest
        )
        SpeyeLogger.d(TAG, "Background sync scheduled every $intervalDays days. Policy: $policy. Initial delay: ${initialDelay / 1000 / 60} minutes")
    }

    fun cancelSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        SpeyeLogger.d(TAG, "Background sync cancelled")
    }
}
