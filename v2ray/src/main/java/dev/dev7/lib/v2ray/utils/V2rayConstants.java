package dev.dev7.lib.v2ray.utils;

public class V2rayConstants {
    public static final String V2RAY_SERVICE_OPENED_APPLICATION_INTENT = "APP_OPEN_FROM_V2RAY_NOTIFICATION_INTENT";
    public static final String V2RAY_SERVICE_STATICS_BROADCAST_INTENT = "V2RAY_SERVICE_STATICS_INTENT";
    public static final String V2RAY_SERVICE_COMMAND_INTENT = "V2RAY_SERVICE_COMMAND_INTENT";
    public static final String V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT = "V2RAY_SERVICE_CURRENT_CONFIG_DELAY_INTENT";
    public static final String V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA = "V2RAY_SERVICE_CURRENT_CONFIG_DELAY_EXTRA";
    public static final String V2RAY_SERVICE_COMMAND_EXTRA = "V2RAY_SERVICE_COMMAND_EXTRA";
    public static final String V2RAY_SERVICE_CONFIG_EXTRA = "V2RAY_SERVICE_CONFIG_EXTRA";
    public static final String SERVICE_CONNECTION_STATE_BROADCAST_EXTRA = "CONNECTION_STATE_EXTRA";
    public static final String SERVICE_TYPE_BROADCAST_EXTRA = "SERVICE_TYPE_EXTRA";
    public static final String SERVICE_CORE_STATE_BROADCAST_EXTRA = "CORE_STATE_EXTRA";
    public static final String SERVICE_DURATION_BROADCAST_EXTRA = "SERVICE_DURATION_EXTRA";
    public static final String SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA = "UPLOAD_SPEED_EXTRA";
    public static final String SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA = "DOWNLOAD_SPEED_EXTRA";
    public static final String SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA = "DOWNLOADED_TRAFFIC_EXTRA";
    public static final String SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA = "UPLOADED_TRAFFIC_EXTRA";

    public static final long BYTE = 1;
    public static final long KILO_BYTE = BYTE * 1024;
    public static final long MEGA_BYTE = KILO_BYTE * 1024;
    public static final long GIGA_BYTE = MEGA_BYTE * 1024;

    public enum SERVICE_MODES { VPN_MODE, PROXY_MODE }
    public enum SERVICE_COMMANDS { START_SERVICE, STOP_SERVICE, MEASURE_DELAY }
    public enum CONNECTION_STATES { CONNECTED, CONNECTING, DISCONNECTED }
    public enum CORE_STATES { RUNNING, IDLE, STOPPED }

    public static final String PROTOCOL_AUTO = "auto";
    public static final String PROTOCOL_V2RAY = "v2ray";
    public static final String PROTOCOL_SSH = "ssh";
}
