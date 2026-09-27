package dev.dev7.lib.v2ray.services;

import static android.content.Context.RECEIVER_EXPORTED;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_CONNECTION_STATE_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_CORE_STATE_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_DURATION_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_TYPE_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_INTENT;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_CONFIG_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_STATICS_BROADCAST_INTENT;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.TrafficStats;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import dev.dev7.lib.v2ray.core.SingBoxCoreBridge;
import dev.dev7.lib.v2ray.core.XraySidecar;
import dev.dev7.lib.v2ray.core.SingBoxLatencyTester;
import dev.dev7.lib.v2ray.interfaces.NotificationProvider;
import dev.dev7.lib.v2ray.interfaces.V2rayServicesListener;
import dev.dev7.lib.v2ray.model.V2rayConfigModel;
import dev.dev7.lib.v2ray.utils.SafeLog;
import dev.dev7.lib.v2ray.utils.V2rayConfigs;
import dev.dev7.lib.v2ray.utils.V2rayConstants;

public class V2rayVPNService extends VpnService implements V2rayServicesListener {
    private static final String TAG = "SingBoxVPNService";
    private static final String CHANNEL_ID = "singbox_vpn_service";
    private static final int NOTIFICATION_ID = 7282;
    private NotificationProvider activeCustomProvider = null;
    private Thread trafficUpdaterThread = null;
    private volatile boolean shouldUpdateTraffic = false;
    /*
     * Keep this false when the host app has its own notification.
     *
     * Important:
     * - If the service is started with startForegroundService(), Android requires
     *   at least one startForeground() call shortly after onCreate().
     * - To avoid a crash and still avoid a permanent second notification, we call
     *   startForeground() once and immediately remove the notification.
     */
    private static final boolean ENABLE_LIBRARY_NOTIFICATION = false;
    private volatile boolean foregroundStarted = false;

    private static final String ACTION_PROTOCOL_TRAFFIC = "pw.fullvpn.android.action.PROTOCOL_TRAFFIC";
    private static final String EXTRA_PROTOCOL = "protocol";
    private static final String EXTRA_STATE = "state";
    private static final String EXTRA_DOWNLOAD_TOTAL = "download_total_bytes";
    private static final String EXTRA_UPLOAD_TOTAL = "upload_total_bytes";
    private static final String EXTRA_DOWNLOAD_SPEED = "download_speed_bps";
    private static final String EXTRA_UPLOAD_SPEED = "upload_speed_bps";
    private static final String EXTRA_DURATION_SECONDS = "duration_seconds";
    private static final String EXTRA_TIMESTAMP = "timestamp_ms";
    private static final String PROTOCOL_V2RAY = "V2RAY";
    private static final String PROTOCOL_SSH = "SSH";
    private static final String STATE_CONNECTING = "CONNECTING";
    private static final String STATE_CONNECTED = "CONNECTED";
    private static final String STATE_DISCONNECTED = "DISCONNECTED";

    private volatile long sessionDownloadTotal = 0L;
    private volatile long sessionUploadTotal = 0L;
    private volatile long lastDownloadSpeed = 0L;
    private volatile long lastUploadSpeed = 0L;

    private final Object lock = new Object();
    private volatile boolean running = false;
    private volatile boolean receiverRegistered = false;
    private Thread workerThread;
    private ParcelFileDescriptor tunFd;
    private SingBoxCoreBridge coreBridge;
    private boolean xhttpSidecarRunning = false;
    private V2rayConfigModel currentConfig = new V2rayConfigModel();
    private long connectedAt = 0L;
    private static NotificationProvider customNotificationProvider = null;

    public static void setCustomNotificationProvider(NotificationProvider provider) {
        customNotificationProvider = provider;
    }

