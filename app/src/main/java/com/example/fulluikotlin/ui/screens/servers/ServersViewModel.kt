```kotlin
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
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLDecoder

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

        val rawConfig = decryptOrRaw(config)

        val isXhttp = isXhttpConfig(rawConfig)

        val cacheKey =
            "${protocol.name}:$serverId:${if (isXhttp) "xhttp" else "normal"}"

        /*
         * فقط نتیجه موفق را از Cache می‌خوانیم.
         */
        pingCache[cacheKey]?.let { cached ->
            if (cached > 0) {
                return cached
            }
        }

        val ping = withContext(Dispatchers.IO) {

            when {
                isXhttp -> {
                    getXhttpPing(
                        rawConfig,
                        fallbackIp
                    )
                }

                protocol == ProtocolType.V2RAY -> {
                    getV2rayPing(rawConfig)
                }

                protocol == ProtocolType.SSH -> {
                    getSshTcpPing(
                        rawConfig,
                        fallbackIp
                    )
                }

                else -> {
                    PING_NOT_AVAILABLE
                }
            }
        }

        /*
         * نتیجه ناموفق Cache نمی‌شود.
         */
        if (ping > 0) {
            pingCache[cacheKey] = ping
        }

        return ping
    }

    suspend fun saveSelectedProtocol(
        protocol: ProtocolType
    ) {
        serverDataStore.saveSelectedProtocol(protocol)
    }

    private fun getV2rayPing(
        config: String
    ): Long {

        return try {

            V2rayController.getV2rayServerDelay(
                Crypto.decryptThis(config)
            )

        } catch (t: Throwable) {

            Log.e(
                TAG,
                "V2Ray ping failed",
                t
            )

            PING_NOT_AVAILABLE
        }
    }

    /**
     * Real HTTPS latency test for XHTTP.
     *
     * This does not start VPN/Xray.
     *
     * It measures:
     *
     * DNS
     * TCP
     * TLS
     * HTTP response
     */
    private fun getXhttpPing(
        rawConfig: String,
        fallbackIp: String
    ): Long {

        val endpoint =
            parseXhttpConfig(
                rawConfig,
                fallbackIp
            )

        if (endpoint.host.isBlank()) {

            Log.w(
                TAG,
                "XHTTP ping skipped: host is empty"
            )

            return PING_NOT_AVAILABLE
        }

        val scheme =
            if (endpoint.tls) {
                "https"
            } else {
                "http"
            }

        val path =
            endpoint.path
                .ifBlank { "/" }

        val requestUrl =
            "$scheme://${endpoint.host}:${endpoint.port}$path"

        Log.d(
            TAG,
            "XHTTP ping URL=$requestUrl"
        )

        var connection: HttpURLConnection? = null

        return try {

            val start =
                System.nanoTime()

            val url =
                URL(requestUrl)

            connection =
                url.openConnection() as HttpURLConnection

            connection.connectTimeout =
                XHTTP_CONNECT_TIMEOUT_MS

            connection.readTimeout =
                XHTTP_READ_TIMEOUT_MS

            connection.useCaches =
                false

            connection.instanceFollowRedirects =
                false

            connection.requestMethod =
                "GET"

            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0"
            )

            connection.setRequestProperty(
                "Accept",
                "*/*"
            )

            connection.connect()

            val responseCode =
                connection.responseCode

            val latency =
                (
                    (System.nanoTime() - start) /
                        1_000_000L
                    ).coerceAtLeast(1L)

            Log.d(
                TAG,
                "XHTTP ping response=" +
                    "$responseCode latency=${latency}ms"
            )

            /*
             * حتی 403 / 404 نیز نشان می‌دهد
             * سرور پاسخ داده است.
             */
            if (responseCode in 100..599) {
                latency
            } else {
                PING_NOT_AVAILABLE
            }

        } catch (t: Throwable) {

            Log.e(
                TAG,
                "XHTTP HTTPS ping failed " +
                    "host=${endpoint.host} " +
                    "port=${endpoint.port}",
                t
            )

            PING_NOT_AVAILABLE

        } finally {

            try {
                connection?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Detect XHTTP from VLESS/VMess configuration.
     */
    private fun isXhttpConfig(
        config: String
    ): Boolean {

        if (config.isBlank()) {
            return false
        }

        val decoded =
            runCatching {
                URLDecoder.decode(
                    config,
                    "UTF-8"
                )
            }.getOrDefault(config)

        val value =
            decoded.lowercase()

        return value.contains("type=xhttp") ||
            value.contains("type%3Dxhttp") ||
            value.contains("\"type\":\"xhttp\"") ||
            value.contains("\"type\" : \"xhttp\"") ||
            value.contains("network=xhttp")
    }

    /**
     * Parse VLESS/VMess XHTTP configuration.
     */
    private fun parseXhttpConfig(
        rawConfig: String,
        fallbackIp: String
    ): XhttpEndpoint {

        val value =
            rawConfig.trim()

        if (
            value.isBlank() ||
            value.equals(
                "N/A",
                ignoreCase = true
            )
        ) {

            return parseFallbackEndpoint(
                fallbackIp
            )
        }

        /*
         * Standard VLESS / VMess URL.
         */
        try {

            if (
                value.startsWith(
                    "vless://",
                    ignoreCase = true
                ) ||
                value.startsWith(
                    "vmess://",
                    ignoreCase = true
                )
            ) {

                val uri =
                    URI(value)

                val host =
                    uri.host
                        .orEmpty()
                        .trim()

                val port =
                    if (uri.port > 0) {
                        uri.port
                    } else {
                        DEFAULT_XHTTP_PORT
                    }

                if (host.isNotBlank()) {

                    val params =
                        parseQuery(
                            uri.rawQuery.orEmpty()
                        )

                    val path =
                        params["path"]
                            ?.let {
                                decodeValue(it)
                            }
                            ?.ifBlank {
                                "/"
                            }
                            ?: "/"

                    val security =
                        params["security"]
                            ?.let {
                                decodeValue(it)
                                    .lowercase()
                            }
                            ?: ""

                    val tls =
                        security == "tls" ||
                            security == "reality" ||
                            port == 443 ||
                            port == 8443 ||
                            port == 2096

                    return XhttpEndpoint(
                        host = host,
                        port = port,
                        path = path,
                        tls = tls
                    )
                }
            }

        } catch (t: Throwable) {

            Log.w(
                TAG,
                "XHTTP URI parse failed",
                t
            )
        }

        /*
         * Fallback parser.
         */
        try {

            val atIndex =
                value.lastIndexOf('@')

            if (
                atIndex >= 0 &&
                atIndex + 1 < value.length
            ) {

                val authorityAndQuery =
                    value.substring(
                        atIndex + 1
                    )

                val authority =
                    authorityAndQuery
                        .substringBefore('?')
                        .substringBefore('#')
                        .trim()

                val basic =
                    parseHostPort(
                        authority,
                        DEFAULT_XHTTP_PORT
                    )

                if (basic.host.isNotBlank()) {

                    val query =
                        authorityAndQuery
                            .substringAfter(
                                '?',
                                ""
                            )

                    val params =
                        parseQuery(query)

                    val path =
                        params["path"]
                            ?.let {
                                decodeValue(it)
                            }
                            ?.ifBlank {
                                "/"
                            }
                            ?: "/"

                    val security =
                        params["security"]
                            ?.let {
                                decodeValue(it)
                                    .lowercase()
                            }
                            ?: ""

                    val tls =
                        security == "tls" ||
                            security == "reality" ||
                            basic.port == 443 ||
                            basic.port == 8443 ||
                            basic.port == 2096

                    return XhttpEndpoint(
                        host = basic.host,
                        port = basic.port,
                        path = path,
                        tls = tls
                    )
                }
            }

        } catch (t: Throwable) {

            Log.w(
                TAG,
                "XHTTP fallback parse failed",
                t
            )
        }

        return parseFallbackEndpoint(
            fallbackIp
        )
    }

    private fun parseQuery(
        query: String
    ): Map<String, String> {

        if (query.isBlank()) {
            return emptyMap()
        }

        return query
            .split('&')
            .mapNotNull { item ->

                val index =
                    item.indexOf('=')

                if (index <= 0) {
                    null
                } else {

                    val key =
                        item.substring(
                            0,
                            index
                        )

                    val value =
                        item.substring(
                            index + 1
                        )

                    key to value
                }
            }
            .toMap()
    }

    private fun decodeValue(
        value: String
    ): String {

        return runCatching {

            URLDecoder.decode(
                value,
                "UTF-8"
            )

        }.getOrDefault(value)
    }

    private fun parseFallbackEndpoint(
        fallbackIp: String
    ): XhttpEndpoint {

        val value =
            fallbackIp.trim()

        if (value.isBlank()) {

            return XhttpEndpoint(
                host = "",
                port = DEFAULT_XHTTP_PORT,
                path = "/",
                tls = true
            )
        }

        val endpoint =
            parseHostPort(
                value,
                DEFAULT_XHTTP_PORT
            )

        return XhttpEndpoint(
            host = endpoint.host,
            port = endpoint.port,
            path = "/",
            tls =
                endpoint.port == 443 ||
                    endpoint.port == 8443 ||
                    endpoint.port == 2096
        )
    }

    private fun parseHostPort(
        value: String,
        defaultPort: Int
    ): BasicEndpoint {

        val input =
            value.trim()

        if (input.isBlank()) {

            return BasicEndpoint(
                "",
                defaultPort
            )
        }

        /*
         * IPv6:
         * [2001:db8::1]:443
         */
        if (input.startsWith("[")) {

            val end =
                input.indexOf(']')

            if (end > 0) {

                val host =
                    input.substring(
                        1,
                        end
                    ).trim()

                val port =
                    input.substring(
                        end + 1
                    )
                        .removePrefix(":")
                        .trim()
                        .toIntOrNull()
                        ?.takeIf {
                            it in 1..65535
                        }
                        ?: defaultPort

                return BasicEndpoint(
                    host,
                    port
                )
            }
        }

        val separator =
            input.lastIndexOf(':')

        /*
         * host:port
         */
        if (
            separator > 0 &&
            input.indexOf(':') == separator
        ) {

            val host =
                input.substring(
                    0,
                    separator
                ).trim()

            val port =
                input.substring(
                    separator + 1
                )
                    .trim()
                    .toIntOrNull()
                    ?.takeIf {
                        it in 1..65535
                    }
                    ?: defaultPort

            return BasicEndpoint(
                host,
                port
            )
        }

        /*
         * Host without port.
         */
        return BasicEndpoint(
            input,
            defaultPort
        )
    }

    private fun getSshTcpPing(
        config: String,
        fallbackIp: String
    ): Long {

        val rawAddress =
            decryptOrRaw(config)
                .ifBlank {
                    fallbackIp
                }

        val endpoint =
            parseSshEndpoint(
                rawAddress
            )

        if (endpoint.host.isBlank()) {
            return PING_NOT_AVAILABLE
        }

        return try {

            val start =
                System.nanoTime()

            Socket().use { socket ->

                socket.connect(
                    InetSocketAddress(
                        endpoint.host,
                        endpoint.port
                    ),
                    SSH_PING_TIMEOUT_MS
                )
            }

            (
                (System.nanoTime() - start) /
                    1_000_000L
                ).coerceAtLeast(1L)

        } catch (t: Throwable) {

            Log.e(
                TAG,
                "SSH TCP ping failed " +
                    "host=${endpoint.host} " +
                    "port=${endpoint.port}",
                t
            )

            PING_NOT_AVAILABLE
        }
    }

    private fun parseSshEndpoint(
        rawAddress: String
    ): SshEndpoint {

        val value =
            rawAddress.trim()

        if (
            value.isEmpty() ||
            value.equals(
                "N/A",
                ignoreCase = true
            )
        ) {

            return SshEndpoint(
                "",
                DEFAULT_SSH_PORT
            )
        }

        runCatching {

            if (
                value.startsWith(
                    "ssh://",
                    ignoreCase = true
                )
            ) {

                val uri =
                    URI(value)

                val host =
                    uri.host
                        .orEmpty()
                        .trim()

                val port =
                    if (uri.port > 0) {
                        uri.port
                    } else {
                        DEFAULT_SSH_PORT
                    }

                return SshEndpoint(
                    host,
                    port
                )
            }
        }

        val address =
            value
                .removePrefix("SSH://")
                .removePrefix("ssh://")
                .trim()

        if (address.startsWith("[")) {

            val end =
                address.indexOf(']')

            if (end > 0) {

                val host =
                    address.substring(
                        1,
                        end
                    ).trim()

                val port =
                    address
                        .substring(end + 1)
                        .removePrefix(":")
                        .trim()
                        .toIntOrNull()
                        ?.takeIf {
                            it in 1..65535
                        }
                        ?: DEFAULT_SSH_PORT

                return SshEndpoint(
                    host,
                    port
                )
            }
        }

        val separator =
            address.lastIndexOf(':')

        return if (
            separator > 0 &&
            address.indexOf(':') == separator
        ) {

            val host =
                address.substring(
                    0,
                    separator
                ).trim()

            val port =
                address.substring(
                    separator + 1
                )
                    .trim()
                    .toIntOrNull()
                    ?.takeIf {
                        it in 1..65535
                    }
                    ?: DEFAULT_SSH_PORT

            SshEndpoint(
                host,
                port
            )

        } else {

            SshEndpoint(
                address,
                DEFAULT_SSH_PORT
            )
        }
    }

    private fun decryptOrRaw(
        value: String
    ): String {

        return try {

            Crypto.decryptThis(value)

        } catch (_: Throwable) {

            value
        }.trim()
    }

    private data class BasicEndpoint(
        val host: String,
        val port: Int
    )

    private data class SshEndpoint(
        val host: String,
        val port: Int
    )

    private data class XhttpEndpoint(
        val host: String,
        val port: Int,
        val path: String,
        val tls: Boolean
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

        private const val XHTTP_CONNECT_TIMEOUT_MS =
            5000

        private const val XHTTP_READ_TIMEOUT_MS =
            5000

        private const val PING_NOT_AVAILABLE =
            -1L
    }
}
```
