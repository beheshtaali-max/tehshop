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

    val organizedServers =
        serverDataStore.organizedServersFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val selectedProtocol =
        serverDataStore.selectedProtocolFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val activeServer =
        serverDataStore.selectedServerFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val pingCache =
        mutableMapOf<String, Long>()

    suspend fun activateAndSelectServer(
        server: Server
    ) {

        val currentList =
            serverDataStore.serversFlow.first()

        val updatedList =
            currentList.map {

                it.copy(
                    isActive =
                        it.serverid == server.serverid
                )
            }

        serverDataStore.saveServers(
            updatedList
        )

        serverDataStore.selectServer(
            server
        )
    }

    suspend fun getRealPing(
        serverId: String,
        config: String,
        protocol: ProtocolType,
        fallbackIp: String = ""
    ): Long {

        val rawConfig =
            decryptOrRaw(config)

        val normalizedConfig =
            normalizeConfig(rawConfig)

        val xhttp =
            parseXhttpConfig(
                rawConfig = normalizedConfig,
                fallbackIp = fallbackIp
            )

        val isXhttp =
            isXhttpConfig(normalizedConfig) ||
                (
                    xhttp.host.isNotBlank() &&
                        xhttp.isXhttp
                    )

        val cacheKey =
            buildString {

                append(protocol.name)
                append(":")
                append(serverId)
                append(":")
                append(
                    if (isXhttp) {
                        "xhttp"
                    } else {
                        "normal"
                    }
                )
            }

        pingCache[cacheKey]?.let { cached ->

            if (cached > 0) {
                return cached
            }
        }

        val ping =
            withContext(Dispatchers.IO) {

                when {

                    isXhttp -> {

                        getXhttpPing(
                            xhttp
                        )
                    }

                    protocol ==
                        ProtocolType.V2RAY -> {

                        getV2rayPing(
                            rawConfig
                        )
                    }

                    protocol ==
                        ProtocolType.SSH -> {

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

            pingCache[cacheKey] =
                ping
        }

        return ping
    }

    /**
     * Diagnostic ping used by the UI.
     *
     * Examples:
     *
     * XHTTP 145ms
     * XHTTP TCP 80ms • TLS 142ms
     * XHTTP TCP FAILED
     * XHTTP HOST EMPTY
     * V2RAY 120ms
     */
    suspend fun getPingDebug(
        serverId: String,
        config: String,
        protocol: ProtocolType,
        fallbackIp: String = ""
    ): String {

        return withContext(Dispatchers.IO) {

            try {

                val rawConfig =
                    decryptOrRaw(config)

                val normalizedConfig =
                    normalizeConfig(
                        rawConfig
                    )

                Log.e(
                    TAG,
                    "PING_DEBUG START " +
                        "server=$serverId " +
                        "protocol=$protocol"
                )

                Log.e(
                    TAG,
                    "PING_DEBUG CONFIG=$normalizedConfig"
                )

                val detectedXhttp =
                    isXhttpConfig(
                        normalizedConfig
                    )

                Log.e(
                    TAG,
                    "PING_DEBUG XHTTP_DETECTED=$detectedXhttp"
                )

                /*
                 * Normal V2Ray
                 */
                if (!detectedXhttp) {

                    if (
                        protocol ==
                            ProtocolType.V2RAY
                    ) {

                        val ping =
                            getV2rayPing(
                                rawConfig
                            )

                        return@withContext if (
                            ping > 0
                        ) {

                            "V2RAY ${ping}ms"

                        } else {

                            "V2RAY FAILED"
                        }
                    }

                    return@withContext "NOT XHTTP"
                }

                /*
                 * Parse XHTTP endpoint.
                 */
                val endpoint =
                    parseXhttpConfig(
                        rawConfig =
                            normalizedConfig,
                        fallbackIp =
                            fallbackIp
                    )

                Log.e(
                    TAG,
                    "PING_DEBUG ENDPOINT " +
                        "host=${endpoint.host} " +
                        "port=${endpoint.port} " +
                        "path=${endpoint.path} " +
                        "tls=${endpoint.tls} " +
                        "sni=${endpoint.sni} " +
                        "alpn=${endpoint.alpn}"
                )

                if (
                    endpoint.host.isBlank()
                ) {

                    return@withContext (
                        "XHTTP HOST EMPTY"
                    )
                }

                val start =
                    System.nanoTime()

                var socket: Socket? =
                    null

                try {

                    /*
                     * ========================
                     * TCP
                     * ========================
                     */

                    socket =
                        Socket()

                    socket.connect(
                        InetSocketAddress(
                            endpoint.host,
                            endpoint.port
                        ),
                        XHTTP_CONNECT_TIMEOUT_MS
                    )

                    val tcpMs =
                        elapsedMs(
                            start
                        )

                    Log.e(
                        TAG,
                        "PING_DEBUG TCP_OK " +
                            "${tcpMs}ms"
                    )

                    /*
                     * ========================
                     * NON TLS
                     * ========================
                     */

                    if (!endpoint.tls) {

                        return@withContext (
                            "XHTTP TCP ${tcpMs}ms"
                        )
                    }

                    /*
                     * ========================
                     * TLS
                     * ========================
                     */

                    val sslSocket =
                        createTlsSocket(
                            socket,
                            endpoint
                        )

                    socket =
                        sslSocket

                    sslSocket.soTimeout =
                        XHTTP_READ_TIMEOUT_MS

                    /*
                     * ALPN
                     */
                    try {

                        val protocols =
                            endpoint.alpn
                                .split(",")
                                .map {
                                    it.trim()
                                }
                                .filter {
                                    it.isNotBlank()
                                }

                        if (
                            protocols.isNotEmpty()
                        ) {

                            val parameters =
                                sslSocket.sslParameters

                            parameters.applicationProtocols =
                                protocols.toTypedArray()

                            sslSocket.sslParameters =
                                parameters
                        }

                    } catch (t: Throwable) {

                        Log.w(
                            TAG,
                            "ALPN setup failed",
                            t
                        )
                    }

                    try {

                        sslSocket.startHandshake()

                    } catch (
                        e: Throwable
                    ) {

                        Log.e(
                            TAG,
                            "PING_DEBUG TLS_FAILED",
                            e
                        )

                        return@withContext (
                            "XHTTP TCP ${tcpMs}ms • TLS FAILED"
                        )
                    }

                    val tlsMs =
                        elapsedMs(
                            start
                        )

                    Log.e(
                        TAG,
                        "PING_DEBUG TLS_OK " +
                            "${tlsMs}ms"
                    )

                    /*
                     * ========================
                     * HTTP diagnostic
                     * ========================
                     *
                     * This is intentionally a
                     * lightweight HTTP request.
                     *
                     * It checks whether the TLS
                     * endpoint answers.
                     *
                     * It is NOT pretending to be
                     * a full XHTTP packet-up
                     * implementation.
                     */

                    val request =
                        buildHttpRequest(
                            endpoint
                        )

                    try {

                        val output =
                            sslSocket
                                .getOutputStream()

                        output.write(
                            request.toByteArray(
                                Charsets.UTF_8
                            )
                        )

                        output.flush()

                    } catch (
                        e: Throwable
                    ) {

                        Log.e(
                            TAG,
                            "PING_DEBUG HTTP_SEND_FAILED",
                            e
                        )

                        return@withContext (
                            "XHTTP TCP ${tcpMs}ms • TLS ${tlsMs}ms"
                        )
                    }

                    /*
                     * Read only the first HTTP line.
                     */
                    try {

                        val reader =
                            BufferedReader(
                                InputStreamReader(
                                    sslSocket
                                        .getInputStream(),
                                    Charsets.ISO_8859_1
                                )
                            )

                        val response =
                            reader.readLine()

                        Log.e(
                            TAG,
                            "PING_DEBUG HTTP_RESPONSE=$response"
                        )

                        val totalMs =
                            elapsedMs(
                                start
                            )

                        if (
                            response != null &&
                                response.startsWith(
                                    "HTTP/",
                                    ignoreCase = true
                                )
                        ) {

                            return@withContext (
                                "XHTTP ${totalMs}ms"
                            )
                        }

                        return@withContext (
                            "XHTTP TCP ${tcpMs}ms • TLS ${tlsMs}ms"
                        )

                    } catch (
                        e: Throwable
                    ) {

                        Log.e(
                            TAG,
                            "PING_DEBUG HTTP_READ_FAILED",
                            e
                        )

                        return@withContext (
                            "XHTTP TCP ${tcpMs}ms • TLS ${tlsMs}ms"
                        )
                    }

                } catch (
                    e: Throwable
                ) {

                    Log.e(
                        TAG,
                        "PING_DEBUG TCP_FAILED",
                        e
                    )

                    return@withContext (
                        "XHTTP TCP FAILED"
                    )

                } finally {

                    try {
                        socket?.close()
                    } catch (_: Throwable) {
                    }
                }

            } catch (
                e: Throwable
            ) {

                Log.e(
                    TAG,
                    "PING_DEBUG ERROR",
                    e
                )

                return@withContext (
                    "XHTTP ERROR"
                )
            }
        }
    }

    suspend fun saveSelectedProtocol(
        protocol: ProtocolType
    ) {

        serverDataStore.saveSelectedProtocol(
            protocol
        )
    }

    private fun getV2rayPing(
        config: String
    ): Long {

        return try {

            V2rayController
                .getV2rayServerDelay(
                    Crypto.decryptThis(
                        config
                    )
                )

        } catch (
            t: Throwable
        ) {

            Log.e(
                TAG,
                "V2Ray ping failed",
                t
            )

            PING_NOT_AVAILABLE
        }
    }

    private fun getXhttpPing(
        endpoint: XhttpEndpoint
    ): Long {

        if (
            endpoint.host.isBlank()
        ) {

            return PING_NOT_AVAILABLE
        }

        val start =
            System.nanoTime()

        var socket: Socket? =
            null

        try {

            val tcpSocket =
                Socket()

            socket =
                tcpSocket

            tcpSocket.connect(
                InetSocketAddress(
                    endpoint.host,
                    endpoint.port
                ),
                XHTTP_CONNECT_TIMEOUT_MS
            )

            if (!endpoint.tls) {

                return elapsedMs(
                    start
                )
            }

            val sslSocket =
                createTlsSocket(
                    tcpSocket,
                    endpoint
                )

            socket =
                sslSocket

            sslSocket.soTimeout =
                XHTTP_READ_TIMEOUT_MS

            /*
             * ALPN
             */
            try {

                val protocols =
                    endpoint.alpn
                        .split(",")
                        .map {
                            it.trim()
                        }
                        .filter {
                            it.isNotBlank()
                        }

                if (
                    protocols.isNotEmpty()
                ) {

                    val parameters =
                        sslSocket.sslParameters

                    parameters.applicationProtocols =
                        protocols.toTypedArray()

                    sslSocket.sslParameters =
                        parameters
                }

            } catch (_: Throwable) {
            }

            sslSocket.startHandshake()

            return elapsedMs(
                start
            )

        } catch (
            t: Throwable
        ) {

            Log.e(
                TAG,
                "XHTTP ping failed " +
                    "host=${endpoint.host} " +
                    "port=${endpoint.port}",
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

    private fun createTlsSocket(
        tcpSocket: Socket,
        endpoint: XhttpEndpoint
    ): SSLSocket {

        val context =
            SSLContext.getInstance(
                "TLS"
            )

        context.init(
            null,
            null,
            null
        )

        val factory =
            context.socketFactory
                as SSLSocketFactory

        return factory.createSocket(
            tcpSocket,
            endpoint.sni.ifBlank {
                endpoint.host
            },
            endpoint.port,
            true
        ) as SSLSocket
    }

    private fun buildHttpRequest(
        endpoint: XhttpEndpoint
    ): String {

        val path =
            endpoint.path
                .ifBlank {
                    "/"
                }
                .let {

                    if (
                        it.startsWith("/")
                    ) {
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

            if (
                endpoint.port != 443
            ) {

                append(":")
                append(endpoint.port)
            }

            append("\r\n")

            append(
                "User-Agent: Mozilla/5.0\r\n"
            )

            append(
                "Accept: */*\r\n"
            )

            append(
                "Connection: close\r\n"
            )

            append("\r\n")
        }
    }

    private fun parseXhttpConfig(
        rawConfig: String,
        fallbackIp: String
    ): XhttpEndpoint {

        val value =
            normalizeConfig(
                rawConfig
            )

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
         * Find VLESS/VMess anywhere in the
         * config instead of requiring it at
         * position zero.
         */
        val vlessIndex =
            value.indexOf(
                "vless://",
                ignoreCase = true
            )

        val vmessIndex =
            value.indexOf(
                "vmess://",
                ignoreCase = true
            )

        val protocolIndex =
            listOf(
                vlessIndex,
                vmessIndex
            )
                .filter {
                    it >= 0
                }
                .minOrNull()

        if (
            protocolIndex != null
        ) {

            try {

                var uriValue =
                    value.substring(
                        protocolIndex
                    )

                /*
                 * Remove possible JSON/string
                 * delimiters after the URI.
                 */
                uriValue =
                    uriValue
                        .substringBefore("\"")
                        .substringBefore("'")
                        .substringBefore("}")
                        .substringBefore("\n")
                        .trim()

                val uri =
                    URI(
                        uriValue
                    )

                val host =
                    uri.host
                        .orEmpty()
                        .trim()

                if (
                    host.isNotBlank()
                ) {

                    val port =
                        if (
                            uri.port > 0
                        ) {
                            uri.port
                        } else {
                            DEFAULT_XHTTP_PORT
                        }

                    val params =
                        parseQuery(
                            uri.rawQuery
                                .orEmpty()
                        )

                    val decodedParams =
                        params.mapValues {
                            (_, v) ->
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
                            ?.lowercase(
                                Locale.US
                            )
                            ?: ""

                    val mode =
                        decodedParams["mode"]
                            ?.lowercase(
                                Locale.US
                            )
                            ?: ""

                    val type =
                        decodedParams["type"]
                            ?.lowercase(
                                Locale.US
                            )
                            ?: ""

                    val alpn =
                        decodedParams["alpn"]
                            ?.let {
                                decodeValue(it)
                            }
                            .orEmpty()

                    val sni =
                        decodedParams["sni"]
                            ?.ifBlank {
                                host
                            }
                            ?: host

                    val isXhttp =
                        type == "xhttp" ||
                            mode.contains(
                                "packet-up"
                            ) ||
                            mode.contains(
                                "stream-up"
                            ) ||
                            value.contains(
                                "packet-up",
                                ignoreCase = true
                            ) ||
                            value.contains(
                                "packet-up-base64",
                                ignoreCase = true
                            ) ||
                            value.contains(
                                "/api/v1/sync",
                                ignoreCase = true
                            )

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
                        tls = tls,
                        sni = sni,
                        alpn = alpn,
                        isXhttp = isXhttp
                    )
                }

            } catch (
                t: Throwable
            ) {

                Log.w(
                    TAG,
                    "XHTTP URI parse failed",
                    t
                )
            }
        }

        /*
         * Generic @host:port parser.
         */
        try {

            val atIndex =
                value.lastIndexOf("@")

            if (
                atIndex >= 0 &&
                    atIndex + 1 <
                    value.length
            ) {

                val authorityAndQuery =
                    value.substring(
                        atIndex + 1
                    )

                val authority =
                    authorityAndQuery
                        .substringBefore("?")
                        .substringBefore("#")
                        .substringBefore("\"")
                        .trim()

                val basic =
                    parseHostPort(
                        authority,
                        DEFAULT_XHTTP_PORT
                    )

                if (
                    basic.host.isNotBlank()
                ) {

                    val query =
                        authorityAndQuery
                            .substringAfter(
                                "?",
                                ""
                            )

                    val params =
                        parseQuery(
                            query
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
                                    .lowercase(
                                        Locale.US
                                    )
                            }
                            ?: ""

                    val mode =
                        params["mode"]
                            ?.let {
                                decodeValue(it)
                                    .lowercase(
                                        Locale.US
                                    )
                            }
                            ?: ""

                    val type =
                        params["type"]
                            ?.let {
                                decodeValue(it)
                                    .lowercase(
                                        Locale.US
                                    )
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
                            mode.contains(
                                "packet-up"
                            ) ||
                            mode.contains(
                                "stream-up"
                            ) ||
                            value.contains(
                                "packet-up",
                                ignoreCase = true
                            ) ||
                            value.contains(
                                "/api/v1/sync",
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

        } catch (
            t: Throwable
        ) {

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

        if (
            query.isBlank()
        ) {
            return emptyMap()
        }

        return query
            .split("&")
            .mapNotNull { item ->

                val index =
                    item.indexOf("=")

                if (
                    index <= 0
                ) {
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

        }.getOrDefault(
            value
        )
    }

    private fun normalizeConfig(
        input: String
    ): String {

        var value =
            input.trim()

        /*
         * Decode nested URL encoding.
         */
        repeat(3) {

            val decoded =
                runCatching {

                    URLDecoder.decode(
                        value,
                        "UTF-8"
                    )

                }.getOrNull()

            if (
                decoded.isNullOrBlank() ||
                    decoded == value
            ) {

                return@repeat
            }

            value =
                decoded
        }

        /*
         * Common escaped JSON characters.
         */
        value =
            value
                .replace(
                    "\\/",
                    "/"
                )
                .replace(
                    "\\\"",
                    "\""
                )
                .replace(
                    "\\u0026",
                    "&",
                    ignoreCase = true
                )
                .replace(
                    "\\u003d",
                    "=",
                    ignoreCase = true
                )

        return value
    }

    private fun isXhttpConfig(
        config: String
    ): Boolean {

        val value =
            normalizeConfig(
                config
            ).lowercase(
                Locale.US
            )

        /*
         * Explicit XHTTP type.
         */
        if (
            value.contains(
                "type=xhttp"
            ) ||
            value.contains(
                "\"type\":\"xhttp\""
            ) ||
            value.contains(
                "\"type\": \"xhttp\""
            )
        ) {

            return true
        }

        /*
         * XHTTP packet-up modes.
         */
        if (
            value.contains(
                "mode=packet-up"
            ) ||
            value.contains(
                "mode=packet-up-base64"
            ) ||
            value.contains(
                "mode=stream-up"
            ) ||
            value.contains(
                "\"mode\":\"packet-up\""
            ) ||
            value.contains(
                "\"mode\": \"packet-up\""
            ) ||
            value.contains(
                "\"mode\":\"packet-up-base64\""
            ) ||
            value.contains(
                "\"mode\": \"packet-up-base64\""
            )
        ) {

            return true
        }

        /*
         * Additional XHTTP identifiers.
         */
        if (
            value.contains(
                "packet-up"
            ) ||
            value.contains(
                "packet_up"
            )
        ) {

            return true
        }

        /*
         * Typical XHTTP sync endpoint.
         */
        if (
            value.contains(
                "/api/v1/sync"
            ) &&
            (
                value.contains(
                    "vless://"
                ) ||
                    value.contains(
                        "vmess://"
                    ) ||
                    value.contains(
                        "\"protocol\""
                    ) ||
                    value.contains(
                        "\"streamsettings\""
                    )
                )
        ) {

            return true
        }

        /*
         * Decode once more in case the config
         * still contains encoded separators.
         */
        val decoded =
            runCatching {

                URLDecoder.decode(
                    value,
                    "UTF-8"
                )

            }.getOrDefault(
                value
            )

        return decoded.contains(
            "packet-up"
        )
    }

    private fun parseFallbackEndpoint(
        fallbackIp: String
    ): XhttpEndpoint {

        val value =
            fallbackIp.trim()

        if (
            value.isBlank()
        ) {

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

        if (
            input.isBlank()
        ) {

            return BasicEndpoint(
                "",
                defaultPort
            )
        }

        if (
            input.startsWith("[")
        ) {

            val end =
                input.indexOf("]")

            if (
                end > 0
            ) {

                val host =
                    input.substring(
                        1,
                        end
                    ).trim()

                val port =
                    input
                        .substring(
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
            input.lastIndexOf(":")

        if (
            separator > 0 &&
                input.indexOf(":") ==
                separator
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
            decryptOrRaw(
                config
            ).ifBlank {
                fallbackIp
            }

        val endpoint =
            parseSshEndpoint(
                rawAddress
            )

        if (
            endpoint.host.isBlank()
        ) {

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

            elapsedMs(
                start
            )

        } catch (
            t: Throwable
        ) {

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
                    URI(
                        value
                    )

                val host =
                    uri.host
                        .orEmpty()
                        .trim()

                val port =
                    if (
                        uri.port > 0
                    ) {
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
                .removePrefix(
                    "SSH://"
                )
                .removePrefix(
                    "ssh://"
                )
                .trim()

        if (
            address.startsWith("[")
        ) {

            val end =
                address.indexOf("]")

            if (
                end > 0
            ) {

                val host =
                    address.substring(
                        1,
                        end
                    ).trim()

                val port =
                    address
                        .substring(
                            end + 1
                        )
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
            address.lastIndexOf(":")

        return if (
            separator > 0 &&
                address.indexOf(":") ==
                separator
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

            Crypto.decryptThis(
                value
            )

        } catch (_: Throwable) {

            value

        }.trim()
    }

    private fun elapsedMs(
        start: Long
    ): Long {

        return (
            (
                System.nanoTime() -
                    start
            ) / 1_000_000L
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

