package com.example.fulluikotlin.data.local.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fulluikotlin.domain.model.OrganizedServers
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

private val Context.serverDataStore by preferencesDataStore(
    name = "servers_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler<Preferences> {
        // A damaged protobuf preferences file should not require Android Clear Data.
        emptyPreferences()
    }
)

class ServerDataStore(private val context: Context) {

    private val SERVERS_KEY = stringPreferencesKey("servers_key")
    private val SELECTED_SERVER_KEY = stringPreferencesKey("selected_server_key")
    private val ORGANIZED_SERVERS_KEY = stringPreferencesKey("organized_servers_key")
    private val SELECTED_PROTOCOL_KEY = stringPreferencesKey("selected_protocol_key")
    private val SMART_SERVER_SELECTION_KEY = booleanPreferencesKey("smart_server_selection_key")
    private val SMART_RESOLVED_SERVER_KEY = stringPreferencesKey("smart_resolved_server_key")

    private val json = Json { ignoreUnknownKeys = true }

    // Keep one application-wide DataStore subscription alive. The server screen used to
    // create fresh cold collectors on every entry, which re-read and decoded the full server
    // JSON before the list could be displayed. This cached state is populated on IO and is
    // delivered immediately to every screen afterwards.
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferencesState: StateFlow<Preferences> = context.serverDataStore.data.stateIn(
        scope = cacheScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyPreferences()
    )

