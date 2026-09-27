package dev.dev7.lib.v2ray.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import dev.dev7.lib.v2ray.utils.SafeLog;

/**
 * Proxy mode was intentionally removed. This class remains only so old imports/references keep compiling.
 */
public class V2rayProxyService extends Service {
    private static final String TAG = "V2rayProxyService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SafeLog.w(TAG, "Proxy mode is disabled in sing-box replacement module. Use V2rayVPNService/TUN mode.");
        stopSelf();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
