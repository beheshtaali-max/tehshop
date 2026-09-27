package com.example.fulluikotlin.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fulluikotlin.domain.model.BlockedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.splitTunnelDataStore by preferencesDataStore(
    name = "split_tunnel_prefs"
)

class SplitTunnelDataStore(
    private val context: Context
) {
    private val MODE_KEY = stringPreferencesKey("split_tunnel_mode")
    private val BLOCKED_APPS_KEY = stringPreferencesKey("blocked_apps")
    private val WHIT_APPS_KEY = stringSetPreferencesKey("whit_apps")
    private val EXCLUDED_APPS_KEY = stringSetPreferencesKey("excluded_apps")

    private val json = Json { ignoreUnknownKeys = true }

    val modeFlow: Flow<String> = context.splitTunnelDataStore.data
        .map { preferences ->
            preferences[MODE_KEY] ?: "OFF"
        }

    suspend fun saveMode(mode: String) {
        context.splitTunnelDataStore.edit { preferences ->
            preferences[MODE_KEY] = mode
        }
    }

    val whitAppsFlow: Flow<Set<String>> =
        context.splitTunnelDataStore.data
            .map { preferences ->
                preferences[WHIT_APPS_KEY] ?: emptySet()
            }

    suspend fun saveWhitApps(apps: Set<String>) {
        context.splitTunnelDataStore.edit { preferences ->
            preferences[WHIT_APPS_KEY] = apps
        }
    }

    val excludedAppsFlow: Flow<Set<String>> =
        context.splitTunnelDataStore.data
            .map { preferences ->
                preferences[EXCLUDED_APPS_KEY] ?: emptySet()
            }

    suspend fun saveExcludedApps(apps: Set<String>) {
        context.splitTunnelDataStore.edit { preferences ->
            preferences[EXCLUDED_APPS_KEY] = apps
        }
    }

    val blockedAppsFlow: Flow<List<BlockedApp>> =
        context.splitTunnelDataStore.data
            .map { preferences ->
                preferences[BLOCKED_APPS_KEY]?.let {
                    json.decodeFromString<List<BlockedApp>>(it)
                } ?: emptyList()
            }

    suspend fun saveBlockedApps(apps: List<BlockedApp>) {
        val jsonString = json.encodeToString(apps)
        context.splitTunnelDataStore.edit { preferences ->
            preferences[BLOCKED_APPS_KEY] = jsonString
        }
    }
}

