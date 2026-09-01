package com.fl0w.speye.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "debug_settings")

class DebugSettingsManager(private val context: Context) {

    companion object {
        private val DEBUG_MODE_ENABLED = booleanPreferencesKey("debug_mode_enabled")
        private val TEST_BUTTON_ENABLED = booleanPreferencesKey("test_button_enabled")
        private val LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")
        private val ACCORDION_ENABLED = booleanPreferencesKey("accordion_enabled")
    }

    val isDebugModeEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DEBUG_MODE_ENABLED] ?: false
    }

    val isTestButtonEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[TEST_BUTTON_ENABLED] ?: false
    }

    val isLoggingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LOGGING_ENABLED] ?: false
    }

    val isAccordionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ACCORDION_ENABLED] ?: false
    }

    suspend fun setDebugModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DEBUG_MODE_ENABLED] = enabled
        }
    }

    suspend fun setTestButtonEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TEST_BUTTON_ENABLED] = enabled
        }
    }

    suspend fun setLoggingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LOGGING_ENABLED] = enabled
        }
    }

    suspend fun setAccordionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ACCORDION_ENABLED] = enabled
        }
    }
}
