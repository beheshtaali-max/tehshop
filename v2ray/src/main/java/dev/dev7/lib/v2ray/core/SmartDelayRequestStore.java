package dev.dev7.lib.v2ray.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.dev7.lib.v2ray.model.RealDelayTestConfig;

/**
 * Keeps large configs out of Intent extras. The VPN service runs in the same app process,
 * so only a short request id needs to cross the Service boundary.
 */
public final class SmartDelayRequestStore {
    private SmartDelayRequestStore() {}

    public static final class Request {
        public final ArrayList<RealDelayTestConfig> configs;
        public final int maxConcurrency;
        public final AtomicBoolean cancelled = new AtomicBoolean(false);

        Request(List<RealDelayTestConfig> configs, int maxConcurrency) {
            this.configs = new ArrayList<>(configs);
            this.maxConcurrency = Math.max(1, maxConcurrency);
        }
    }

    private static final ConcurrentHashMap<String, Request> REQUESTS = new ConcurrentHashMap<>();

    public static void put(String requestId, List<RealDelayTestConfig> configs, int maxConcurrency) {
        REQUESTS.put(requestId, new Request(configs, maxConcurrency));
    }

    public static Request get(String requestId) {
        return requestId == null ? null : REQUESTS.get(requestId);
    }

    public static void cancel(String requestId) {
        Request request = get(requestId);
        if (request != null) request.cancelled.set(true);
    }

    public static void remove(String requestId) {
        if (requestId != null) REQUESTS.remove(requestId);
    }
}