    val selectedProtocolFlow: StateFlow<ProtocolType?> = preferencesState.map { prefs ->
        prefs[SELECTED_PROTOCOL_KEY]?.let { protocolName ->
            ProtocolType.fromExternalName(protocolName)
                .also { parsed ->
                    if (parsed == null) Log.w(TAG, "Ignoring invalid selected protocol: $protocolName")
                }
        }
    }.stateIn(
        scope = cacheScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    suspend fun saveSelectedProtocol(protocol: ProtocolType) {
        context.serverDataStore.edit { prefs ->
            prefs[SELECTED_PROTOCOL_KEY] = protocol.name
        }
    }

    val smartServerSelectionFlow: StateFlow<Boolean> = preferencesState.map { prefs ->
        prefs[SMART_SERVER_SELECTION_KEY] ?: false
    }.stateIn(
        scope = cacheScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val serversFlow: StateFlow<List<Server>> = preferencesState.map { prefs ->
        decodeOrNull<List<Server>>(prefs[SERVERS_KEY], "servers list") ?: emptyList()
    }.stateIn(
        scope = cacheScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val selectedServerFlow: StateFlow<Server?> = preferencesState.map { prefs ->
        decodeOrNull<Server>(prefs[SELECTED_SERVER_KEY], "selected server")
    }.stateIn(
        scope = cacheScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    /**
     * The concrete server chosen by automatic selection. This is intentionally kept
     * separate from SELECTED_SERVER_KEY because the user's actual preference remains
     * "automatic". Persisting the resolved server lets Home restore its name when the
     * UI/ViewModel is recreated while the VPN connection is still active.
     */
    val smartResolvedServerFlow: StateFlow<Server?> = preferencesState.map { prefs ->
        decodeOrNull<Server>(prefs[SMART_RESOLVED_SERVER_KEY], "smart resolved server")
    }.stateIn(
        scope = cacheScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    suspend fun saveServers(servers: List<Server>) {
        val jsonString = json.encodeToString(servers)
        context.serverDataStore.edit { prefs ->
            prefs[SERVERS_KEY] = jsonString
        }
    }

    val organizedServersFlow: StateFlow<OrganizedServers?> = preferencesState.map { prefs ->
        decodeOrNull<OrganizedServers>(prefs[ORGANIZED_SERVERS_KEY], "organized servers")
    }.stateIn(
        scope = cacheScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    suspend fun saveOrganizedServers(organized: OrganizedServers) {
        val jsonString = json.encodeToString(organized)
        context.serverDataStore.edit { prefs ->
            prefs[ORGANIZED_SERVERS_KEY] = jsonString
        }
    }

    suspend fun selectServer(server: Server) {
        context.serverDataStore.edit { prefs ->
            prefs[SELECTED_SERVER_KEY] = json.encodeToString(server)
            prefs[SELECTED_PROTOCOL_KEY] = server.protocol.name
            prefs[SMART_SERVER_SELECTION_KEY] = false
            prefs.remove(SMART_RESOLVED_SERVER_KEY)
        }
    }

    suspend fun selectSmartServer(protocol: ProtocolType) {
        context.serverDataStore.edit { prefs ->
            prefs[SELECTED_PROTOCOL_KEY] = protocol.name
            prefs[SMART_SERVER_SELECTION_KEY] = true
            prefs.remove(SELECTED_SERVER_KEY)
            // A fresh automatic choice must not display a server from an older session.
            prefs.remove(SMART_RESOLVED_SERVER_KEY)
        }
    }

    suspend fun saveSmartResolvedServer(server: Server) {
        context.serverDataStore.edit { prefs ->
            if (prefs[SMART_SERVER_SELECTION_KEY] == true) {
                prefs[SMART_RESOLVED_SERVER_KEY] = json.encodeToString(server)
            }
        }
    }

    /**
     * Finalizes an automatic selection after the server has passed AUTH.
     *
     * Automatic selection is only the mechanism used to find a server. Once a real server is
     * resolved, it must become the application's normal selected server. Otherwise the next tap
     * on Connect starts the automatic scan again and a recreated Home screen has no durable
     * primary server to restore.
     */
    suspend fun promoteSmartServerToSelected(server: Server) {
        context.serverDataStore.edit { prefs ->
            val cachedServers = decodeOrNull<List<Server>>(
                prefs[SERVERS_KEY],
                "servers list"
            )
            val canonicalServer = cachedServers
                ?.firstOrNull { it.serverid == server.serverid && it.protocol == server.protocol }
                ?: server
            val activeServer = canonicalServer.copy(isActive = true)

            if (cachedServers != null) {
                prefs[SERVERS_KEY] = json.encodeToString(
                    cachedServers.map { cached ->
                        cached.copy(
                            isActive = cached.serverid == activeServer.serverid &&
                                cached.protocol == activeServer.protocol
                        )
                    }
                )
            }

            prefs[SELECTED_SERVER_KEY] = json.encodeToString(activeServer)
            prefs[SELECTED_PROTOCOL_KEY] = activeServer.protocol.name
            prefs[SMART_SERVER_SELECTION_KEY] = false
            prefs.remove(SMART_RESOLVED_SERVER_KEY)
        }
    }

    suspend fun clearSmartResolvedServer() {
        context.serverDataStore.edit { prefs ->
            prefs.remove(SMART_RESOLVED_SERVER_KEY)
        }
    }

    suspend fun getServers(): List<Server> {
        val prefs = context.serverDataStore.data.first()
        return decodeOrNull<List<Server>>(prefs[SERVERS_KEY], "servers list") ?: emptyList()
    }

    suspend fun getOrganizedServers(): OrganizedServers? {
        val prefs = context.serverDataStore.data.first()
        return decodeOrNull<OrganizedServers>(prefs[ORGANIZED_SERVERS_KEY], "organized servers")
    }

    suspend fun getSelectedServer(): Server? {
        val prefs = context.serverDataStore.data.first()
        return decodeOrNull<Server>(prefs[SELECTED_SERVER_KEY], "selected server")
    }

    suspend fun getSelectedProtocol(): ProtocolType? {
        val protocolName = context.serverDataStore.data.first()[SELECTED_PROTOCOL_KEY]
        return protocolName?.let { ProtocolType.fromExternalName(it) }
    }

    suspend fun isSmartServerSelection(): Boolean =
        context.serverDataStore.data.first()[SMART_SERVER_SELECTION_KEY] ?: false

    suspend fun hasSelectedServer(): Boolean = getSelectedServer() != null

    suspend fun clearSelectedServer() {
        context.serverDataStore.edit { prefs ->
            prefs.remove(SELECTED_SERVER_KEY)
            prefs.remove(SMART_RESOLVED_SERVER_KEY)
            prefs[SMART_SERVER_SELECTION_KEY] = false
        }
    }

    suspend fun clearSelectedProtocol() {
        context.serverDataStore.edit { prefs ->
            prefs.remove(SELECTED_PROTOCOL_KEY)
            prefs.remove(SMART_RESOLVED_SERVER_KEY)
            prefs[SMART_SERVER_SELECTION_KEY] = false
        }
    }

    suspend fun clearSelectedServerAndProtocol() {
        context.serverDataStore.edit { prefs ->
            prefs.remove(SELECTED_SERVER_KEY)
            prefs.remove(SMART_RESOLVED_SERVER_KEY)
            prefs.remove(SELECTED_PROTOCOL_KEY)
            prefs.remove(SMART_SERVER_SELECTION_KEY)
        }
    }

    private inline fun <reified T> decodeOrNull(value: String?, label: String): T? {
        if (value.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<T>(value) }
            .onFailure { Log.w(TAG, "Ignoring invalid cached $label", it) }
            .getOrNull()
    }

    private companion object {
        const val TAG = "ServerDataStore"
    }
}
