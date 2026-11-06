package dev.dev7.lib.v2ray.core;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public final class SingBoxLatencyTester {
    private SingBoxLatencyTester() {}

    /** Legacy endpoint TCP delay, kept for existing server-list UI compatibility. */
    public static long measureConfigDelay(String config) {
        try {
            SingBoxConfigBuilder.Endpoint endpoint = SingBoxConfigBuilder.extractEndpoint(config);
            return measureTcpDelay(endpoint.host, endpoint.port, 7000);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public static long measureTcpDelay(String host, int port, int timeoutMs) {
        if (host == null || host.trim().isEmpty() || port <= 0) return -1;
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return Math.max(1, System.currentTimeMillis() - start);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * Performs a real HTTPS request through the supplied local SOCKS port. The measured value
     * includes SOCKS negotiation, proxy connection, TLS handshake and the first HTTP response.
     */
    public static long measureHttpDelayThroughSocks(
            String socksHost,
            int socksPort,
            String testUrl,
            int timeoutMs
    ) {
        if (socksHost == null || socksHost.trim().isEmpty() || socksPort <= 0) return -1;
        HttpURLConnection connection = null;
        long startedAt = System.nanoTime();
        try {
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(socksHost, socksPort));
            URL url = new URL(testUrl);
            connection = (HttpURLConnection) url.openConnection(proxy);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "TehVPN-RealDelay/1.0");
            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier());
            }

            int code = connection.getResponseCode();
            if (code < 200 || code >= 400) return -1;

            // Touch at most one byte so the measurement includes receiving the response body/header.
            try (InputStream stream = connection.getInputStream()) {
                stream.read();
            } catch (Throwable ignored) {
                // 204 responses legitimately have no body.
            }
            return Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L);
        } catch (Throwable ignored) {
            return -1;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
