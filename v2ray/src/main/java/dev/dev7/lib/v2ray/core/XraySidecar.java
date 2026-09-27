package dev.dev7.lib.v2ray.core;

import android.content.Context;
import android.os.Build;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dev.dev7.lib.v2ray.utils.SafeLog;

/**
 * Xray sidecar used only for XHTTP configurations.
 *
 * The main VPN/TUN stack remains sing-box. Xray listens on a local SOCKS port and
 * sing-box forwards all device traffic to it. This keeps existing protocols on the
 * current core while giving XHTTP a real Xray implementation.
 */
public final class XraySidecar {
    private static final String TAG = "XraySidecar";
    private static final int SOCKS_PORT = 10808;
    private static final String XRAY_VERSION = "26.9.8";
    private static volatile Process process;

    private XraySidecar() {}

    public static boolean isXhttp(String input) {
        if (input == null) return false;
        String raw = input.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("vless://") || lower.startsWith("vmess://") || lower.startsWith("trojan://")) {
            return lower.contains("type=xhttp") || lower.contains("type%3dxhttp") || lower.contains("network=xhttp") || lower.contains("net=xhttp");
        }
        if (raw.startsWith("{") || raw.startsWith("[")) {
            try {
                JSONObject root = raw.startsWith("[") ? new JSONArray(raw).getJSONObject(0) : new JSONObject(raw);
                return containsXhttp(root);
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static boolean containsXhttp(JSONObject root) {
        if (root == null) return false;
        if ("xhttp".equalsIgnoreCase(root.optString("network", ""))) return true;
        if ("xhttp".equalsIgnoreCase(root.optString("net", ""))) return true;
        JSONObject stream = root.optJSONObject("streamSettings");
        if (stream != null && "xhttp".equalsIgnoreCase(stream.optString("network", ""))) return true;
        JSONObject transport = root.optJSONObject("transport");
        if (transport != null && "xhttp".equalsIgnoreCase(transport.optString("type", ""))) return true;
        JSONArray outs = root.optJSONArray("outbounds");
        if (outs != null) for (int i = 0; i < outs.length(); i++) {
            if (containsXhttp(outs.optJSONObject(i))) return true;
        }
        return false;
    }

    public static synchronized void start(Context context, String rawInput) throws Exception {
        stop();
        Context app = context.getApplicationContext();
        File binary = XrayBinaryManager.ensure(app);
        File dir = new File(app.getFilesDir(), "xray-sidecar");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create Xray sidecar directory");
        File configFile = new File(dir, "config.json");
        JSONObject config = buildXrayConfig(rawInput);
        writeText(configFile, config.toString(2));

        ProcessBuilder pb = new ProcessBuilder(binary.getAbsolutePath(), "run", "-c", configFile.getAbsolutePath());
        pb.directory(dir);
        pb.redirectErrorStream(true);
        process = pb.start();
        drainAsync(process.getInputStream());

        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Xray sidecar exited immediately (exit=" + process.exitValue()
                        + "). See logcat tag XraySidecar for the Xray error.");
            }
            if (canConnectLocalhost(SOCKS_PORT)) {
                SafeLog.i(TAG, "Xray sidecar ready on 127.0.0.1:" + SOCKS_PORT);
                return;
            }
            Thread.sleep(100);
        }
        stop();
        throw new IllegalStateException("Xray sidecar did not open SOCKS port " + SOCKS_PORT);
    }

    public static synchronized void stop() {
        Process p = process;
        process = null;
        if (p != null) {
            try { p.destroy(); } catch (Throwable ignored) {}
            try { if (Build.VERSION.SDK_INT >= 26) p.destroyForcibly(); } catch (Throwable ignored) {}
        }
    }

    public static int socksPort() { return SOCKS_PORT; }

    /** Convert the supported Xray/V2Ray share forms into a native Xray client config. */
    public static JSONObject buildXrayConfig(String input) throws Exception {
        String raw = input == null ? "" : input.trim();
        JSONObject outbound;
        if (raw.toLowerCase(Locale.ROOT).startsWith("vless://")) outbound = parseVless(raw);
        else if (raw.startsWith("{")) outbound = findOutbound(new JSONObject(raw));
        else throw new IllegalArgumentException("XHTTP currently requires a VLESS URI or Xray JSON");

        outbound.put("tag", "xhttp-proxy");
        JSONObject root = new JSONObject();
        root.put("log", new JSONObject().put("loglevel", "warning"));
        JSONArray inbounds = new JSONArray();
        inbounds.put(new JSONObject()
                .put("tag", "socks-in")
                .put("listen", "127.0.0.1")
                .put("port", SOCKS_PORT)
                .put("protocol", "socks")
                .put("settings", new JSONObject().put("udp", true)));
        root.put("inbounds", inbounds);
        root.put("outbounds", new JSONArray().put(outbound).put(new JSONObject().put("protocol", "freedom").put("tag", "direct")));
        return root;
    }

