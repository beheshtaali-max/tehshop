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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

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

        val xhttp = parseXhttpConfig(
            rawConfig = rawConfig,
            fallbackIp = fallbackIp
        )

        val isXhttp =
            xhttp.host.isNotBlank() &&
                (
                    xhttp.isXhttp ||
                    rawConfig.contains(
                        "packet-up",
                        ignoreCase = true
                    ) ||
                    rawConfig.contains(
                        "packet-up-base64",
                        ignoreCase = true
                    )
                )

        val cacheKey =
            "${protocol.name}:$serverId:${if (isXhttp) "xhttp" else "normal"}"

        pingCache[cacheKey]?.let { cached ->
            if (cached > 0) {
                return cached
            }
        }

        val ping = withContext(Dispatchers.IO) {

            when {
                isXhttp -> {
                    getXhttpPing(xhttp)
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
     * XHTTP latency:
     *
     * 1. DNS
     * 2. TCP connect
     * 3. TLS handshake
     * 4. HTTP request
     *
     * The TLS socket uses the server hostname as SNI.
     */
    private fun getXhttpPing(
        endpoint: XhttpEndpoint
    ): Long {

        if (endpoint.host.isBlank()) {
            return PING_NOT_AVAILABLE
        }

        val start =
            System.nanoTime()

        var socket: Socket? = null

        try {

            val tcpSocket =
                Socket()

            socket = tcpSocket

            tcpSocket.connect(
                InetSocketAddress(
                    endpoint.host,
                    endpoint.port
                ),
                XHTTP_CONNECT_TIMEOUT_MS
            )

            val tcpConnectedAt =
                System.nanoTime()

            Log.d(
                TAG,
                "XHTTP TCP connected " +
                    "${endpoint.host}:${endpoint.port} " +
                    "time=${elapsedMs(start)}ms"
            )

            if (!endpoint.tls) {

                val request =
                    buildHttpRequest(
                        endpoint
                    )

                tcpSocket.soTimeout =
                    XHTTP_READ_TIMEOUT_MS

                tcpSocket.getOutputStream()
                    .use { output ->
                        output.write(
                            request.toByteArray(
                                Charsets.UTF_8
                            )
                        )
                        output.flush()
                    }

                val responseTime =
                    waitForHttpResponse(
                        tcpSocket
                    )

                return if (responseTime > 0) {
                    responseTime
                } else {
                    elapsedMs(start)
                }
            }

            val sslSocket =
                createTlsSocket(
                    tcpSocket,
                    endpoint
                )

            socket = sslSocket

            sslSocket.soTimeout =
                XHTTP_READ_TIMEOUT_MS

            sslSocket.startHandshake()

            val tlsFinishedAt =
                System.nanoTime()

            Log.d(
                TAG,
                "XHTTP TLS handshake completed " +
                    "host=${endpoint.host} " +
                    "tls=${elapsedMs(start)}ms"
            )

            val request =
                buildHttpRequest(
                    endpoint
                )

            sslSocket.getOutputStream()
                .use { output ->
                    output.write(
                        request.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    output.flush()
                }

            val responseTime =
                waitForHttpResponse(
                    sslSocket
                )

            val total =
                if (responseTime > 0) {
                    responseTime
                } else {
                    (
                        (System.nanoTime() - start) /
                            1_000_000L
                    ).coerceAtLeast(1L)
                }

            Log.d(
                TAG,
                "XHTTP result " +
                    "tcp=${nanosToMs(tcpConnectedAt - start)}ms " +
                    "tls=${nanosToMs(tlsFinishedAt - start)}ms " +
                    "total=${total}ms"
            )

            return total

        } catch (t: Throwable) {

            Log.e(
                TAG,
                "XHTTP ping failed " +
                    "host=${endpoint.host} " +
                    "port=${endpoint.port} " +
                    "path=${endpoint.path}",
                t
            )

            return PING_NOT_AVAILABLE

        } finally {

            try {
                socket?.close()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Creates an SSL socket and enables SNI.
     */
    private fun createTlsSocket(
        tcpSocket: Socket,
        endpoint: XhttpEndpoint
    ): SSLSocket {

        val context =
            SSLContext.getInstance("TLS")

        context.init(
            null,
            null,
            null
        )

        val factory =
            context.socketFactory as SSLSocketFactory

        val sslSocket =
            factory.createSocket(
                tcpSocket,
                endpoint.host,
                endpoint.port,
                true
            ) as SSLSocket

        /*
         * SNI is set automatically by modern Android
         * when the hostname is supplied to createSocket.
         *
         * ALPN is intentionally not forced here because
         * Android API availability varies between versions.
         */
        return sslSocket
    }

    /**
     * Minimal HTTP request to verify that the TLS/HTTP
     * endpoint actually responds.
     */
    private fun buildHttpRequest(
        endpoint: XhttpEndpoint
    ): String {

        val path =
            endpoint.path
                .ifBlank {
                    "/"
                }
                .let {
                    if (it.startsWith("/")) {
                        it
                    } else {
                        "/$it"
                    }
                }

        return buildString {

            append("GET ")
            append(path)
            append(" HTTP/1.1\r\n")

            append("Host: ")
            append(endpoint.host)

            if (endpoint.port != 443) {
                append(":")
                append(endpoint.port)
            }

            append("\r\n")

            append("User-Agent: Mozilla/5.0\r\n")
            append("Accept: */*\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
    }

    /**
     * Reads only the beginning of the HTTP response.
     *
     * We don't need the whole response. Receiving the
     * status line is enough to prove that the endpoint
     * answered.
     */
    private fun waitForHttpResponse(
        socket: Socket
    ): Long {

        val start =
            System.nanoTime()

        return try {

            val reader =
                BufferedReader(
                    InputStreamReader(
                        socket.getInputStream(),
                        Charsets.ISO_8859_1
                    )
                )

            val firstLine =
                reader.readLine()

            val elapsed =
                (
                    (System.nanoTime() - start) /
                        1_000_000L
                    ).coerceAtLeast(1L)

            Log.d(
                TAG,
                "XHTTP HTTP response=$firstLine"
            )

            if (
                firstLine != null &&
                firstLine.startsWith(
                    "HTTP/",
                    ignoreCase = true
                )
            ) {
                elapsed
            } else {
                PING_NOT_AVAILABLE
            }

        } catch (t: Throwable) {

            Log.e(
                TAG,
                "XHTTP response read failed",
                t
            )

            PING_NOT_AVAILABLE
        }
    }

    /**
     * Detect and parse XHTTP URL.
     *
     * Supports:
     *
     * vless://UUID@host:2096?
     * mode=packet-up
     * &path=/api/v1/sync
     * &security=tls
     * &alpn=h2,http/1.1
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
         * Decode only enough to detect URL parameters.
         * We don't decode the complete URL because '+'
         * has special meaning in query strings.
         */
        val decoded =
            decodeUrlSafely(value)

        val isVlessOrVmess =
            value.startsWith(
                "vless://",
                ignoreCase = true
            ) ||
                value.startsWith(
                    "vmess://",
                    ignoreCase = true
                )

        if (isVlessOrVmess) {

            try {

                val uri =
                    URI(value)

                val host =
                    uri.host
                        .orEmpty()
                        .trim()

                if (host.isNotBlank()) {

                    val port =
                        if (uri.port > 0) {
                            uri.port
                        } else {
                            DEFAULT_XHTTP_PORT
                        }

                    val params =
                        parseQuery(
                            uri.rawQuery.orEmpty()
                        )

                    val decodedParams =
                        params.mapValues { (_, v) ->
                            decodeValue(v)
                        }

                    val path =
                        decodedParams["path"]
                            ?.ifBlank {
                                "/"
                            }
                            ?: "/"

                    val security =
                        decodedParams["security"]
                            ?.lowercase(Locale.US)
                            ?: ""

                    val mode =
                        decodedParams["mode"]
                            ?.lowercase(Locale.US)
                            ?: ""

                    val type =
                        decodedParams["type"]
                            ?.lowercase(Locale.US)
                            ?: ""

                    val alpn =
                        decodedParams["alpn"]
                            .orEmpty()

                    val sni =
                        decodedParams["sni"]
                            ?.ifBlank {
                                host
                            }
                            ?: host

                    val isXhttp =
                        type == "xhttp" ||
                            mode.contains("packet-up") ||
                            decoded.contains(
                                "packet-up",
                                ignoreCase = true
                            )

                    val tls =
                        security == "tls" ||
                            security == "reality" ||
                            port == 443 ||
                            port == 8443 ||
                            port == 2096

                    Log.d(
                        TAG,
                        "Parsed XHTTP " +
                            "host=$host " +
                            "port=$port " +
                            "path=$path " +
                            "mode=$mode " +
                            "security=$security " +
                            "sni=$sni " +
                            "alpn=$alpn " +
                            "isXhttp=$isXhttp"
                    )

                    return XhttpEndpoint(
                        host = host,
                        port = port,
                        path = path,
                        tls = tls,
                        sni = sni,
                        alpn = alpn,
                        isXhttp = isXhttp
                    )
                }

            } catch (t: Throwable) {

                Log.w(
                    TAG,
                    "XHTTP URI parse failed",
                    t
                )
            }
        }

        /*
         * Generic fallback:
         *
         * Anything containing @host:port
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
                                    .lowercase(Locale.US)
                            }
                            ?: ""

                    val mode =
                        params["mode"]
                            ?.let {
                                decodeValue(it)
                                    .lowercase(Locale.US)
                            }
                            ?: ""

                    val type =
                        params["type"]
                            ?.let {
                                decodeValue(it)
                                    .lowercase(Locale.US)
                            }
                            ?: ""

                    val sni =
                        params["sni"]
                            ?.let {
                                decodeValue(it)
                            }
                            ?.ifBlank {
                                basic.host
                            }
                            ?: basic.host

                    val alpn =
                        params["alpn"]
                            ?.let {
                                decodeValue(it)
                            }
                            ?: ""

                    val isXhttp =
                        type == "xhttp" ||
                            mode.contains("packet-up") ||
                            value.contains(
                                "packet-up",
                                ignoreCase = true
                            )

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
                        tls = tls,
                        sni = sni,
                        alpn = alpn,
                        isXhttp = isXhttp
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

    private fun decodeUrlSafely(
        value: String
    ): String {

        return runCatching {

            value
                .replace(
                    "%3D",
                    "=",
                    ignoreCase = true
                )
                .replace(
                    "%26",
                    "&",
                    ignoreCase = true
                )
                .replace(
                    "%2F",
                    "/",
                    ignoreCase = true
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
                tls = true,
                sni = "",
                alpn = "",
                isXhttp = false
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
                    endpoint.port == 2096,
            sni = endpoint.host,
            alpn = "",
            isXhttp = false
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

        try {

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

        } catch (_: Throwable) {
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

    private fun elapsedMs(
        start: Long
    ): Long {

        return (
            (System.nanoTime() - start) /
                1_000_000L
            ).coerceAtLeast(1L)
    }

    private fun nanosToMs(
        nanos: Long
    ): Long {

        return (
            nanos /
                1_000_000L
            ).coerceAtLeast(1L)
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
        val tls: Boolean,
        val sni: String,
        val alpn: String,
        val isXhttp: Boolean
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
