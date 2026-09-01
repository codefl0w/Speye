package com.fl0w.speye.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.billingDataStore: DataStore<Preferences> by preferencesDataStore(name = "billing_settings")

class BillingSettingsManager(private val context: Context) {

    companion object {
        private val IS_PRO_USER = booleanPreferencesKey("is_pro_user")
    }

    val isProUser: Flow<Boolean> = context.billingDataStore.data.map { preferences ->
        preferences[IS_PRO_USER] ?: false
    }

    suspend fun setProUser(isPro: Boolean) {
        context.billingDataStore.edit { preferences ->
            preferences[IS_PRO_USER] = isPro
        }
    }
}
