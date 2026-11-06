package com.example.fulluikotlin.ui.screens.servers

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.fulluikotlin.data.local.datastore.AppDetailsDataStore
import com.example.fulluikotlin.data.local.datastore.ServerDataStore
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import com.example.fulluikotlin.ui.utils.Crypto
import dev.dev7.lib.v2ray.V2rayController
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ServersViewModel(
    private val serverDataStore: ServerDataStore,
    private val appDetailsDataStore: AppDetailsDataStore
) : ViewModel() {

    val organizedServers = serverDataStore.organizedServersFlow
    val selectedProtocol = serverDataStore.selectedProtocolFlow
    val activeServer = serverDataStore.selectedServerFlow
    val smartServerSelection = serverDataStore.smartServerSelectionFlow

    val defaultProtocol = appDetailsDataStore.defaultProtocolFlow

    private val pingCache = mutableMapOf<String, Long>()

    fun orderProtocols(protocols: List<ProtocolType>): List<ProtocolType> {
        val default = ProtocolType.fromExternalName(defaultProtocol.value)
        return protocols.distinct().sortedWith(
            compareBy<ProtocolType> { if (it == default) 0 else 1 }
                .thenBy { protocols.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
        )
    }

    /**
     * Keeps an existing user choice. The API default is used only when the user has never
     * selected a valid protocol (or that protocol no longer exists in the server payload).
     */
    suspend fun ensureInitialProtocol(availableProtocols: List<ProtocolType>) {
        if (availableProtocols.isEmpty()) return

        val saved = serverDataStore.getSelectedProtocol()
        if (saved != null && saved in availableProtocols) return

        val apiDefault = ProtocolType.fromExternalName(appDetailsDataStore.getDefaultProtocol())
            ?.takeIf { it in availableProtocols }
        val protocol = apiDefault ?: availableProtocols.first()

        if (serverDataStore.isSmartServerSelection()) {
            serverDataStore.selectSmartServer(protocol)
        } else {
            val firstServer = serverDataStore.getServers()
                .firstOrNull { it.protocol == protocol }
            if (firstServer != null) {
                serverDataStore.selectServer(firstServer)
            } else {
                serverDataStore.saveSelectedProtocol(protocol)
            }
        }
    }

    suspend fun activateAndSelectServer(server: Server) {
        val currentList = serverDataStore.getServers()
        val updatedList = currentList.map {
            it.copy(isActive = it.serverid == server.serverid)
        }
        serverDataStore.saveServers(updatedList)
        serverDataStore.selectServer(server)
    }

    suspend fun selectSmartServer(protocol: ProtocolType) {
        serverDataStore.selectSmartServer(protocol)
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
                ProtocolType.V2RAY -> getV2rayTcpPing(config)
                ProtocolType.SSH -> getSshTcpPing(config, fallbackIp)
                ProtocolType.CISCO -> getCiscoTcpPing(config, fallbackIp)
                else -> PING_NOT_AVAILABLE
            }
            pingCache[cacheKey] = ping
            ping
        }
    }

    suspend fun saveSelectedProtocol(protocol: ProtocolType) {
        if (serverDataStore.isSmartServerSelection()) {
            serverDataStore.selectSmartServer(protocol)
            return
        }

        val currentServer = serverDataStore.getSelectedServer()
        if (currentServer?.protocol == protocol) {
            serverDataStore.saveSelectedProtocol(protocol)
            return
        }

        val firstServer = serverDataStore.getServers()
            .firstOrNull { it.protocol == protocol }

        if (firstServer != null) {
            serverDataStore.selectServer(firstServer)
        } else {
            serverDataStore.saveSelectedProtocol(protocol)
        }
    }

    private fun getV2rayTcpPing(config: String): Long {
        return try {
            // This remains a lightweight list indicator. Smart selection uses the separate
            // concurrent HTTP-through-proxy real-delay engine in the v2ray library.
            V2rayController.getV2rayServerDelay(Crypto.decryptThis(config))
        } catch (t: Throwable) {
            Log.e(TAG, "V2Ray TCP ping failed", t)
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


    private fun getCiscoTcpPing(config: String, fallbackIp: String): Long {
        val raw = decryptOrRaw(config)
        val endpoint = runCatching {
            val uri = URI(if (raw.contains("://")) raw else "https://$raw")
            val host = uri.host.orEmpty().ifBlank { fallbackIp.trim() }
            val port = if (uri.port in 1..65535) uri.port else 443
            SshEndpoint(host, port)
        }.getOrElse {
            val hostPort = raw.substringBefore('/').trim()
            val separator = hostPort.lastIndexOf(':')
            if (separator > 0 && hostPort.indexOf(':') == separator) {
                SshEndpoint(
                    hostPort.substring(0, separator).trim().ifBlank { fallbackIp.trim() },
                    hostPort.substring(separator + 1).toIntOrNull()?.takeIf { p -> p in 1..65535 } ?: 443
                )
            } else {
                SshEndpoint(hostPort.ifBlank { fallbackIp.trim() }, 443)
            }
        }
        if (endpoint.host.isBlank()) return PING_NOT_AVAILABLE
        return try {
            val startedAt = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(endpoint.host, endpoint.port), SSH_PING_TIMEOUT_MS)
            }
            ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L)
        } catch (t: Throwable) {
            Log.e(TAG, "Cisco TCP ping failed host=${endpoint.host} port=${endpoint.port}", t)
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
