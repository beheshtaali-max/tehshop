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
                else -> getXhttpPing(config, fallbackIp)
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

    /**
     * XHTTP ping:
     *
     * Measures TCP connection latency to the actual XHTTP endpoint.
     *
     * Example:
     * vless://uuid@ge1.fr3shop.ir:2096?...type=xhttp...
     *
     * This intentionally does not start Xray or the VPN.
     * It only measures the network path to the XHTTP server.
     */
    private fun getXhttpPing(config: String, fallbackIp: String): Long {
        val rawConfig = decryptOrRaw(config)

        val endpoint = parseXhttpEndpoint(rawConfig, fallbackIp)

        if (endpoint.host.isBlank()) {
            Log.w(TAG, "XHTTP ping skipped: endpoint host is empty")
            return PING_NOT_AVAILABLE
        }

        return try {
            Log.d(
                TAG,
                "XHTTP ping start host=${endpoint.host} port=${endpoint.port}"
            )

            val startedAt = System.nanoTime()

            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(endpoint.host, endpoint.port),
                    XHTTP_PING_TIMEOUT_MS
                )
            }

            val delayMs =
                ((System.nanoTime() - startedAt) / 1_000_000L)
                    .coerceAtLeast(1L)

            Log.d(
                TAG,
                "XHTTP ping success host=${endpoint.host} port=${endpoint.port} delay=${delayMs}ms"
            )

            delayMs
        } catch (t: Throwable) {
            Log.e(
                TAG,
                "XHTTP ping failed host=${endpoint.host} port=${endpoint.port}",
                t
            )
            PING_NOT_AVAILABLE
        }
    }

    private fun getSshTcpPing(
        config: String,
        fallbackIp: String
    ): Long {
        val rawAddress = decryptOrRaw(config).ifBlank { fallbackIp }
        val endpoint = parseSshEndpoint(rawAddress)

        if (endpoint.host.isBlank()) {
            return PING_NOT_AVAILABLE
        }

        return try {
            val startedAt = System.nanoTime()

            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(endpoint.host, endpoint.port),
                    SSH_PING_TIMEOUT_MS
                )
            }

            ((System.nanoTime() - startedAt) / 1_000_000L)
                .coerceAtLeast(1L)

        } catch (t: Throwable) {
            Log.e(
                TAG,
                "SSH TCP ping failed host=${endpoint.host} port=${endpoint.port}",
                t
            )
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

    /**
     * Parses:
     *
     * vless://uuid@host:port?...type=xhttp...
     *
     * and returns host + port.
     */
    private fun parseXhttpEndpoint(
        rawConfig: String,
        fallbackIp: String
    ): XhttpEndpoint {

        val value = rawConfig.trim()

        if (value.isEmpty() ||
            value.equals("N/A", ignoreCase = true)
        ) {
            return parseFallbackEndpoint(fallbackIp)
        }

        try {
            if (value.startsWith("vless://", ignoreCase = true) ||
                value.startsWith("vmess://", ignoreCase = true)
            ) {
                val uri = URI(value)

                val host = uri.host.orEmpty().trim()

                val port = if (uri.port > 0) {
                    uri.port
                } else {
                    DEFAULT_XHTTP_PORT
                }

                if (host.isNotBlank()) {
                    return XhttpEndpoint(host, port)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "XHTTP URI parse failed", t)
        }

        /*
         * Fallback parser for cases where URI cannot parse
         * the VLESS link because of unusual query parameters.
         */
        try {
            val atIndex = value.lastIndexOf('@')

            if (atIndex >= 0 && atIndex + 1 < value.length) {
                val authorityAndQuery =
                    value.substring(atIndex + 1)

                val authority =
                    authorityAndQuery
                        .substringBefore('?')
                        .substringBefore('#')
                        .trim()

                val endpoint = parseHostPort(
                    authority,
                    DEFAULT_XHTTP_PORT
                )

                if (endpoint.host.isNotBlank()) {
                    return endpoint
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "XHTTP fallback parse failed", t)
        }

        return parseFallbackEndpoint(fallbackIp)
    }

    private fun parseFallbackEndpoint(
        fallbackIp: String
    ): XhttpEndpoint {
        val value = fallbackIp.trim()

        if (value.isBlank()) {
            return XhttpEndpoint("", DEFAULT_XHTTP_PORT)
        }

        return parseHostPort(
            value,
            DEFAULT_XHTTP_PORT
        )
    }

    private fun parseHostPort(
        value: String,
        defaultPort: Int
    ): XhttpEndpoint {

        val input = value.trim()

        if (input.isBlank()) {
            return XhttpEndpoint("", defaultPort)
        }

        // IPv6 with brackets:
        // [2001:db8::1]:443
        if (input.startsWith("[")) {
            val end = input.indexOf(']')

            if (end > 0) {
                val host =
                    input.substring(1, end).trim()

                val portText =
                    input.substring(end + 1)
                        .removePrefix(":")
                        .trim()

                val port =
                    portText.toIntOrNull()
                        ?.takeIf { it in 1..65535 }
                        ?: defaultPort

                return XhttpEndpoint(host, port)
            }
        }

        val separator = input.lastIndexOf(':')

        /*
         * Normal host:port
         */
        if (separator > 0 &&
            input.indexOf(':') == separator
        ) {
            val host =
                input.substring(0, separator).trim()

            val port =
                input.substring(separator + 1)
                    .trim()
                    .toIntOrNull()
                    ?.takeIf { it in 1..65535 }
                    ?: defaultPort

            return XhttpEndpoint(host, port)
        }

        /*
         * Host without explicit port.
         */
        return XhttpEndpoint(
            input,
            defaultPort
        )
    }

    private fun parseSshEndpoint(
        rawAddress: String
    ): SshEndpoint {

        val value = rawAddress.trim()

        if (value.isEmpty() ||
            value.equals("N/A", ignoreCase = true)
        ) {
            return SshEndpoint("", DEFAULT_SSH_PORT)
        }

        runCatching {
            if (value.startsWith(
                    "ssh://",
                    ignoreCase = true
                )
            ) {
                val uri = URI(value)

                val host =
                    uri.host.orEmpty().trim()

                val port =
                    if (uri.port > 0) {
                        uri.port
                    } else {
                        DEFAULT_SSH_PORT
                    }

                return SshEndpoint(host, port)
            }
        }

        val withoutScheme =
            value
                .removePrefix("SSH://")
                .removePrefix("ssh://")
                .trim()

        // IPv6 with brackets:
        // [2001:db8::1]:2222
        if (withoutScheme.startsWith("[")) {
            val end =
                withoutScheme.indexOf(']')

            if (end > 0) {
                val host =
                    withoutScheme
                        .substring(1, end)
                        .trim()

                val portText =
                    withoutScheme
                        .substring(end + 1)
                        .removePrefix(":")
                        .trim()

                val port =
                    portText
                        .toIntOrNull()
                        ?.takeIf { it in 1..65535 }
                        ?: DEFAULT_SSH_PORT

                return SshEndpoint(host, port)
            }
        }

        val separator =
            withoutScheme.lastIndexOf(':')

        return if (
            separator > 0 &&
            withoutScheme.indexOf(':') == separator
        ) {

            val host =
                withoutScheme
                    .substring(0, separator)
                    .trim()

            val port =
                withoutScheme
                    .substring(separator + 1)
                    .trim()
                    .toIntOrNull()
                    ?.takeIf { it in 1..65535 }
                    ?: DEFAULT_SSH_PORT

            SshEndpoint(host, port)

        } else {

            SshEndpoint(
                withoutScheme,
                DEFAULT_SSH_PORT
            )
        }
    }

    private data class SshEndpoint(
        val host: String,
        val port: Int
    )

    private data class XhttpEndpoint(
        val host: String,
        val port: Int
    )

    companion object {

        private const val TAG =
            "ServersViewModel"

        private const val DEFAULT_SSH_PORT =
            22

        private const val DEFAULT_XHTTP_PORT =
            443

        private const val SSH_PING_TIMEOUT_MS =
            3000

        private const val XHTTP_PING_TIMEOUT_MS =
            3000

        private const val PING_NOT_AVAILABLE =
            -1L
    }
}