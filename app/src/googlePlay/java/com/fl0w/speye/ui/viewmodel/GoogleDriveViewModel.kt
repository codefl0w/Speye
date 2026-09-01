package com.fl0w.speye.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.fl0w.speye.data.settings.CloudSettingsManager
import com.fl0w.speye.utils.DriveAuthManager
import com.fl0w.speye.utils.DriveServiceHelper
import com.fl0w.speye.utils.SpeyeLogger
import com.fl0w.speye.utils.SpeyeSyncManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GoogleDriveViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "GoogleDriveViewModel"
    private val cloudManager = CloudSettingsManager(application)

    private val _account = DriveAuthManager.account
    val account: StateFlow<GoogleSignInAccount?> = _account

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp

    private val _storageInfo = MutableStateFlow<Pair<Long, Long>?>(null)
    val storageInfo: StateFlow<Pair<Long, Long>?> = _storageInfo

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring

    val isAutoBackupEnabled = cloudManager.isAutoBackupEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val backupFrequency = cloudManager.backupFrequency.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Daily")
    val lastBackupTime = cloudManager.lastBackupTime.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    init {
        refreshStorageInfo()
        updateSyncSchedule()
    }

    fun refreshStorageInfo() {
        viewModelScope.launch {
            try {
                val driveService = DriveAuthManager.createDriveService(getApplication()) ?: return@launch
                
                val driveHelper = DriveServiceHelper(driveService)
                _storageInfo.value = driveHelper.getStorageInfo()
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "Failed to refresh storage info", e)
            }
        }
    }

    fun handleSignInResult(account: GoogleSignInAccount?) {
        DriveAuthManager.handleSignInResult(account)
        if (account != null) refreshStorageInfo()
    }

    fun signOut() {
        DriveAuthManager.signOut(getApplication()) {
            // Log out successful
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch { 
            cloudManager.setAutoBackupEnabled(enabled)
            if (enabled) {
                val freq = cloudManager.backupFrequency.first()
                SpeyeSyncManager.scheduleSync(getApplication(), freq, ExistingPeriodicWorkPolicy.UPDATE)
            } else {
                SpeyeSyncManager.cancelSync(getApplication())
            }
        }
    }

    fun setBackupFrequency(frequency: String) {
        viewModelScope.launch { 
            cloudManager.setBackupFrequency(frequency)
            val enabled = cloudManager.isAutoBackupEnabled.first()
            if (enabled) {
                SpeyeSyncManager.scheduleSync(getApplication(), frequency, ExistingPeriodicWorkPolicy.UPDATE)
            }
        }
    }

    private fun updateSyncSchedule() {
        // Now handled by SpeyeSyncManager.init() on startup 
        // and setAutoBackupEnabled/setBackupFrequency on change
    }

    fun backupNow() {
        viewModelScope.launch {
            _isBackingUp.value = true
            SpeyeLogger.d(TAG, "Manual backup triggered")
            try {
                val driveService = DriveAuthManager.createDriveService(getApplication())
                if (driveService == null) {
                    SpeyeLogger.e(TAG, "No credential available for backup")
                    return@launch
                }

                val driveHelper = DriveServiceHelper(driveService)
                val tempFile = java.io.File(getApplication<Application>().cacheDir, "manual_backup.spy")
                com.fl0w.speye.utils.BackupManager.exportToFile(getApplication(), tempFile)
                
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    SpeyeLogger.e(TAG, "Backup aborted: File is empty or does not exist.")
                } else {
                    val resultId = driveHelper.uploadBackup(tempFile)
                    if (resultId != null) {
                        SpeyeLogger.d(TAG, "Cloud upload successful: $resultId")
                        cloudManager.setLastBackupTime(System.currentTimeMillis())
                        refreshStorageInfo()
                    } else {
                        SpeyeLogger.e(TAG, "Cloud upload failed (returned null ID)")
                    }
                }
                tempFile.delete()
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "Manual backup failed with exception", e)
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            _isRestoring.value = true
            SpeyeLogger.d(TAG, "Cloud restore triggered")
            try {
                val driveService = DriveAuthManager.createDriveService(getApplication()) ?: return@launch
                
                val driveHelper = DriveServiceHelper(driveService)
                val tempFile = java.io.File(getApplication<Application>().cacheDir, "cloud_restore.spy")
                
                val success = driveHelper.downloadLatestBackup(tempFile)
                if (success) {
                    SpeyeLogger.d(TAG, "Downloaded cloud backup: ${tempFile.length()} bytes")
                    com.fl0w.speye.utils.BackupManager.importFromFile(getApplication(), tempFile)
                    SpeyeLogger.d(TAG, "Cloud restore data merged successfully")
                } else {
                    SpeyeLogger.e(TAG, "No backup found in cloud to restore")
                }
                tempFile.delete()
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "Cloud restore failed", e)
            } finally {
                _isRestoring.value = false
            }
        }
    }

    fun deleteCloudSaves() {
        viewModelScope.launch {
            try {
                val driveService = DriveAuthManager.createDriveService(getApplication()) ?: return@launch
                
                val driveHelper = DriveServiceHelper(driveService)
                val success = driveHelper.deleteAllCloudData()
                if (success) {
                    SpeyeLogger.d(TAG, "Cloud data deleted successfully")
                }
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "Failed to delete cloud data", e)
            }
        }
    }
}
