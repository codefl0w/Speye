package com.fl0w.speye.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cloudDataStore: DataStore<Preferences> by preferencesDataStore(name = "cloud_settings")

class CloudSettingsManager(private val context: Context) {

    companion object {
        private val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val BACKUP_FREQUENCY = stringPreferencesKey("backup_frequency")
        private val LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")
        private val WIFI_ONLY_BACKUP = booleanPreferencesKey("wifi_only_backup")
    }

    val isAutoBackupEnabled: Flow<Boolean> = context.cloudDataStore.data.map { preferences ->
        preferences[AUTO_BACKUP_ENABLED] ?: true
    }

    val isWifiOnlyBackup: Flow<Boolean> = context.cloudDataStore.data.map { preferences ->
        preferences[WIFI_ONLY_BACKUP] ?: false
    }

    val backupFrequency: Flow<String> = context.cloudDataStore.data.map { preferences ->
        preferences[BACKUP_FREQUENCY] ?: "Daily"
    }

    val lastBackupTime: Flow<Long> = context.cloudDataStore.data.map { preferences ->
        preferences[LAST_BACKUP_TIME] ?: 0L
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.cloudDataStore.edit { preferences ->
            preferences[AUTO_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun setBackupFrequency(frequency: String) {
        context.cloudDataStore.edit { preferences ->
            preferences[BACKUP_FREQUENCY] = frequency
        }
    }

    suspend fun setWifiOnlyBackup(enabled: Boolean) {
        context.cloudDataStore.edit { preferences ->
            preferences[WIFI_ONLY_BACKUP] = enabled
        }
    }

    suspend fun setLastBackupTime(timestamp: Long) {
        context.cloudDataStore.edit { preferences ->
            preferences[LAST_BACKUP_TIME] = timestamp
        }
    }
}