    private final BroadcastReceiver serviceCommandBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                V2rayConstants.SERVICE_COMMANDS command = (V2rayConstants.SERVICE_COMMANDS) intent.getSerializableExtra(V2RAY_SERVICE_COMMAND_EXTRA);
                SafeLog.i(TAG, "Command received=" + command);
                if (command == null) return;
                switch (command) {
                    case STOP_SERVICE:
                        stopService();
                        break;
                    case MEASURE_DELAY:
                        broadcastCurrentDelay();
                        break;
                    default:
                        break;
                }
            } catch (Throwable t) {
                SafeLog.w(TAG, "Command receiver failed", t);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        SafeLog.init(this);
        SafeLog.i(TAG, "onCreate package=" + getPackageName() + " pid=" + android.os.Process.myPid());
        V2rayConfigs.connectionState = V2rayConstants.CONNECTION_STATES.CONNECTING;

        /*
         * Do not keep a library notification.
         *
         * If V2rayController starts this service with startForegroundService(),
         * Android 8+ will crash the app unless startForeground() is called.
         * This method safely satisfies that requirement, then removes the
         * notification immediately when ENABLE_LIBRARY_NOTIFICATION=false.
         */
        prepareForegroundWithoutPersistentNotification("Starting VPN...");

        registerCommandReceiver();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerCommandReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter(V2RAY_SERVICE_COMMAND_INTENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceCommandBroadcastReceiver, filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(serviceCommandBroadcastReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SafeLog.i(TAG, "onStartCommand flags=" + flags + " startId=" + startId + " running=" + running + " intent=" + intent);
        if (intent == null) return START_NOT_STICKY;
        V2rayConstants.SERVICE_COMMANDS command = (V2rayConstants.SERVICE_COMMANDS) intent.getSerializableExtra(V2RAY_SERVICE_COMMAND_EXTRA);
        if (command == V2rayConstants.SERVICE_COMMANDS.STOP_SERVICE) {
            stopService();
            return START_NOT_STICKY;
        }
        if (command != V2rayConstants.SERVICE_COMMANDS.START_SERVICE) return START_STICKY;

        V2rayConfigModel config = (V2rayConfigModel) intent.getSerializableExtra(V2RAY_SERVICE_CONFIG_EXTRA);
        if (config == null || config.fullJsonConfig == null || config.fullJsonConfig.trim().isEmpty()) {
            SafeLog.e(TAG, "Missing V2rayConfigModel/fullJsonConfig");
            stopService();
            return START_NOT_STICKY;
        }
        currentConfig = config;
        V2rayConfigs.currentConfig = config;
        startCore(config);
        return START_STICKY;
    }

    private void startCore(V2rayConfigModel config) {
        synchronized (lock) {
            if (running) {
                SafeLog.w(TAG, "startCore ignored: already running");
                return;
            }
            running = true;
        }
        V2rayConfigs.connectionState = V2rayConstants.CONNECTION_STATES.CONNECTING;
        broadcastState(V2rayConstants.CONNECTION_STATES.CONNECTING, V2rayConstants.CORE_STATES.IDLE);
        updateNotification("Connecting " + safe(config.remark) + "...");

        workerThread = new Thread(() -> {
            boolean connected = false;
            try {
                SafeLog.i(TAG, "Worker started protocol=" + config.protocolMode + " endpoint=" + config.currentServerAddress + ":" + config.currentServerPort);
                SafeLog.d(TAG, "sing-box config:\n" + config.fullJsonConfig);

                if (XraySidecar.isXhttp(config.rawInputConfig)) {
                    SafeLog.i(TAG, "XHTTP detected; starting Xray sidecar");
                    XraySidecar.start(this, config.rawInputConfig);
                    xhttpSidecarRunning = true;
                }

                coreBridge = new SingBoxCoreBridge(this);
                coreBridge.init();
                SafeLog.i(TAG, "core init OK");
                coreBridge.start(config.fullJsonConfig);
                SafeLog.i(TAG, "core start OK");

                connected = true;
                startTrafficMonitoring();
                connectedAt = SystemClock.elapsedRealtime();
                V2rayConfigs.connectionState = V2rayConstants.CONNECTION_STATES.CONNECTED;
                updateNotification("Connected: " + safe(config.remark));
                broadcastState(V2rayConstants.CONNECTION_STATES.CONNECTED, V2rayConstants.CORE_STATES.RUNNING);

                while (running) {
                    broadcastState(V2rayConstants.CONNECTION_STATES.CONNECTED, V2rayConstants.CORE_STATES.RUNNING);
                    Thread.sleep(1000);
                }
            } catch (Throwable t) {
                SafeLog.e(TAG, "Core failed", t);
                V2rayConfigs.connectionState = V2rayConstants.CONNECTION_STATES.DISCONNECTED;
                broadcastState(V2rayConstants.CONNECTION_STATES.DISCONNECTED, V2rayConstants.CORE_STATES.STOPPED);
            } finally {
                SafeLog.i(TAG, "Worker finally connected=" + connected);
                cleanup();
                V2rayConfigs.connectionState = V2rayConstants.CONNECTION_STATES.DISCONNECTED;
                broadcastState(V2rayConstants.CONNECTION_STATES.DISCONNECTED, V2rayConstants.CORE_STATES.STOPPED);
                stopForegroundCompat();
                stopSelf();
            }
        }, "singbox-vpn-worker");
        workerThread.setUncaughtExceptionHandler((t, e) -> SafeLog.e(TAG, "Uncaught worker exception", e));
        workerThread.start();
    }

    public synchronized int openTun(String singTunOptionsJson, String tunPlatformOptionsJson) throws Exception {
        SafeLog.i(TAG, "openTun called");
        SafeLog.d(TAG, "openTun singTunOptions=" + singTunOptionsJson);
        SafeLog.d(TAG, "openTun platformOptions=" + tunPlatformOptionsJson);

        if (tunFd != null) {
            try { tunFd.close(); } catch (Exception e) { SafeLog.w(TAG, "close old tun failed", e); }
            tunFd = null;
        }

        TunOptions options = TunOptions.fromJson(singTunOptionsJson);

        Builder builder = new Builder()
                .setSession(currentConfig.remark == null ? "sing-box" : currentConfig.remark)
                .setMtu(options.mtu > 0 ? options.mtu : 1400);

        boolean hasAddress = false;
        for (String address : options.addresses) {
            hasAddress |= addTunAddress(builder, address);
        }
        if (!hasAddress) {
            builder.addAddress("172.19.0.1", 30);
            SafeLog.w(TAG, "openTun fallback address=172.19.0.1/30");
        }

        boolean hasRoute = false;
        for (String route : options.routes) {
            hasRoute |= addTunRoute(builder, route);
        }
        if (!hasRoute) {
            builder.addRoute("0.0.0.0", 0);
            SafeLog.w(TAG, "openTun fallback route=0.0.0.0/0");
        }

        boolean hasDns = false;
        for (String dns : options.dnsServers) {
            hasDns |= addTunDns(builder, dns);
        }
        if (!hasDns) {
            builder.addDnsServer("172.19.0.2");
            SafeLog.w(TAG, "openTun fallback dns=172.19.0.2");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }

        try {
            builder.addDisallowedApplication(getPackageName());
            SafeLog.i(TAG, "Split-tunnel excluded host app=" + getPackageName());
        } catch (Throwable t) {
            SafeLog.w(TAG, "Split-tunnel exclude host app failed", t);
        }

        ArrayList<String> apps = currentConfig.blockedApplications;
        if (apps != null) {
            for (String pkg : apps) {
                try {
                    if (pkg != null && !pkg.trim().isEmpty() && !pkg.equals(getPackageName())) {
                        builder.addDisallowedApplication(pkg);
                        SafeLog.i(TAG, "Split-tunnel excluded app=" + pkg);
                    }
                } catch (Throwable t) {
                    SafeLog.w(TAG, "Split-tunnel exclude failed pkg=" + pkg, t);
                }
            }
        }

        tunFd = builder.establish();
        if (tunFd == null) throw new IllegalStateException("VPN permission denied or TUN establish failed");
        SafeLog.i(TAG, "TUN established fd=" + tunFd.getFd()
                + " addresses=" + options.addresses
                + " routes=" + options.routes
                + " dns=" + options.dnsServers
                + " mtu=" + (options.mtu > 0 ? options.mtu : 1400));
        return tunFd.getFd();
    }

    private boolean addTunAddress(Builder builder, String cidr) {
        try {
            Cidr parsed = Cidr.parse(cidr);
            if (parsed == null) return false;
            builder.addAddress(parsed.address, parsed.prefixLength);
            SafeLog.i(TAG, "openTun address=" + parsed.address + "/" + parsed.prefixLength);
            return true;
        } catch (Throwable t) {
            SafeLog.w(TAG, "openTun addAddress failed cidr=" + cidr, t);
            return false;
        }
    }

    private boolean addTunRoute(Builder builder, String cidr) {
        try {
            Cidr parsed = Cidr.parse(cidr);
            if (parsed == null) return false;
            builder.addRoute(parsed.address, parsed.prefixLength);
            SafeLog.i(TAG, "openTun route=" + parsed.address + "/" + parsed.prefixLength);
            return true;
        } catch (Throwable t) {
            SafeLog.w(TAG, "openTun addRoute failed cidr=" + cidr, t);
            return false;
        }
    }

    private boolean addTunDns(Builder builder, String dns) {
        try {
            if (dns == null || dns.trim().isEmpty()) return false;
            builder.addDnsServer(dns.trim());
            SafeLog.i(TAG, "openTun dns=" + dns.trim());
            return true;
        } catch (Throwable t) {
            SafeLog.w(TAG, "openTun addDns failed dns=" + dns, t);
            return false;
        }
    }

    private static final class Cidr {
        final String address;
        final int prefixLength;

        private Cidr(String address, int prefixLength) {
            this.address = address;
            this.prefixLength = prefixLength;
        }

        static Cidr parse(String value) {
            if (value == null) return null;
            String trimmed = value.trim();
            if (trimmed.isEmpty()) return null;
            int slash = trimmed.indexOf('/');
            if (slash < 0) {
                int prefix = trimmed.contains(":") ? 128 : 32;
                return new Cidr(trimmed, prefix);
            }
            String address = trimmed.substring(0, slash).trim();
            int prefix = Integer.parseInt(trimmed.substring(slash + 1).trim());
            if (address.isEmpty()) return null;
            return new Cidr(address, prefix);
        }
    }

    private static final class TunOptions {
        final ArrayList<String> addresses = new ArrayList<>();
        final ArrayList<String> routes = new ArrayList<>();
        final ArrayList<String> dnsServers = new ArrayList<>();
        int mtu = 1400;

        static TunOptions fromJson(String json) {
            TunOptions options = new TunOptions();
            if (json == null || json.trim().isEmpty()) return options;
            try {
                JSONObject root = new JSONObject(json);

                options.mtu = root.optInt("mtu", 1400);

                addStrings(options.addresses, root.optJSONArray("address"));
                addStrings(options.addresses, root.optJSONArray("addresses"));
                addStrings(options.addresses, root.optJSONArray("inet4_address"));
                addStrings(options.addresses, root.optJSONArray("inet6_address"));

                addStrings(options.routes, root.optJSONArray("route_address"));
                addStrings(options.routes, root.optJSONArray("route_addresses"));
                addStrings(options.routes, root.optJSONArray("inet4_route_address"));
                addStrings(options.routes, root.optJSONArray("inet6_route_address"));
                addStrings(options.routes, root.optJSONArray("include_address"));
                addStrings(options.routes, root.optJSONArray("include_addresses"));

                addStrings(options.dnsServers, root.optJSONArray("dns_server_address"));
                addStrings(options.dnsServers, root.optJSONArray("dns_server_addresses"));
                addStrings(options.dnsServers, root.optJSONArray("dns_servers"));
                addStrings(options.dnsServers, root.optJSONArray("dns"));

                if (options.routes.isEmpty() && root.optBoolean("auto_route", false)) {
                    options.routes.add("0.0.0.0/0");
                    // IPv6 is intentionally not forced as fallback because many configs/devices do not use it.
                }
            } catch (Throwable ignored) {
                // Fallbacks in openTun() will be used.
            }
            return options;
        }

        private static void addStrings(ArrayList<String> target, JSONArray array) {
            if (array == null) return;
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (!value.isEmpty() && !target.contains(value)) target.add(value);
            }
        }
    }

    public boolean protectFd(int fd) {
        boolean ok = protect(fd);
        // protectFd can be called very frequently by sing-box; keep it debug-only to avoid log spam.
        SafeLog.d(TAG, "protectFd fd=" + fd + " ok=" + ok);
        return ok;
    }

    public int getConnectionOwnerUidCompat(Object[] args) {
        if (Build.VERSION.SDK_INT < 29) return 0;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network active = cm == null ? null : cm.getActiveNetwork();
            return active == null ? 0 : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private void broadcastCurrentDelay() {
        new Thread(() -> {
            long delay = SingBoxLatencyTester.measureConfigDelay(currentConfig.rawInputConfig != null ? currentConfig.rawInputConfig : currentConfig.fullJsonConfig);
            Intent intent = new Intent(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT)
                    .setPackage(getPackageName())
                    .putExtra(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA, (int) delay);
            sendBroadcast(intent);
            SafeLog.i(TAG, "broadcastCurrentDelay=" + delay);
        }, "singbox-delay-worker").start();
    }

    private void broadcastState(V2rayConstants.CONNECTION_STATES state, V2rayConstants.CORE_STATES coreState) {
        long duration = connectedAt <= 0 ? 0 : (SystemClock.elapsedRealtime() - connectedAt) / 1000;
        Intent intent = new Intent(V2RAY_SERVICE_STATICS_BROADCAST_INTENT)
                .setPackage(getPackageName())
                .putExtra(SERVICE_CONNECTION_STATE_BROADCAST_EXTRA, state)
                .putExtra(SERVICE_CORE_STATE_BROADCAST_EXTRA, coreState)
                .putExtra(SERVICE_TYPE_BROADCAST_EXTRA, V2rayVPNService.class.getSimpleName())
                .putExtra(SERVICE_DURATION_BROADCAST_EXTRA, duration)
                .putExtra(SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA, lastUploadSpeed)
                .putExtra(SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA, lastDownloadSpeed)
                .putExtra(SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA, sessionUploadTotal)
                .putExtra(SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA, sessionDownloadTotal);
        sendBroadcast(intent);

        sendNormalizedTrafficBroadcast(state, duration);
    }

    private void sendNormalizedTrafficBroadcast(V2rayConstants.CONNECTION_STATES state, long duration) {
        Intent intent = new Intent(ACTION_PROTOCOL_TRAFFIC);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_PROTOCOL, normalizedProtocolName());
        intent.putExtra(EXTRA_STATE, mapConnectionState(state));
        intent.putExtra(EXTRA_DOWNLOAD_TOTAL, Math.max(0L, sessionDownloadTotal));
        intent.putExtra(EXTRA_UPLOAD_TOTAL, Math.max(0L, sessionUploadTotal));
        intent.putExtra(EXTRA_DOWNLOAD_SPEED, Math.max(0L, lastDownloadSpeed));
        intent.putExtra(EXTRA_UPLOAD_SPEED, Math.max(0L, lastUploadSpeed));
        intent.putExtra(EXTRA_DURATION_SECONDS, duration);
        intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        sendBroadcast(intent);
    }

    private String normalizedProtocolName() {
        try {
            return V2rayConstants.PROTOCOL_SSH.equalsIgnoreCase(currentConfig.protocolMode) ? PROTOCOL_SSH : PROTOCOL_V2RAY;
        } catch (Throwable ignored) {
            return PROTOCOL_V2RAY;
        }
    }

    private String mapConnectionState(V2rayConstants.CONNECTION_STATES state) {
        if (state == V2rayConstants.CONNECTION_STATES.CONNECTED) return STATE_CONNECTED;
        if (state == V2rayConstants.CONNECTION_STATES.CONNECTING) return STATE_CONNECTING;
        return STATE_DISCONNECTED;
    }

    private void cleanup() {
        SafeLog.i(TAG, "cleanup begin");
        cancelLibraryNotificationSafe();
        synchronized (lock) { running = false; }
        try {
            if (coreBridge != null) coreBridge.stop();
        } catch (Throwable t) {
            SafeLog.w(TAG, "coreBridge.stop failed", t);
        }
        coreBridge = null;
        try {
            if (tunFd != null) tunFd.close();
        } catch (Throwable t) {
            SafeLog.w(TAG, "tun close failed", t);
        }
        tunFd = null;
        if (xhttpSidecarRunning) {
            try { XraySidecar.stop(); } catch (Throwable t) { SafeLog.w(TAG, "Xray sidecar stop failed", t); }
            xhttpSidecarRunning = false;
        }
        connectedAt = 0L;
        stopTrafficMonitoring();
        SafeLog.i(TAG, "cleanup end");
    }

    private Notification createNotification(String text) {
        createChannelIfNeeded();
        Intent stopIntent = new Intent(V2RAY_SERVICE_COMMAND_INTENT)
                .setPackage(getPackageName())
                .putExtra(V2RAY_SERVICE_COMMAND_EXTRA, V2rayConstants.SERVICE_COMMANDS.STOP_SERVICE);
        PendingIntent stopPending = PendingIntent.getBroadcast(
                this,
                701,
                stopIntent,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        int icon = currentConfig != null && currentConfig.applicationIcon != 0 ? currentConfig.applicationIcon : android.R.drawable.stat_sys_download_done;
        return builder
                .setContentTitle(currentConfig != null && currentConfig.applicationName != null ? currentConfig.applicationName : "VPN")
                .setContentText(text)
                .setSmallIcon(icon)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopPending)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        if (!ENABLE_LIBRARY_NOTIFICATION) {
            cancelLibraryNotificationSafe();
            return;
        }

        try {
            if (!foregroundStarted) {
                startForeground(NOTIFICATION_ID, createNotification(text));
                foregroundStarted = true;
                return;
            }

            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, createNotification(text));
        } catch (Throwable t) {
            SafeLog.w(TAG, "updateNotification failed", t);
        }
    }

    private void prepareForegroundWithoutPersistentNotification(String text) {
        try {
            if (customNotificationProvider != null) {
                // استفاده از Provider سفارشی
                activeCustomProvider = customNotificationProvider;
                customNotificationProvider = null; // یکبار مصرف

                int icon = (currentConfig != null && currentConfig.applicationIcon != 0)
                        ? currentConfig.applicationIcon
                        : android.R.drawable.stat_sys_download_done;
                String remark = (currentConfig != null && currentConfig.remark != null)
                        ? currentConfig.remark
                        : "VPN";
                Notification notif = activeCustomProvider.getOngoingNotification(remark, icon);
                startForeground(NOTIFICATION_ID, notif);
                foregroundStarted = true;
                return;
            }

            // حالت fallback (اگر Provider نبود و ENABLE_LIBRARY_NOTIFICATION فعال بود)
            if (!ENABLE_LIBRARY_NOTIFICATION) {
                startForeground(NOTIFICATION_ID, createNotification(text));
                foregroundStarted = true;
                stopForegroundCompat(); // حذف فوری نوتیف کتابخانه
            } else {
                startForeground(NOTIFICATION_ID, createNotification(text));
                foregroundStarted = true;
            }
        } catch (Throwable t) {
            SafeLog.w(TAG, "prepareForegroundWithoutPersistentNotification failed", t);
            cancelLibraryNotificationSafe();
        }
    }

    private void startTrafficMonitoring() {
        shouldUpdateTraffic = true;
        sessionDownloadTotal = 0L;
        sessionUploadTotal = 0L;
        lastDownloadSpeed = 0L;
        lastUploadSpeed = 0L;

        trafficUpdaterThread = new Thread(() -> {
            long lastRx = safeUidRxBytes();
            long lastTx = safeUidTxBytes();
            while (shouldUpdateTraffic && running) {
                try {
                    Thread.sleep(1000);
                    long newRx = safeUidRxBytes();
                    long newTx = safeUidTxBytes();
                    long dlSpeed = Math.max(0, newRx - lastRx);
                    long ulSpeed = Math.max(0, newTx - lastTx);
                    lastRx = newRx;
                    lastTx = newTx;

                    sessionDownloadTotal += dlSpeed;
                    sessionUploadTotal += ulSpeed;
                    lastDownloadSpeed = dlSpeed;
                    lastUploadSpeed = ulSpeed;

                    if (activeCustomProvider != null) {
                        activeCustomProvider.updateNotificationSpeed(dlSpeed, ulSpeed, sessionDownloadTotal, sessionUploadTotal);
                    }
                    broadcastState(V2rayConstants.CONNECTION_STATES.CONNECTED, V2rayConstants.CORE_STATES.RUNNING);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "traffic-updater");
        trafficUpdaterThread.start();
    }

    private long safeUidRxBytes() {
        long value = TrafficStats.getUidRxBytes(android.os.Process.myUid());
        return value == TrafficStats.UNSUPPORTED ? 0L : Math.max(0L, value);
    }

    private long safeUidTxBytes() {
        long value = TrafficStats.getUidTxBytes(android.os.Process.myUid());
        return value == TrafficStats.UNSUPPORTED ? 0L : Math.max(0L, value);
    }

    private void stopTrafficMonitoring() {
        shouldUpdateTraffic = false;
        if (trafficUpdaterThread != null) {
            trafficUpdaterThread.interrupt();
            trafficUpdaterThread = null;
        }
    }

    private void cancelLibraryNotificationSafe() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(NOTIFICATION_ID);
        } catch (Throwable ignored) {}
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW));
            }
        }
    }

    private void stopForegroundCompat() {
        if (activeCustomProvider != null) {
            activeCustomProvider.dismiss();
            activeCustomProvider = null;
        }
        if (!foregroundStarted) {
            cancelLibraryNotificationSafe();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForeground(true);
        } catch (Throwable ignored) {
        } finally {
            foregroundStarted = false;
            cancelLibraryNotificationSafe();
        }
    }

    @Override
    public void onRevoke() {
        stopService();
    }

    @Override
    public void onDestroy() {
        SafeLog.i(TAG, "onDestroy");
        try {
            if (receiverRegistered) unregisterReceiver(serviceCommandBroadcastReceiver);
        } catch (Throwable ignored) {}
        receiverRegistered = false;
        cleanup();
        super.onDestroy();
    }

    @Override
    public boolean onProtect(int socket) { return protect(socket); }

    @Override
    public Service getService() { return this; }

    @Override
    public void startService() { /* compatibility */ }

    @Override
    public void stopService() {
        SafeLog.i(TAG, "stopService called");
        synchronized (lock) { running = false; }
        cleanup();
        V2rayConfigs.connectionState = V2rayConstants.CONNECTION_STATES.DISCONNECTED;
        broadcastState(V2rayConstants.CONNECTION_STATES.DISCONNECTED, V2rayConstants.CORE_STATES.STOPPED);
        stopForegroundCompat();
        stopSelf();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
