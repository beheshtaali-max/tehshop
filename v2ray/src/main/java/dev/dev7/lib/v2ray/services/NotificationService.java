package dev.dev7.lib.v2ray.services;

import dev.dev7.lib.v2ray.interfaces.TrafficListener;

/** Compatibility shell. Notifications are handled directly by V2rayVPNService. */
public class NotificationService {
    public TrafficListener trafficListener = (uploadSpeed, downloadSpeed, uploadedTraffic, downloadedTraffic) -> {};
    public NotificationService(V2rayVPNService ignored) {}
    public void dismissNotification() {}
    public void setConnectedNotification(String remark, int icon) {}
}
