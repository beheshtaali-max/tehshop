package com.example.fulluikotlin.ui.screens.servers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dev7.lib.v2ray.V2rayController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import com.example.fulluikotlin.data.local.datastore.ServerDataStore
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import com.example.fulluikotlin.ui.utils.Crypto
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

class ServersViewModel(
    private val serverDataStore: ServerDataStore
) : ViewModel() {

    val organizedServers = serverDataStore.organizedServersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val selectedProtocol = serverDataStore.selectedProtocolFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val activeServer = serverDataStore.selectedServerFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val pingCache = mutableMapOf<String, Long>()


    suspend fun activateAndSelectServer(server: Server) {
        val currentList = serverDataStore.serversFlow.first()
        val updatedList = currentList.map {
            it.copy(isActive = it.serverid == server.serverid)
        }
        serverDataStore.saveServers(updatedList)
        serverDataStore.selectServer(server)
    }


    suspend fun getRealPing(
        serverId: String,
        config: String,
        protocol: ProtocolType,
        fallbackIp: String = ""
    ): Long {
        val cacheKey = "${protocol.name}:$serverId"
        return pingCache[cacheKey] ?: withContext(Dispatchers.IO) {
            val ping = when (protocol) {
                ProtocolType.V2RAY -> getV2rayPing(config)
                ProtocolType.SSH -> getSshTcpPing(config, fallbackIp)
                else -> PING_NOT_AVAILABLE
            }
            pingCache[cacheKey] = ping
            ping
        }
    }

    suspend fun saveSelectedProtocol(protocol: ProtocolType) {
        serverDataStore.saveSelectedProtocol(protocol)
    }

    private fun getV2rayPing(config: String): Long {
        return try {
            V2rayController.getV2rayServerDelay(Crypto.decryptThis(config))
        } catch (t: Throwable) {
            Log.e(TAG, "V2Ray ping failed", t)
            PING_NOT_AVAILABLE
        }
    }

    private fun getSshTcpPing(config: String, fallbackIp: String): Long {
        val rawAddress = decryptOrRaw(config).ifBlank { fallbackIp }
        val endpoint = parseSshEndpoint(rawAddress)
        if (endpoint.host.isBlank()) return PING_NOT_AVAILABLE

        return try {
            val startedAt = System.nanoTime()
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(endpoint.host, endpoint.port),
                    SSH_PING_TIMEOUT_MS
                )
            }
            ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L)
        } catch (t: Throwable) {
            Log.e(TAG, "SSH TCP ping failed host=${endpoint.host} port=${endpoint.port}", t)
            PING_NOT_AVAILABLE
        }
    }

    private fun decryptOrRaw(value: String): String {
        return try {
            Crypto.decryptThis(value)
        } catch (_: Throwable) {
            value
        }.trim()
    }

    private fun parseSshEndpoint(rawAddress: String): SshEndpoint {
        val value = rawAddress.trim()
        if (value.isEmpty() || value.equals("N/A", ignoreCase = true)) {
            return SshEndpoint("", DEFAULT_SSH_PORT)
        }

        runCatching {
            if (value.startsWith("ssh://", ignoreCase = true)) {
                val uri = URI(value)
                val host = uri.host.orEmpty().trim()
                val port = if (uri.port > 0) uri.port else DEFAULT_SSH_PORT
                return SshEndpoint(host, port)
            }
        }

        val withoutScheme = value.removePrefix("SSH://").removePrefix("ssh://").trim()

        // IPv6 with brackets: [2001:db8::1]:2222
        if (withoutScheme.startsWith("[")) {
            val end = withoutScheme.indexOf(']')
            if (end > 0) {
                val host = withoutScheme.substring(1, end).trim()
                val portText = withoutScheme.substring(end + 1).removePrefix(":").trim()
                val port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: DEFAULT_SSH_PORT
                return SshEndpoint(host, port)
            }
        }

        val separator = withoutScheme.lastIndexOf(':')
        return if (separator > 0 && withoutScheme.indexOf(':') == separator) {
            val host = withoutScheme.substring(0, separator).trim()
            val port = withoutScheme.substring(separator + 1).trim()
                .toIntOrNull()
                ?.takeIf { it in 1..65535 }
                ?: DEFAULT_SSH_PORT
            SshEndpoint(host, port)
        } else {
            SshEndpoint(withoutScheme, DEFAULT_SSH_PORT)
        }
    }

    private data class SshEndpoint(val host: String, val port: Int)

    companion object {
        private const val TAG = "ServersViewModel"
        private const val DEFAULT_SSH_PORT = 22
        private const val SSH_PING_TIMEOUT_MS = 3000
        private const val PING_NOT_AVAILABLE = -1L
    }
}
