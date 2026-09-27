package dev.dev7.lib.v2ray.core;

import android.content.Context;
import android.os.Build;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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

        if (lower.startsWith("vless://") ||
                lower.startsWith("vmess://") ||
                lower.startsWith("trojan://")) {

            return lower.contains("type=xhttp") ||
                    lower.contains("type%3dxhttp") ||
                    lower.contains("network=xhttp") ||
                    lower.contains("net=xhttp");
        }

        if (raw.startsWith("{") || raw.startsWith("[")) {
            try {
                JSONObject root = raw.startsWith("[")
                        ? new JSONArray(raw).getJSONObject(0)
                        : new JSONObject(raw);

                return containsXhttp(root);

            } catch (Exception ignored) {
            }
        }

        return false;
    }

    private static boolean containsXhttp(JSONObject root) {
        if (root == null) return false;

        if ("xhttp".equalsIgnoreCase(
                root.optString("network", "")
        )) {
            return true;
        }

        if ("xhttp".equalsIgnoreCase(
                root.optString("net", "")
        )) {
            return true;
        }

        JSONObject stream =
                root.optJSONObject("streamSettings");

        if (stream != null &&
                "xhttp".equalsIgnoreCase(
                        stream.optString("network", "")
                )) {

            return true;
        }

        JSONObject transport =
                root.optJSONObject("transport");

        if (transport != null &&
                "xhttp".equalsIgnoreCase(
                        transport.optString("type", "")
                )) {

            return true;
        }

        JSONArray outs =
                root.optJSONArray("outbounds");

        if (outs != null) {
            for (int i = 0; i < outs.length(); i++) {
                if (containsXhttp(
                        outs.optJSONObject(i)
                )) {
                    return true;
                }
            }
        }

        return false;
    }

    public static synchronized void start(
            Context context,
            String rawInput
    ) throws Exception {

        stop();

        Context app =
                context.getApplicationContext();

        File binary =
                XrayBinaryManager.ensure(app);

        SafeLog.i(
                TAG,
                "Xray binary path="
                        + binary.getAbsolutePath()
        );

        SafeLog.i(
                TAG,
                "Xray binary exists="
                        + binary.exists()
                        + " size="
                        + binary.length()
                        + " executable="
                        + binary.canExecute()
        );

        if (!binary.exists()) {
            throw new IllegalStateException(
                    "Xray binary does not exist: "
                            + binary.getAbsolutePath()
            );
        }

        if (!binary.canExecute()) {
            throw new IllegalStateException(
                    "Xray binary is not executable: "
                            + binary.getAbsolutePath()
            );
        }

        File dir =
                new File(
                        app.getFilesDir(),
                        "xray-sidecar"
                );

        if (!dir.exists() &&
                !dir.mkdirs()) {

            throw new IllegalStateException(
                    "Cannot create Xray sidecar directory"
            );
        }

        File configFile =
                new File(
                        dir,
                        "config.json"
                );

        JSONObject config =
                buildXrayConfig(rawInput);

        writeText(
                configFile,
                config.toString(2)
        );

        SafeLog.i(
                TAG,
                "Starting Xray process"
        );

        SafeLog.d(
                TAG,
                "Xray config:\n"
                        + config.toString(2)
        );

        ProcessBuilder pb =
                new ProcessBuilder(
                        binary.getAbsolutePath(),
                        "run",
                        "-c",
                        configFile.getAbsolutePath()
                );

        pb.directory(dir);
        pb.redirectErrorStream(true);

        process = pb.start();

        drainAsync(
                process.getInputStream()
        );

        long deadline =
                System.currentTimeMillis() + 5000;

        while (
                System.currentTimeMillis()
                        < deadline
        ) {

            if (!process.isAlive()) {

                throw new IllegalStateException(
                        "Xray sidecar exited immediately (exit="
                                + process.exitValue()
                                + "). See XraySidecar log for the Xray error."
                );
            }

            if (canConnectLocalhost(
                    SOCKS_PORT
            )) {

                SafeLog.i(
                        TAG,
                        "Xray sidecar ready on 127.0.0.1:"
                                + SOCKS_PORT
                );

                return;
            }

            Thread.sleep(100);
        }

        stop();

        throw new IllegalStateException(
                "Xray sidecar did not open SOCKS port "
                        + SOCKS_PORT
        );
    }

    public static synchronized void stop() {

        Process p = process;
        process = null;

        if (p != null) {

            try {
                p.destroy();
            } catch (Throwable ignored) {
            }

            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    p.destroyForcibly();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static int socksPort() {
        return SOCKS_PORT;
    }

    /** Convert the supported Xray/V2Ray share forms into a native Xray client config. */
    public static JSONObject buildXrayConfig(
            String input
    ) throws Exception {

        String raw =
                input == null
                        ? ""
                        : input.trim();

        JSONObject outbound;

        if (raw.toLowerCase(
                Locale.ROOT
        ).startsWith("vless://")) {

            outbound =
                    parseVless(raw);

        } else if (raw.startsWith("{")) {

            outbound =
                    findOutbound(
                            new JSONObject(raw)
                    );

        } else {

            throw new IllegalArgumentException(
                    "XHTTP currently requires a VLESS URI or Xray JSON"
            );
        }

        outbound.put(
                "tag",
                "xhttp-proxy"
        );

        JSONObject root =
                new JSONObject();

        root.put(
                "log",
                new JSONObject()
                        .put(
                                "loglevel",
                                "warning"
                        )
        );

        JSONArray inbounds =
                new JSONArray();

        inbounds.put(
                new JSONObject()
                        .put(
                                "tag",
                                "socks-in"
                        )
                        .put(
                                "listen",
                                "127.0.0.1"
                        )
                        .put(
                                "port",
                                SOCKS_PORT
                        )
                        .put(
                                "protocol",
                                "socks"
                        )
                        .put(
                                "settings",
                                new JSONObject()
                                        .put(
                                                "udp",
                                                true
                                        )
                        )
        );

        root.put(
                "inbounds",
                inbounds
        );

        root.put(
                "outbounds",
                new JSONArray()
                        .put(outbound)
                        .put(
                                new JSONObject()
                                        .put(
                                                "protocol",
                                                "freedom"
                                        )
                                        .put(
                                                "tag",
                                                "direct"
                                        )
                        )
        );

        return root;
    }

    private static JSONObject parseVless(
            String raw
    ) throws Exception {

        URI uri =
                URI.create(raw);

        String uuid =
                uri.getUserInfo();

        if (uuid == null ||
                uuid.isEmpty()) {

            throw new IllegalArgumentException(
                    "Missing VLESS UUID"
            );
        }

        Map<String, String> q =
                queryMap(
                        uri.getRawQuery()
                );

        String type =
                first(
                        q.get("type"),
                        q.get("net"),
                        q.get("network")
                );

        if (!"xhttp".equalsIgnoreCase(type)) {

            throw new IllegalArgumentException(
                    "Not an XHTTP VLESS config"
            );
        }

        JSONObject out =
                new JSONObject();

        out.put(
                "protocol",
                "vless"
        );

        out.put(
                "settings",
                new JSONObject()
                        .put(
                                "vnext",
                                new JSONArray()
                                        .put(
                                                new JSONObject()
                                                        .put(
                                                                "address",
                                                                uri.getHost()
                                                        )
                                                        .put(
                                                                "port",
                                                                uri.getPort() > 0
                                                                        ? uri.getPort()
                                                                        : 443
                                                        )
                                                        .put(
                                                                "users",
                                                                new JSONArray()
                                                                        .put(
                                                                                new JSONObject()
                                                                                        .put(
                                                                                                "id",
                                                                                                uuid
                                                                                        )
                                                                                        .put(
                                                                                                "encryption",
                                                                                                "none"
                                                                                        )
                                                                        )
                                                        )
                                        )
                        )
        );

        JSONObject stream =
                new JSONObject();

        stream.put(
                "network",
                "xhttp"
        );

        String security =
                first(
                        q.get("security"),
                        "none"
                );

        if ("tls".equalsIgnoreCase(security) ||
                "reality".equalsIgnoreCase(security)) {

            stream.put(
                    "security",
                    security
            );

            JSONObject tls =
                    new JSONObject();

            tls.put(
                    "serverName",
                    first(
                            q.get("sni"),
                            q.get("host"),
                            uri.getHost()
                    )
            );

            tls.put(
                    "allowInsecure",
                    truthy(q.get("allowInsecure")) ||
                            truthy(q.get("insecure"))
            );

            if (notEmpty(q.get("alpn"))) {

                tls.put(
                        "alpn",
                        new JSONArray(
                                q.get("alpn").split(",")
                        )
                );
            }

            if (notEmpty(q.get("fp"))) {
                tls.put(
                        "fingerprint",
                        q.get("fp")
                );
            }

            if ("reality".equalsIgnoreCase(
                    security
            )) {

                tls.put(
                        "publicKey",
                        first(
                                q.get("pbk"),
                                q.get("publicKey")
                        )
                );

                tls.put(
                        "shortId",
                        first(
                                q.get("sid"),
                                q.get("shortId")
                        )
                );

                tls.put(
                        "spiderX",
                        first(
                                q.get("spx"),
                                q.get("spiderX"),
                                "/"
                        )
                );
            }

            stream.put(
                    "tlsSettings",
                    tls
            );
        }

        JSONObject xhttp =
                new JSONObject();

        xhttp.put(
                "path",
                first(
                        q.get("path"),
                        "/"
                )
        );

        if (notEmpty(q.get("mode"))) {
            xhttp.put(
                    "mode",
                    q.get("mode")
            );
        }

        if (notEmpty(q.get("host"))) {
            xhttp.put(
                    "host",
                    q.get("host")
            );
        }

        if (notEmpty(q.get("extra"))) {

            JSONObject extra =
                    parseXhttpExtra(
                            q.get("extra")
                    );

            /*
             * scMaxConcurrentPosts is not used by the
             * current packet-up configuration.
             */
            extra.remove(
                    "scMaxConcurrentPosts"
            );

            xhttp.put(
                    "extra",
                    extra
            );
        }

        stream.put(
                "xhttpSettings",
                xhttp
        );

        out.put(
                "streamSettings",
                stream
        );

        return out;
    }

    private static JSONObject findOutbound(
            JSONObject root
    ) throws Exception {

        if (root.has("protocol")) {
            return new JSONObject(
                    root.toString()
            );
        }

        if (root.has("outbounds")) {

            JSONArray a =
                    root.getJSONArray(
                            "outbounds"
                    );

            for (int i = 0;
                 i < a.length();
                 i++) {

                JSONObject o =
                        a.optJSONObject(i);

                if (o != null &&
                        "vless".equalsIgnoreCase(
                                o.optString(
                                        "protocol",
                                        o.optString(
                                                "type",
                                                ""
                                        )
                                )
                        )) {

                    return new JSONObject(
                            o.toString()
                    );
                }
            }
        }

        throw new IllegalArgumentException(
                "No VLESS outbound found"
        );
    }

    /*
     * IMPORTANT:
     *
     * Xray is no longer copied into:
     *
     *     context.getFilesDir()/xray-bin/xray
     *
     * because some Android devices mount app-private files
     * with noexec, producing:
     *
     *     error=13, Permission denied
     *
     * Instead Xray is packaged as:
     *
     *     v2ray/libs/arm64-v8a/libxray.so
     *
     * Android extracts it into:
     *
     *     ApplicationInfo.nativeLibraryDir
     *
     * and we execute that file directly.
     */
    private static final class XrayBinaryManager {

        static File ensure(
                Context context
        ) throws Exception {

            String abi;

            if (Build.SUPPORTED_64_BIT_ABIS.length > 0) {

                abi =
                        Build.SUPPORTED_64_BIT_ABIS[0];

            } else if (Build.SUPPORTED_ABIS.length > 0) {

                abi =
                        Build.SUPPORTED_ABIS[0];

            } else {

                throw new IllegalStateException(
                        "Cannot determine Android CPU ABI"
                );
            }

            SafeLog.i(
                    TAG,
                    "Android ABI selected="
                            + abi
            );

            if (!"arm64-v8a".equals(abi)) {

                throw new IllegalStateException(
                        "XHTTP requires a 64-bit ARM Android device (arm64-v8a) in this release"
                );
            }

            String nativeLibraryDir =
                    context.getApplicationInfo()
                            .nativeLibraryDir;

            if (nativeLibraryDir == null ||
                    nativeLibraryDir.trim().isEmpty()) {

                throw new IllegalStateException(
                        "Android nativeLibraryDir is unavailable"
                );
            }

            File nativeDir =
                    new File(
                            nativeLibraryDir
                    );

            SafeLog.i(
                    TAG,
                    "Android nativeLibraryDir="
                            + nativeDir.getAbsolutePath()
            );

            File bin =
                    new File(
                            nativeDir,
                            "libxray.so"
                    );

            SafeLog.i(
                    TAG,
                    "Native Xray binary path="
                            + bin.getAbsolutePath()
            );

            SafeLog.i(
                    TAG,
                    "Native Xray binary exists="
                            + bin.exists()
                            + " size="
                            + (
                            bin.exists()
                                    ? bin.length()
                                    : 0
                    )
                            + " executable="
                            + bin.canExecute()
            );

            if (!bin.exists()) {

                throw new IllegalStateException(
                        "Xray native binary not found: "
                                + bin.getAbsolutePath()
                                + ". Check that libxray.so is packaged under "
                                + "v2ray/libs/arm64-v8a/"
                );
            }

            if (!bin.isFile()) {

                throw new IllegalStateException(
                        "Xray native binary is not a regular file: "
                                + bin.getAbsolutePath()
                );
            }

            if (bin.length() < 1_000_000) {

                throw new IllegalStateException(
                        "Xray native binary is unexpectedly small: "
                                + bin.length()
                                + " bytes"
                );
            }

            if (!bin.canExecute()) {

                SafeLog.e(
                        TAG,
                        "Xray native binary exists but is not executable: "
                                + bin.getAbsolutePath()
                );

                throw new IllegalStateException(
                        "Android native Xray binary is not executable: "
                                + bin.getAbsolutePath()
                );
            }

            SafeLog.i(
                    TAG,
                    "Using executable Xray from nativeLibraryDir"
            );

            return bin;
        }
    }

    private static void writeText(
            File f,
            String s
    ) throws Exception {

        try (
                FileOutputStream out =
                        new FileOutputStream(f)
        ) {

            out.write(
                    s.getBytes(
                            StandardCharsets.UTF_8
                    )
            );
        }
    }

    private static void drainAsync(
            final InputStream input
    ) {

        new Thread(
                () -> {

                    try (
                            java.io.BufferedReader reader =
                                    new java.io.BufferedReader(
                                            new java.io.InputStreamReader(
                                                    input,
                                                    StandardCharsets.UTF_8
                                            )
                                    )
                    ) {

                        String line;

                        while (
                                (line = reader.readLine())
                                        != null
                        ) {

                            SafeLog.e(
                                    TAG,
                                    "xray: "
                                            + line
                            );
                        }

                    } catch (Exception e) {

                        SafeLog.w(
                                TAG,
                                "Xray log reader stopped",
                                e
                        );
                    }
                },
                "xray-log-reader"
        ).start();
    }

    private static JSONObject parseXhttpExtra(
            String raw
    ) throws Exception {

        String value =
                raw == null
                        ? ""
                        : raw.trim();

        try {

            return new JSONObject(
                    value
            );

        } catch (Exception first) {

            String normalized =
                    value.replaceAll(
                            "([:,]\\s*)\\+",
                            "$1"
                    );

            return new JSONObject(
                    normalized
            );
        }
    }

    private static boolean canConnectLocalhost(
            int port
    ) {

        try (
                Socket s =
                        new Socket()
        ) {

            s.connect(
                    new InetSocketAddress(
                            "127.0.0.1",
                            port
                    ),
                    100
            );

            return true;

        } catch (Exception e) {

            return false;
        }
    }

    private static Map<String, String> queryMap(
            String raw
    ) throws Exception {

        Map<String, String> m =
                new LinkedHashMap<>();

        if (raw == null) return m;

        for (String part :
                raw.split("&")) {

            if (part.isEmpty()) continue;

            int eq =
                    part.indexOf('=');

            String k =
                    eq >= 0
                            ? part.substring(
                                    0,
                                    eq
                            )
                            : part;

            String v =
                    eq >= 0
                            ? part.substring(
                                    eq + 1
                            )
                            : "";

            m.put(
                    urlDecode(k),
                    urlDecode(v)
            );
        }

        return m;
    }

    private static String urlDecode(
            String s
    ) throws Exception {

        return URLDecoder.decode(
                s == null
                        ? ""
                        : s,
                "UTF-8"
        );
    }

    private static String first(
            String... values
    ) {

        if (values != null) {

            for (String v : values) {

                if (notEmpty(v)) {
                    return v;
                }
            }
        }

        return "";
    }

    private static boolean notEmpty(
            String s
    ) {

        return s != null &&
                !s.trim().isEmpty();
    }

    private static boolean truthy(
            String s
    ) {

        return "1".equalsIgnoreCase(s) ||
                "true".equalsIgnoreCase(s);
    }
}