package dev.dev7.lib.v2ray.utils;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import dev.dev7.lib.v2ray.core.SingBoxConfigBuilder;
import dev.dev7.lib.v2ray.core.XraySidecar;
import dev.dev7.lib.v2ray.model.V2rayConfigModel;

import static dev.dev7.lib.v2ray.utils.V2rayConfigs.currentConfig;

public class Utilities {
    private static final String TAG = "V2rayUtilities";

    public static boolean refillV2rayConfig(final String remark, final String config, final ArrayList<String> blockedApplications) {
        try {
            V2rayConfigModel model = currentConfig;
            model.remark = remark == null || remark.trim().isEmpty() ? "sing-box" : remark;
            model.blockedApplications = blockedApplications;
            model.rawInputConfig = config;
            model.protocolMode = XraySidecar.isXhttp(config) ? "xhttp" : SingBoxConfigBuilder.detectProtocol(config);
            model.fullJsonConfig = XraySidecar.isXhttp(config)
                    ? SingBoxConfigBuilder.buildXhttpBridgeConfig(XraySidecar.socksPort())
                    : SingBoxConfigBuilder.buildAutoConfig(config);
            SingBoxConfigBuilder.Endpoint endpoint = SingBoxConfigBuilder.extractEndpoint(config);
            model.currentServerAddress = endpoint.host == null ? "" : endpoint.host;
            model.currentServerPort = endpoint.port;
            model.enableLocalTunneledDNS = true;
            SafeLog.i(TAG, "Config refill OK protocol=" + model.protocolMode + " endpoint=" + model.currentServerAddress + ":" + model.currentServerPort);
            return true;
        } catch (Throwable t) {
            SafeLog.e(TAG, "refillV2rayConfig failed", t);
            return false;
        }
    }

    public static boolean refillSshConfig(final String remark, final String server, int port, final String user, final String password, final ArrayList<String> blockedApplications) {
        try {
            V2rayConfigModel model = currentConfig;
            model.remark = remark == null || remark.trim().isEmpty() ? "ssh" : remark;
            model.blockedApplications = blockedApplications;
            model.protocolMode = V2rayConstants.PROTOCOL_SSH;
            model.rawInputConfig = "ssh://" + safe(user) + ":***@" + safe(server) + ":" + (port <= 0 ? 22 : port);
            model.fullJsonConfig = SingBoxConfigBuilder.buildSshOnlyConfig(server, port, user, password);
            model.currentServerAddress = server;
            model.currentServerPort = port <= 0 ? 22 : port;
            model.enableLocalTunneledDNS = true;
            SafeLog.i(TAG, "SSH config refill OK endpoint=" + model.currentServerAddress + ":" + model.currentServerPort);
            return true;
        } catch (Throwable t) {
            SafeLog.e(TAG, "refillSshConfig failed", t);
            return false;
        }
    }

    public static String normalizeV2rayFullConfig(final String config) {
        try {
            return SingBoxConfigBuilder.buildAutoConfig(config);
        } catch (Exception e) {
            SafeLog.w(TAG, "normalizeV2rayFullConfig failed; returning raw config", e);
            return config;
        }
    }

    public static String convertIntToTwoDigit(int value) {
        if (value < 10) return "0" + value;
        return String.valueOf(value);
    }

    public static String parseTraffic(final double bytes, final boolean inBits, final boolean isMomentary) {
        double value = bytes;
        String unit = inBits ? "b" : "B";
        if (inBits) value *= 8;
        if (value >= V2rayConstants.GIGA_BYTE) return String.format(java.util.Locale.US, "%.2f G%s%s", value / V2rayConstants.GIGA_BYTE, unit, isMomentary ? "/s" : "");
        if (value >= V2rayConstants.MEGA_BYTE) return String.format(java.util.Locale.US, "%.2f M%s%s", value / V2rayConstants.MEGA_BYTE, unit, isMomentary ? "/s" : "");
        if (value >= V2rayConstants.KILO_BYTE) return String.format(java.util.Locale.US, "%.2f K%s%s", value / V2rayConstants.KILO_BYTE, unit, isMomentary ? "/s" : "");
        return String.format(java.util.Locale.US, "%.0f %s%s", value, unit, isMomentary ? "/s" : "");
    }

    public static String normalizeIpv6(String address) {
        if (isIpv6Address(address) && !address.contains("[") && !address.contains("]")) return String.format("[%s]", address);
        return address;
    }

    public static boolean isIpv6Address(String address) {
        return address != null && address.split(":").length > 2;
    }

    public static JSONObject firstOutboundFromXrayJson(String raw) throws Exception {
        JSONObject obj = new JSONObject(raw);
        JSONArray outs = obj.getJSONArray("outbounds");
        return outs.getJSONObject(0);
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
