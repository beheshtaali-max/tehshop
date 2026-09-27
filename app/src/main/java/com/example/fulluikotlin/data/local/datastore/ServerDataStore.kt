package com.example.fulluikotlin.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fulluikotlin.domain.model.OrganizedServers
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.serverDataStore by preferencesDataStore(name = "servers_prefs")

class ServerDataStore(private val context: Context) {

    private val SERVERS_KEY = stringPreferencesKey("servers_key")
    private val SELECTED_SERVER_KEY = stringPreferencesKey("selected_server_key")  // کلید جدید
    private val ORGANIZED_SERVERS_KEY = stringPreferencesKey("organized_servers_key")
    private val SELECTED_PROTOCOL_KEY = stringPreferencesKey("selected_protocol_key")

    private val json = Json { ignoreUnknownKeys = true }

    val selectedProtocolFlow: Flow<ProtocolType?> = context.serverDataStore.data.map { prefs ->
        prefs[SELECTED_PROTOCOL_KEY]?.let { protocolName ->
            try {
                ProtocolType.valueOf(protocolName.uppercase())
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun saveSelectedProtocol(protocol: ProtocolType) {
        context.serverDataStore.edit { prefs ->
            prefs[SELECTED_PROTOCOL_KEY] = protocol.name
        }
    }

    val serversFlow: Flow<List<Server>> =
        context.serverDataStore.data.map { prefs ->
            prefs[SERVERS_KEY]?.let {
                json.decodeFromString<List<Server>>(it)
            } ?: emptyList()
        }

    val selectedServerFlow: Flow<Server?> =
        context.serverDataStore.data.map { prefs ->
            prefs[SELECTED_SERVER_KEY]?.let {
                json.decodeFromString<Server>(it)
            }
        }
    suspend fun saveServers(servers: List<Server>) {
        val jsonString = json.encodeToString(servers)
        context.serverDataStore.edit { prefs ->
            prefs[SERVERS_KEY] = jsonString
        }
    }

    val organizedServersFlow: Flow<OrganizedServers?> =
        context.serverDataStore.data.map { prefs ->
            prefs[ORGANIZED_SERVERS_KEY]?.let {
                json.decodeFromString<OrganizedServers>(it)
            }
        }

    suspend fun saveOrganizedServers(organized: OrganizedServers) {
        val jsonString = json.encodeToString(organized)
        context.serverDataStore.edit { prefs ->
            prefs[ORGANIZED_SERVERS_KEY] = jsonString
        }
    }

    suspend fun selectServer(server: Server) {
        context.serverDataStore.edit { prefs ->
            prefs[SELECTED_SERVER_KEY] = json.encodeToString(server)
        }
    }

    suspend fun getSelectedServer(): Server? = selectedServerFlow.first()
    suspend fun getSelectedProtocol(): ProtocolType? = selectedProtocolFlow.first()
    suspend fun hasSelectedServer(): Boolean = getSelectedServer() != null

    suspend fun clearSelectedServer() {
        context.serverDataStore.edit { prefs ->
            prefs.remove(SELECTED_SERVER_KEY)
        }
    }

    suspend fun clearSelectedProtocol() {
        context.serverDataStore.edit { prefs ->
            prefs.remove(SELECTED_PROTOCOL_KEY)
        }
    }

    suspend fun clearSelectedServerAndProtocol() {
        context.serverDataStore.edit { prefs ->
            prefs.remove(SELECTED_SERVER_KEY)
            prefs.remove(SELECTED_PROTOCOL_KEY)
        }
    }
}