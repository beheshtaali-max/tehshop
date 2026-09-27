package dev.dev7.lib.v2ray.services;

import dev.dev7.lib.v2ray.interfaces.StateListener;
import dev.dev7.lib.v2ray.interfaces.TrafficListener;

/** Compatibility shell. State broadcasts are sent directly by V2rayVPNService. */
public class StaticsBroadCastService {
    public boolean isTrafficStaticsEnabled = false;
    public TrafficListener trafficListener;
    public StaticsBroadCastService(V2rayVPNService ignored, StateListener listener) {}
    public void start() {}
    public void stop() {}
}
