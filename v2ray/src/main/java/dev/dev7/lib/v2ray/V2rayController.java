package dev.dev7.lib.v2ray;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.content.Context.RECEIVER_EXPORTED;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_CONNECTION_STATE_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_TYPE_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_INTENT;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_CONFIG_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_STATICS_BROADCAST_INTENT;
import static dev.dev7.lib.v2ray.utils.V2rayConfigs.connectionState;
import static dev.dev7.lib.v2ray.utils.V2rayConfigs.currentConfig;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Objects;

import dev.dev7.lib.v2ray.core.SingBoxLatencyTester;
import dev.dev7.lib.v2ray.interfaces.LatencyDelayListener;
import dev.dev7.lib.v2ray.services.V2rayProxyService;
import dev.dev7.lib.v2ray.services.V2rayVPNService;
import dev.dev7.lib.v2ray.utils.SafeLog;
import dev.dev7.lib.v2ray.utils.Utilities;
import dev.dev7.lib.v2ray.utils.V2rayConfigs;
import dev.dev7.lib.v2ray.utils.V2rayConstants;

public class V2rayController {
    private static final String TAG = "V2rayController";
    private static boolean stateReceiverRegistered = false;

    static final BroadcastReceiver stateUpdaterBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                connectionState = (V2rayConstants.CONNECTION_STATES) Objects.requireNonNull(intent.getExtras()).getSerializable(SERVICE_CONNECTION_STATE_BROADCAST_EXTRA);
                V2rayConfigs.serviceMode = V2rayConstants.SERVICE_MODES.VPN_MODE;
            } catch (Exception ignore) {}
        }
    };

    public static void init(final AppCompatActivity activity, final int app_icon, final String app_name) {
        currentConfig.applicationIcon = app_icon;
        currentConfig.applicationName = app_name;
        if (activity != null) registerReceiversSafe(activity);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public static void registerReceivers(final Activity activity) {
        if (activity == null || stateReceiverRegistered) return;
        // Some legacy app code calls init(new AppCompatActivity(), ...). That Activity has no
        // attached base context, so registerReceiver would throw and spam logs. Treat it as
        // optional; connection itself does not depend on this receiver.
        try {
            if (activity.getApplicationContext() == null) {
                SafeLog.w(TAG, "registerReceivers skipped: activity has no attached context", null);
                return;
            }
        } catch (Throwable ignored) {
            SafeLog.w(TAG, "registerReceivers skipped: invalid activity context", null);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(stateUpdaterBroadcastReceiver, new IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT), RECEIVER_EXPORTED);
        } else {
            activity.registerReceiver(stateUpdaterBroadcastReceiver, new IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT));
        }
        stateReceiverRegistered = true;
    }

    private static void registerReceiversSafe(Activity activity) {
        try { registerReceivers(activity); } catch (Throwable t) { SafeLog.w(TAG, "registerReceivers skipped", t); }
    }

    public static V2rayConstants.CONNECTION_STATES getConnectionState() {
        return connectionState;
    }

    public static boolean isPreparedForConnection(final Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) != PermissionChecker.PERMISSION_GRANTED) return false;
        }
        return VpnService.prepare(context) == null;
    }

    private static boolean prepareForConnection(final Activity activity) {
        if (activity == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, POST_NOTIFICATIONS) != PermissionChecker.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{POST_NOTIFICATIONS}, 101);
                return false;
            }
        }
        Intent vpnIntent = VpnService.prepare(activity);
        if (vpnIntent != null) {
            activity.startActivityForResult(vpnIntent, 9201);
            return false;
        }
        return true;
    }

    /**
     * Drop-in replacement for the old Xray API.
     * Config may be: vless://, vmess://, trojan://, ss://, Xray/V2Ray outbound/full JSON, sing-box full JSON,
     * or an SSH outbound JSON/link if you want to route SSH through the same entry point.
     */
    public static void startV2ray(final Activity activity, final String remark, final String config, final ArrayList<String> blocked_apps) {
        if (!Utilities.refillV2rayConfig(remark, config, blocked_apps)) return;
        if (!isPreparedForConnection(activity)) {
            prepareForConnection(activity);
            return;
        }
        startTunnel(activity);
    }

    /** New helper for SSH, while keeping the old V2rayController class name. */
    public static void startSsh(final Activity activity, final String remark, final String server, final int port, final String username, final String password, final ArrayList<String> blocked_apps) {
        if (!Utilities.refillSshConfig(remark, server, port, username, password, blocked_apps)) return;
        if (!isPreparedForConnection(activity)) {
            prepareForConnection(activity);
            return;
        }
        startTunnel(activity);
    }

    public static void startSSH(final Activity activity, final String remark, final String server, final int port, final String username, final String password, final ArrayList<String> blocked_apps) {
        startSsh(activity, remark, server, port, username, password, blocked_apps);
    }

    public static void stopV2ray(Context context) {
        try {
            if (context == null) return;

            Context appContext = context.getApplicationContext();

            Intent commandIntent = new Intent(V2RAY_SERVICE_COMMAND_INTENT)
                    .setPackage(appContext.getPackageName())
                    .putExtra(V2RAY_SERVICE_COMMAND_EXTRA, V2rayConstants.SERVICE_COMMANDS.STOP_SERVICE);

            // مهم: برای stop از startForegroundService استفاده نکن
            appContext.sendBroadcast(commandIntent);

            Intent serviceIntent = new Intent(appContext, V2rayVPNService.class)
                    .putExtra(V2RAY_SERVICE_COMMAND_EXTRA, V2rayConstants.SERVICE_COMMANDS.STOP_SERVICE);

            appContext.startService(serviceIntent);

        } catch (Exception e) {
            Log.e("V2rayController", "stopV2ray failed", e);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public static void getConnectedV2rayServerDelay(final Context context, final LatencyDelayListener latencyDelayCallback) {
        if (context == null || latencyDelayCallback == null) return;
        if (getConnectionState() != V2rayConstants.CONNECTION_STATES.CONNECTED) {
            latencyDelayCallback.OnResultReady(-1);
            return;
        }
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    long delay = Objects.requireNonNull(intent.getExtras()).getInt(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA);
                    latencyDelayCallback.OnResultReady(delay);
                } catch (Exception ignore) {
                    latencyDelayCallback.OnResultReady(-1);
                }
                try { context.unregisterReceiver(this); } catch (Throwable ignored) {}
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, new IntentFilter(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT), RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, new IntentFilter(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT));
        }
        Intent delay = new Intent(V2RAY_SERVICE_COMMAND_INTENT)
                .setPackage(context.getPackageName())
                .putExtra(V2RAY_SERVICE_COMMAND_EXTRA, V2rayConstants.SERVICE_COMMANDS.MEASURE_DELAY);
        context.sendBroadcast(delay);
    }

    public static long getV2rayServerDelay(final String config) {
        return SingBoxLatencyTester.measureConfigDelay(config);
    }

    public static long getSshServerDelay(final String server, int port) {
        return SingBoxLatencyTester.measureTcpDelay(server, port <= 0 ? 22 : port, 7000);
    }

    public static String getCoreVersion() {
        try {
            System.loadLibrary("gojni");
        } catch (Throwable ignored) {}
        try {
            Class<?> cls = Class.forName("libcore.Libcore");
            for (String name : new String[]{"versionBox", "VersionBox"}) {
                try {
                    Method m = cls.getMethod(name);
                    Object r = m.invoke(null);
                    return String.valueOf(r);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return "sing-box/libcore";
    }

    /** Proxy mode removed intentionally. This method is now a no-op to keep old app code compiling. */
    public static void toggleConnectionMode() {
        V2rayConfigs.serviceMode = V2rayConstants.SERVICE_MODES.VPN_MODE;
    }

    public static void toggleTrafficStatics() {
        currentConfig.enableTrafficStatics = !currentConfig.enableTrafficStatics;
        currentConfig.enableTrafficStaticsOnNotification = currentConfig.enableTrafficStatics;
    }

    private static void startTunnel(final Context context) {
        if (context == null) return;
        V2rayConfigs.serviceMode = V2rayConstants.SERVICE_MODES.VPN_MODE;
        V2rayConfigs.connectionState = V2rayConstants.CONNECTION_STATES.CONNECTING;
        Intent start = new Intent(context, V2rayVPNService.class)
                .setPackage(context.getPackageName())
                .putExtra(V2RAY_SERVICE_COMMAND_EXTRA, V2rayConstants.SERVICE_COMMANDS.START_SERVICE)
                .putExtra(V2RAY_SERVICE_CONFIG_EXTRA, currentConfig);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) context.startForegroundService(start);
        else context.startService(start);
    }

    @Deprecated
    public static boolean IsPreparedForConnection(final Context context) { return isPreparedForConnection(context); }

    @Deprecated
    public static void StartV2ray(final Context context, final String remark, final String config, final ArrayList<String> blocked_apps) {
        if (!Utilities.refillV2rayConfig(remark, config, blocked_apps)) return;
        startTunnel(context);
    }

    @Deprecated
    public static void StopV2ray(final Context context) { stopV2ray(context); }
}
