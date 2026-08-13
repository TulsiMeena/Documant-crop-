package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scanova_preferences")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        val APP_THEME = stringPreferencesKey("app_theme") // "SYSTEM", "LIGHT", "DARK"
        val DEFAULT_SCAN_MODE = stringPreferencesKey("default_scan_mode") // "AUTO", "COLOR", "BW"
        val AUTO_CROP_ENABLED = booleanPreferencesKey("auto_crop_enabled")
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_ONBOARDING_COMPLETED] ?: false
    }

    val appTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: "SYSTEM"
    }

    val defaultScanMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_SCAN_MODE] ?: "AUTO"
    }

    val autoCropEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_CROP_ENABLED] ?: true
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setAppTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME] = theme
        }
    }

    suspend fun setDefaultScanMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_SCAN_MODE] = mode
        }
    }

    suspend fun setAutoCropEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CROP_ENABLED] = enabled
        }
    }
}