    private static JSONObject parseVless(String raw) throws Exception {
        URI uri = URI.create(raw);
        String uuid = uri.getUserInfo();
        if (uuid == null || uuid.isEmpty()) throw new IllegalArgumentException("Missing VLESS UUID");
        Map<String,String> q = queryMap(uri.getRawQuery());
        String type = first(q.get("type"), q.get("net"), q.get("network"));
        if (!"xhttp".equalsIgnoreCase(type)) throw new IllegalArgumentException("Not an XHTTP VLESS config");

        JSONObject out = new JSONObject();
        out.put("protocol", "vless");
        out.put("settings", new JSONObject().put("vnext", new JSONArray().put(
                new JSONObject().put("address", uri.getHost())
                        .put("port", uri.getPort() > 0 ? uri.getPort() : 443)
                        .put("users", new JSONArray().put(new JSONObject().put("id", uuid).put("encryption", "none")))
        )));

        JSONObject stream = new JSONObject();
        stream.put("network", "xhttp");
        String security = first(q.get("security"), "none");
        if ("tls".equalsIgnoreCase(security) || "reality".equalsIgnoreCase(security)) {
            stream.put("security", security);
            JSONObject tls = new JSONObject();
            tls.put("serverName", first(q.get("sni"), q.get("host"), uri.getHost()));
            tls.put("allowInsecure", truthy(q.get("allowInsecure")) || truthy(q.get("insecure")));
            if (notEmpty(q.get("alpn"))) tls.put("alpn", new JSONArray(q.get("alpn").split(",")));
            if (notEmpty(q.get("fp"))) tls.put("fingerprint", q.get("fp"));
            if ("reality".equalsIgnoreCase(security)) {
                tls.put("publicKey", first(q.get("pbk"), q.get("publicKey")));
                tls.put("shortId", first(q.get("sid"), q.get("shortId")));
                tls.put("spiderX", first(q.get("spx"), q.get("spiderX"), "/"));
            }
            stream.put("tlsSettings", tls);
        }
        JSONObject xhttp = new JSONObject();
        xhttp.put("path", first(q.get("path"), "/"));
        if (notEmpty(q.get("mode"))) xhttp.put("mode", q.get("mode"));
        if (notEmpty(q.get("host"))) xhttp.put("host", q.get("host"));
        if (notEmpty(q.get("extra"))) {
            JSONObject extra = parseXhttpExtra(q.get("extra"));
            // scMaxConcurrentPosts was used by some older/custom XHTTP configs,
            // but is not a current Xray client option for packet-up. Do not pass
            // it through to a newer core where it can make config validation fail.
            extra.remove("scMaxConcurrentPosts");
            xhttp.put("extra", extra);
        }
        stream.put("xhttpSettings", xhttp);
        out.put("streamSettings", stream);
        return out;
    }

    private static JSONObject findOutbound(JSONObject root) throws Exception {
        if (root.has("protocol")) return new JSONObject(root.toString());
        if (root.has("outbounds")) {
            JSONArray a = root.getJSONArray("outbounds");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && "vless".equalsIgnoreCase(o.optString("protocol", o.optString("type", "")))) return new JSONObject(o.toString());
            }
        }
        throw new IllegalArgumentException("No VLESS outbound found");
    }

    private static final class XrayBinaryManager {
        static File ensure(Context context) throws Exception {
            File dir = new File(context.getFilesDir(), "xray-bin");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create Xray directory");
            File bin = new File(dir, "xray");
            if (bin.exists() && bin.length() > 1_000_000) { bin.setExecutable(true, true); return bin; }

            String abi = Build.SUPPORTED_64_BIT_ABIS.length > 0 ? Build.SUPPORTED_64_BIT_ABIS[0] : Build.SUPPORTED_ABIS[0];
            if (!"arm64-v8a".equals(abi)) {
                throw new IllegalStateException("XHTTP requires a 64-bit ARM Android device (arm64-v8a) in this release");
            }
            String assetName = "xray/arm64-v8a/xray";
            try (InputStream in = context.getAssets().open(assetName); FileOutputStream out = new FileOutputStream(bin)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            } catch (Exception noAsset) {
                // The source package intentionally does not embed a third-party binary. A release
                // pipeline can put the official Xray Android binary in the assets path above.
                throw new IllegalStateException("Xray binary is missing. Add the official Xray Android binary to assets/" + assetName, noAsset);
            }
            bin.setExecutable(true, true);
            return bin;
        }
    }

    private static void writeText(File f, String s) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(s.getBytes(StandardCharsets.UTF_8)); }
    }

    private static void drainAsync(final InputStream input) {
        new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    SafeLog.e(TAG, "xray: " + line);
                }
            } catch (Exception e) {
                SafeLog.w(TAG, "Xray log reader stopped", e);
            }
        }, "xray-log-reader").start();
    }

    private static JSONObject parseXhttpExtra(String raw) throws Exception {
        String value = raw == null ? "" : raw.trim();
        try {
            return new JSONObject(value);
        } catch (Exception first) {
            // Some share links URL-encode a JSON value with a leading '+', e.g.
            // ":+1000000" or ":+false". After URL decoding that is still a
            // literal '+', but JSON does not permit a leading plus. Normalize it
            // without decoding the whole value a second time.
            String normalized = value.replaceAll("([:,]\\s*)\\+", "$1");
            return new JSONObject(normalized);
        }
    }

    private static boolean canConnectLocalhost(int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 100);
            return true;
        } catch (Exception e) { return false; }
    }

    private static Map<String,String> queryMap(String raw) throws Exception {
        Map<String,String> m = new LinkedHashMap<>();
        if (raw == null) return m;
        for (String part : raw.split("&")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            String k = eq >= 0 ? part.substring(0, eq) : part;
            String v = eq >= 0 ? part.substring(eq + 1) : "";
            m.put(urlDecode(k), urlDecode(v));
        }
        return m;
    }
    private static String urlDecode(String s) throws Exception { return URLDecoder.decode(s == null ? "" : s, "UTF-8"); }
    private static String first(String... values) { if (values != null) for (String v : values) if (notEmpty(v)) return v; return ""; }
    private static boolean notEmpty(String s) { return s != null && !s.trim().isEmpty(); }
    private static boolean truthy(String s) { return "1".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s); }
}
