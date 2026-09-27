package dev.dev7.lib.v2ray.core;

import java.net.InetSocketAddress;
import java.net.Socket;

public final class SingBoxLatencyTester {
    private SingBoxLatencyTester() {}

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
}
