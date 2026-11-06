package dev.dev7.lib.v2ray.model;

import java.io.Serializable;

/** A single server/config entry used by the parallel real-delay test. */
public final class RealDelayTestConfig implements Serializable {
    public final String serverId;
    public final String config;

    public RealDelayTestConfig(String serverId, String config) {
        this.serverId = serverId == null ? "" : serverId;
        this.config = config == null ? "" : config;
    }
}
