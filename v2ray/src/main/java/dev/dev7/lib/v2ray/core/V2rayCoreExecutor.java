package dev.dev7.lib.v2ray.core;

import dev.dev7.lib.v2ray.interfaces.V2rayServicesListener;
import dev.dev7.lib.v2ray.model.V2rayConfigModel;
import dev.dev7.lib.v2ray.utils.V2rayConstants;

/**
 * Compatibility stub for old integrations. The Xray executor was replaced by sing-box libcore.
 * The real core is managed by V2rayVPNService + SingBoxCoreBridge.
 */
public class V2rayCoreExecutor {
    public V2rayCoreExecutor(V2rayServicesListener ignored) {}
    public void startCore(V2rayConfigModel ignored) {}
    public void stopCore(boolean ignored) {}
    public V2rayConstants.CORE_STATES getCoreState() { return V2rayConstants.CORE_STATES.IDLE; }
    public long getDownloadSpeed() { return -1; }
    public long getUploadSpeed() { return -1; }
    public void broadCastCurrentServerDelay() {}
    public static long getConfigDelay(String config) { return SingBoxLatencyTester.measureConfigDelay(config); }
}
