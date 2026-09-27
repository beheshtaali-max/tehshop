package com.example.fulluikotlin.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

class SettingsDataStore(private val context: Context) {

    private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
    private val NOTIF_ALLOWED_KEY = booleanPreferencesKey("notif_allowed")
    private val KILL_SWITCH_KEY = booleanPreferencesKey("kill_switch")

    val isDarkMode: Flow<Boolean> = context.settingsDataStore.data
        .map { it[DARK_MODE_KEY] ?: false }

    val isNotifAllowed: Flow<Boolean> = context.settingsDataStore.data
        .map { it[NOTIF_ALLOWED_KEY] ?: false }

    val isKillSwitchEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { it[KILL_SWITCH_KEY] ?: false }

    suspend fun setDarkMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[DARK_MODE_KEY] = enabled }
    }

    suspend fun setNotifAllowed(allowed: Boolean) {
        context.settingsDataStore.edit { it[NOTIF_ALLOWED_KEY] = allowed }
    }
    suspend fun setKillSwitch(enabled: Boolean) {
        context.settingsDataStore.edit { it[KILL_SWITCH_KEY] = enabled }
    }

}