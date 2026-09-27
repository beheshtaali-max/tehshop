package dev.dev7.lib.v2ray.core;

import dev.dev7.lib.v2ray.interfaces.V2rayServicesListener;

/** Compatibility stub. sing-box TUN replaces the old tun2socks binary. */
public class Tun2SocksExecutor {
    public Tun2SocksExecutor(V2rayServicesListener ignored) {}
    public void run(V2rayServicesListener ignored, int localSocksPort, int localDNSPort) {}
    public boolean isTun2SucksRunning() { return false; }
    public void stopTun2Socks() {}
}
