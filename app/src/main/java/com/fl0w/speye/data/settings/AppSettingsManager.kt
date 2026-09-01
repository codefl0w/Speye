package com.fl0w.speye.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

enum class ImageFormatSetting(val extension: String, val quality: Int) {
    PNG("png", 100),
    JPEG_HIGH("jpg", 90),
    JPEG_BALANCED("jpg", 80),
    WEBP("webp", 75);

    companion object {
        fun fromString(value: String): ImageFormatSetting {
            return entries.find { it.name == value } ?: PNG
        }
    }
}

class AppSettingsManager(private val context: Context) {

    companion object {
        private val IMAGE_FORMAT = stringPreferencesKey("image_format")
        private val RETENTION_DAYS = intPreferencesKey("retention_days")
        private val TEXT_SCALE = floatPreferencesKey("text_scale")
        private val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        private val REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
    }

    val imageFormat: Flow<ImageFormatSetting> = context.appDataStore.data.map { preferences ->
        val raw = preferences[IMAGE_FORMAT] ?: ImageFormatSetting.PNG.name
        ImageFormatSetting.fromString(raw)
    }

    val retentionDays: Flow<Int> = context.appDataStore.data.map { preferences ->
        preferences[RETENTION_DAYS] ?: 0
    }

    val textScale: Flow<Float> = context.appDataStore.data.map { preferences ->
        preferences[TEXT_SCALE] ?: 1.0f
    }

    val highContrast: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[HIGH_CONTRAST] ?: false
    }

    val reduceAnimations: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[REDUCE_ANIMATIONS] ?: false
    }

    suspend fun setImageFormat(format: ImageFormatSetting) {
        context.appDataStore.edit { preferences ->
            preferences[IMAGE_FORMAT] = format.name
        }
    }

    suspend fun setRetentionDays(days: Int) {
        context.appDataStore.edit { preferences ->
            preferences[RETENTION_DAYS] = days
        }
    }

    suspend fun setTextScale(scale: Float) {
        context.appDataStore.edit { preferences ->
            preferences[TEXT_SCALE] = scale
        }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[HIGH_CONTRAST] = enabled
        }
    }

    suspend fun setReduceAnimations(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[REDUCE_ANIMATIONS] = enabled
        }
    }
}
