package dev.dev7.lib.v2ray.core;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import dev.dev7.lib.v2ray.utils.SafeLog;

public final class SingBoxConfigBuilder {
    private SingBoxConfigBuilder() {}

    public static String buildAutoConfig(String input) throws Exception {
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) throw new IllegalArgumentException("Config is empty");
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("ssh://")) return wrapProxyOutbound(parseSshUri(raw));
        if (raw.startsWith("{") || raw.startsWith("[")) {
            Object parsed = raw.startsWith("[") ? new JSONArray(raw) : new JSONObject(raw);
            if (parsed instanceof JSONObject) {
                JSONObject obj = (JSONObject) parsed;
                if (obj.has("type") && "ssh".equalsIgnoreCase(obj.optString("type"))) {
                    JSONObject copy = deepCopy(obj);
                    copy.put("tag", "proxy");
                    return wrapProxyOutbound(copy);
                }
                if (looksLikeCompleteSingBoxConfig(obj)) return obj.toString(2);
            }
        }
        return buildV2RayConfig(raw);
    }

    public static String detectProtocol(String input) {
        String raw = input == null ? "" : input.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("ssh://")) return "ssh";
        try {
            if (raw.startsWith("{")) {
                JSONObject obj = new JSONObject(raw);
                if ("ssh".equalsIgnoreCase(obj.optString("type"))) return "ssh";
                if (obj.has("outbounds")) {
                    JSONArray outs = obj.getJSONArray("outbounds");
                    for (int i = 0; i < outs.length(); i++) {
                        JSONObject outbound = outs.getJSONObject(i);
                        if ("ssh".equalsIgnoreCase(outbound.optString("type"))) return "ssh";
                    }
                }
            }
        } catch (Exception ignored) {}
        return "v2ray";
    }

    public static final class Endpoint {
        public final String host;
        public final int port;
        public final String protocol;
        public Endpoint(String protocol, String host, int port) {
            this.protocol = protocol;
            this.host = host;
            this.port = port;
        }
    }

    public static Endpoint extractEndpoint(String input) {
        String raw = input == null ? "" : input.trim();
        try {
            String lower = raw.toLowerCase(Locale.ROOT);
            if (lower.startsWith("ssh://")) {
                URI uri = URI.create(raw);
                return new Endpoint("ssh", requireHost(uri), uri.getPort() > 0 ? uri.getPort() : 22);
            }
            if (lower.startsWith("vless://")) {
                URI uri = URI.create(raw);
                return new Endpoint("vless", requireHost(uri), uri.getPort() > 0 ? uri.getPort() : defaultPort(queryMap(uri.getRawQuery())));
            }
            if (lower.startsWith("trojan://")) {
                URI uri = URI.create(raw);
                return new Endpoint("trojan", requireHost(uri), uri.getPort() > 0 ? uri.getPort() : defaultPort(queryMap(uri.getRawQuery())));
            }
            if (lower.startsWith("vmess://")) {
                String data = raw.substring("vmess://".length()).trim();
                int hash = data.indexOf('#');
                if (hash >= 0) data = data.substring(0, hash);
                JSONObject vm = new JSONObject(base64DecodeToString(data));
                return new Endpoint("vmess", vm.optString("add", vm.optString("server", "")), parseInt(vm.optString("port", "443"), 443));
            }
            if (lower.startsWith("ss://")) {
                JSONObject ss = parseShadowsocksUri(raw);
                return new Endpoint("shadowsocks", ss.optString("server"), ss.optInt("server_port", 8388));
            }
            if (raw.startsWith("{") || raw.startsWith("[")) {
                String full = buildAutoConfig(raw);
                JSONObject obj = new JSONObject(full);
                JSONArray outs = obj.getJSONArray("outbounds");
                for (int i = 0; i < outs.length(); i++) {
                    JSONObject out = outs.getJSONObject(i);
                    String type = out.optString("type", out.optString("protocol", ""));
                    if ("direct".equals(type) || "block".equals(type)) continue;
                    String host = out.optString("server", "");
                    int port = out.optInt("server_port", out.optInt("port", 443));
                    if (!host.isEmpty()) return new Endpoint(type, host, port);
                }
            }
        } catch (Exception ignored) {}
        return new Endpoint("unknown", "", 443);
    }

    private static JSONObject parseSshUri(String raw) throws Exception {
        URI uri = URI.create(raw);
        String userInfo = uri.getRawUserInfo() == null ? "" : uri.getRawUserInfo();
        String user = "";
        String password = "";
        int colon = userInfo.indexOf(':');
        if (colon >= 0) {
            user = urlDecode(userInfo.substring(0, colon));
            password = urlDecode(userInfo.substring(colon + 1));
        } else {
            user = urlDecode(userInfo);
        }
        JSONObject out = new JSONObject();
        out.put("type", "ssh");
        out.put("tag", "proxy");
        out.put("server", requireHost(uri));
        out.put("server_port", uri.getPort() > 0 ? uri.getPort() : 22);
        out.put("user", user);
        out.put("password", password);
        out.put("client_version", "SSH-2.0-OpenSSH_8.9");
        return out;
    }

    public static String buildSshOnlyConfig(String server, int port, String user, String password) throws Exception {
        JSONObject proxy = new JSONObject();
        proxy.put("type", "ssh");
        proxy.put("tag", "proxy");
        proxy.put("server", nullToEmpty(server));
        proxy.put("server_port", port <= 0 ? 22 : port);
        proxy.put("user", nullToEmpty(user));
        proxy.put("password", nullToEmpty(password));
        proxy.put("client_version", "SSH-2.0-OpenSSH_8.9");
        return wrapProxyOutbound(proxy);
    }

    /** Build the TUN side of the XHTTP bridge. Xray itself runs as a local SOCKS sidecar. */
    public static String buildXhttpBridgeConfig(int socksPort) throws Exception {
        JSONObject root = baseConfigSkeleton();
        JSONArray outbounds = new JSONArray();
        JSONObject proxy = new JSONObject();
        proxy.put("type", "socks");
        proxy.put("tag", "proxy");
        proxy.put("server", "127.0.0.1");
        proxy.put("server_port", socksPort);
        proxy.put("version", "5");
        outbounds.put(proxy);
        outbounds.put(new JSONObject().put("type", "direct").put("tag", "direct"));
        outbounds.put(new JSONObject().put("type", "block").put("tag", "block"));
        root.put("outbounds", outbounds);
        return root.toString(2);
    }

    public static String buildV2RayConfig(String input) throws Exception {
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) throw new IllegalArgumentException("V2Ray link or JSON is empty");

        if (raw.startsWith("{") || raw.startsWith("[")) {
            return buildFromJson(raw);
        }

        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("vless://")) return wrapProxyOutbound(parseVlessUri(raw));
        if (lower.startsWith("vmess://")) return wrapProxyOutbound(parseVmessUri(raw));
        if (lower.startsWith("trojan://")) return wrapProxyOutbound(parseTrojanUri(raw));
        if (lower.startsWith("ss://")) return wrapProxyOutbound(parseShadowsocksUri(raw));

        throw new IllegalArgumentException("Unsupported config. Paste vless:// vmess:// trojan:// ss:// or JSON.");
    }

    private static String buildFromJson(String raw) throws Exception {
        Object parsed = raw.startsWith("[") ? new JSONArray(raw) : new JSONObject(raw);
        if (parsed instanceof JSONArray) {
            JSONArray arr = (JSONArray) parsed;
            if (arr.length() == 0) throw new IllegalArgumentException("Empty JSON array");
            JSONObject first = arr.getJSONObject(0);
            return wrapProxyOutbound(convertJsonOutbound(first));
        }

        JSONObject obj = (JSONObject) parsed;

        // Full sing-box config: keep it as-is.
        if (looksLikeCompleteSingBoxConfig(obj)) {
            return obj.toString(2);
        }

        // sing-box outbound object or Xray/V2Ray outbound object.
        if (obj.has("type") || obj.has("protocol")) {
            return wrapProxyOutbound(convertJsonOutbound(obj));
        }

        // V2Ray/Xray full config: find the first outbound and convert it.
        if (obj.has("outbounds")) {
            JSONArray outbounds = obj.getJSONArray("outbounds");
            for (int i = 0; i < outbounds.length(); i++) {
                JSONObject outbound = outbounds.getJSONObject(i);
                if (outbound.has("type") || outbound.has("protocol")) {
                    return wrapProxyOutbound(convertJsonOutbound(outbound));
                }
            }
        }

        throw new IllegalArgumentException("JSON was parsed, but no supported outbound was found");
    }

    private static boolean looksLikeCompleteSingBoxConfig(JSONObject obj) {
        try {
            if (!obj.has("inbounds") || !obj.has("outbounds")) return false;
            JSONArray outbounds = obj.getJSONArray("outbounds");
            if (outbounds.length() == 0) return false;
            JSONObject first = outbounds.getJSONObject(0);
            return first.has("type");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static JSONObject convertJsonOutbound(JSONObject outbound) throws Exception {
        if (outbound.has("type")) {
            JSONObject copy = deepCopy(outbound);
            copy.put("tag", "proxy");
            return copy;
        }
        if (!outbound.has("protocol")) throw new IllegalArgumentException("Outbound has no type/protocol");

        String protocol = outbound.getString("protocol").toLowerCase(Locale.ROOT);
        if ("vless".equals(protocol)) return parseXrayVnext(outbound, "vless");
        if ("vmess".equals(protocol)) return parseXrayVnext(outbound, "vmess");
        if ("trojan".equals(protocol)) return parseXrayServerList(outbound, "trojan");
        if ("shadowsocks".equals(protocol) || "ss".equals(protocol)) return parseXrayServerList(outbound, "shadowsocks");

        throw new IllegalArgumentException("Unsupported JSON outbound protocol: " + protocol);
    }

    private static JSONObject parseVlessUri(String raw) throws Exception {
        URI uri = URI.create(raw);
        Map<String, String> q = queryMap(uri.getRawQuery());
        String server = requireHost(uri);
        int port = uri.getPort() > 0 ? uri.getPort() : defaultPort(q);
        String uuid = urlDecode(uri.getRawUserInfo());

        JSONObject out = new JSONObject();
        out.put("type", "vless");
        out.put("tag", "proxy");
        out.put("server", server);
        out.put("server_port", port);
        out.put("uuid", uuid);
        if (notEmpty(q.get("flow"))) out.put("flow", q.get("flow"));

        addTlsFromQuery(out, q, server, false);
        addTransportFromQuery(out, q);
        return out;
    }

    private static JSONObject parseVmessUri(String raw) throws Exception {
        String data = raw.substring("vmess://".length()).trim();
        int hash = data.indexOf('#');
        if (hash >= 0) data = data.substring(0, hash);
        String json = base64DecodeToString(data);
        JSONObject vm = new JSONObject(json);

        JSONObject out = new JSONObject();
        out.put("type", "vmess");
        out.put("tag", "proxy");
        out.put("server", vm.optString("add", vm.optString("server", "")));
        out.put("server_port", parseInt(vm.optString("port", "443"), 443));
        out.put("uuid", vm.optString("id", vm.optString("uuid", "")));
        out.put("security", vm.optString("scy", vm.optString("security", "auto")));
        int alterId = parseInt(vm.optString("aid", vm.optString("alterId", "0")), 0);
        if (alterId > 0) out.put("alter_id", alterId);

        Map<String, String> q = new LinkedHashMap<>();
        q.put("type", vm.optString("net", "tcp"));
        q.put("security", notEmpty(vm.optString("tls", "")) ? vm.optString("tls", "") : vm.optString("security", ""));
        q.put("sni", vm.optString("sni", vm.optString("peer", "")));
        q.put("host", vm.optString("host", ""));
        q.put("path", vm.optString("path", ""));
        q.put("serviceName", vm.optString("serviceName", vm.optString("service_name", "")));
        addTlsFromQuery(out, q, out.optString("server"), false);
        addTransportFromQuery(out, q);
        return out;
    }

    private static JSONObject parseTrojanUri(String raw) throws Exception {
        URI uri = URI.create(raw);
        Map<String, String> q = queryMap(uri.getRawQuery());
        String server = requireHost(uri);
        int port = uri.getPort() > 0 ? uri.getPort() : defaultPort(q);
        String password = urlDecode(uri.getRawUserInfo());

        JSONObject out = new JSONObject();
        out.put("type", "trojan");
        out.put("tag", "proxy");
        out.put("server", server);
        out.put("server_port", port);
        out.put("password", password);
        addTlsFromQuery(out, q, server, true);
        addTransportFromQuery(out, q);
        return out;
    }

    private static JSONObject parseShadowsocksUri(String raw) throws Exception {
        String s = raw.substring("ss://".length());
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash);
        String query = null;
        int qIndex = s.indexOf('?');
        if (qIndex >= 0) {
            query = s.substring(qIndex + 1);
            s = s.substring(0, qIndex);
        }

        String userInfo;
        String hostPort;
        int at = s.lastIndexOf('@');
        if (at >= 0) {
            userInfo = s.substring(0, at);
            hostPort = s.substring(at + 1);
            if (!userInfo.contains(":")) userInfo = base64DecodeToString(userInfo);
        } else {
            String decoded = base64DecodeToString(s);
            at = decoded.lastIndexOf('@');
            if (at < 0) throw new IllegalArgumentException("Invalid ss:// link");
            userInfo = decoded.substring(0, at);
            hostPort = decoded.substring(at + 1);
        }

        int colon = userInfo.indexOf(':');
        if (colon < 0) throw new IllegalArgumentException("Invalid ss:// credentials");
        String method = urlDecode(userInfo.substring(0, colon));
        String password = urlDecode(userInfo.substring(colon + 1));
        HostPort hp = parseHostPort(hostPort, 8388);

        JSONObject out = new JSONObject();
        out.put("type", "shadowsocks");
        out.put("tag", "proxy");
        out.put("server", hp.host);
        out.put("server_port", hp.port);
        out.put("method", method);
        out.put("password", password);

        Map<String, String> q = queryMap(query);
        if (notEmpty(q.get("plugin"))) {
            out.put("plugin", q.get("plugin"));
            if (notEmpty(q.get("plugin-opts"))) out.put("plugin_opts", q.get("plugin-opts"));
        }
        return out;
    }

    private static JSONObject parseXrayVnext(JSONObject outbound, String type) throws Exception {
        JSONObject settings = outbound.getJSONObject("settings");
        JSONArray vnext = settings.getJSONArray("vnext");
        JSONObject node = vnext.getJSONObject(0);
        JSONObject user = node.getJSONArray("users").getJSONObject(0);

        JSONObject out = new JSONObject();
        out.put("type", type);
        out.put("tag", "proxy");
        out.put("server", node.getString("address"));
        out.put("server_port", node.getInt("port"));
        out.put("uuid", user.optString("id", user.optString("uuid", "")));
        if ("vmess".equals(type)) {
            out.put("security", user.optString("security", "auto"));
            int alterId = user.optInt("alterId", user.optInt("alter_id", 0));
            if (alterId > 0) out.put("alter_id", alterId);
        } else {
            if (notEmpty(user.optString("flow", ""))) out.put("flow", user.optString("flow"));
        }
        addXrayStreamSettings(out, outbound.optJSONObject("streamSettings"), out.optString("server"));
        return out;
    }

    private static JSONObject parseXrayServerList(JSONObject outbound, String type) throws Exception {
        JSONObject settings = outbound.getJSONObject("settings");
        JSONArray servers = settings.getJSONArray("servers");
        JSONObject server = servers.getJSONObject(0);

        JSONObject out = new JSONObject();
        out.put("type", type);
        out.put("tag", "proxy");
        out.put("server", server.optString("address", server.optString("server", "")));
        out.put("server_port", server.optInt("port", 443));
        if ("trojan".equals(type)) {
            out.put("password", server.optString("password", ""));
        } else {
            out.put("method", server.optString("method", "aes-128-gcm"));
            out.put("password", server.optString("password", ""));
        }
        addXrayStreamSettings(out, outbound.optJSONObject("streamSettings"), out.optString("server"));
        return out;
    }

    private static void addXrayStreamSettings(JSONObject out, JSONObject stream, String server) throws Exception {
        if (stream == null) return;
        String network = stream.optString("network", "tcp");
        String security = stream.optString("security", "none");
        Map<String, String> q = new LinkedHashMap<>();
        q.put("type", network);
        q.put("security", security);

        JSONObject tls = stream.optJSONObject("tlsSettings");
        if (tls != null) {
            q.put("sni", tls.optString("serverName", tls.optString("server_name", "")));
            q.put("allowInsecure", String.valueOf(tls.optBoolean("allowInsecure", false)));

            // Xray/V2Ray often relies on browser-like TLS fingerprints.
            // Without copying fingerprint -> sing-box uTLS, some CDN/TLS endpoints accept the
            // connection in Xray but silently fail or timeout in sing-box.
            String fingerprint = tls.optString("fingerprint", "");
            if (notEmpty(fingerprint)) q.put("fp", fingerprint);

            JSONArray alpn = tls.optJSONArray("alpn");
            if (alpn != null && alpn.length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < alpn.length(); i++) {
                    String value = alpn.optString(i, "").trim();
                    if (value.isEmpty()) continue;
                    if (sb.length() > 0) sb.append(',');
                    sb.append(value);
                }
                if (sb.length() > 0) q.put("alpn", sb.toString());
            }
        }
        JSONObject reality = stream.optJSONObject("realitySettings");
        if (reality != null) {
            q.put("security", "reality");
            q.put("sni", reality.optString("serverName", reality.optString("server_name", "")));
            q.put("pbk", reality.optString("publicKey", reality.optString("public_key", "")));
            q.put("sid", reality.optString("shortId", reality.optString("short_id", "")));
            q.put("fp", reality.optString("fingerprint", "chrome"));
        }
        JSONObject ws = stream.optJSONObject("wsSettings");
        if (ws != null) {
            q.put("type", "ws");
            q.put("path", ws.optString("path", "/"));
            JSONObject headers = ws.optJSONObject("headers");
            if (headers != null) q.put("host", headers.optString("Host", headers.optString("host", "")));
        }
        JSONObject grpc = stream.optJSONObject("grpcSettings");
        if (grpc != null) {
            q.put("type", "grpc");
            q.put("serviceName", grpc.optString("serviceName", grpc.optString("service_name", "")));
        }

        addTlsFromQuery(out, q, server, false);
        addTransportFromQuery(out, q);
    }

    private static void addTlsFromQuery(JSONObject out, Map<String, String> q, String server, boolean defaultTls) throws Exception {
        String security = lower(q.get("security"));
        boolean tlsEnabled = defaultTls || "tls".equals(security) || "reality".equals(security);
        if (!tlsEnabled || "none".equals(security)) return;

        JSONObject tls = new JSONObject();
        tls.put("enabled", true);
        String sni = firstNonEmpty(q.get("sni"), q.get("peer"), q.get("serverName"), q.get("servername"), server);
        if (notEmpty(sni)) tls.put("server_name", sni);
        if (isTrue(q.get("allowInsecure")) || isTrue(q.get("insecure")) || isTrue(q.get("skip-cert-verify"))) tls.put("insecure", true);

        String alpn = q.get("alpn");
        if (notEmpty(alpn)) {
            JSONArray arr = new JSONArray();
            String transportType = lower(firstNonEmpty(q.get("type"), q.get("net"), q.get("network")));
            for (String item : alpn.split(",")) {
                String v = item.trim();
                if (v.isEmpty()) continue;
                // WebSocket in sing-box should negotiate HTTP/1.1. Some Xray exports include
                // [h2,http/1.1], but h2 can break ws handshakes in sing-box/CDN setups.
                if (("ws".equals(transportType) || "websocket".equals(transportType)) && "h2".equalsIgnoreCase(v)) {
                    continue;
                }
                arr.put(v);
            }
            if (arr.length() == 0 && ("ws".equals(transportType) || "websocket".equals(transportType))) {
                arr.put("http/1.1");
            }
            if (arr.length() > 0) tls.put("alpn", arr);
        }

        String fp = firstNonEmpty(q.get("fp"), q.get("fingerprint"));
        if (notEmpty(fp)) {
            JSONObject utls = new JSONObject();
            utls.put("enabled", true);
            utls.put("fingerprint", fp);
            tls.put("utls", utls);
        }

        if ("reality".equals(security)) {
            JSONObject reality = new JSONObject();
            reality.put("enabled", true);
            if (notEmpty(q.get("pbk"))) reality.put("public_key", q.get("pbk"));
            if (notEmpty(q.get("sid"))) reality.put("short_id", q.get("sid"));
            if (notEmpty(q.get("spx"))) reality.put("spider_x", q.get("spx"));
            tls.put("reality", reality);
        }
        out.put("tls", tls);
    }

    private static void addTransportFromQuery(JSONObject out, Map<String, String> q) throws Exception {
        String type = lower(firstNonEmpty(q.get("type"), q.get("net"), q.get("network")));
        if (!notEmpty(type) || "tcp".equals(type)) return;
        // XHTTP is handled by the Xray sidecar path. Never silently downgrade it to HTTP.
        if ("xhttp".equals(type)) throw new IllegalArgumentException("XHTTP must be handled by Xray sidecar");
        if ("h2".equals(type)) type = "http";

        JSONObject tr = new JSONObject();
        tr.put("type", type);
        if ("ws".equals(type) || "websocket".equals(type)) {
            tr.put("type", "ws");
            String path = firstNonEmpty(q.get("path"), "/");
            tr.put("path", path);
            String host = firstNonEmpty(q.get("host"), q.get("wsHost"));
            if (notEmpty(host)) {
                JSONObject headers = new JSONObject();
                headers.put("Host", host);
                tr.put("headers", headers);
            }
        } else if ("grpc".equals(type)) {
            String service = firstNonEmpty(q.get("serviceName"), q.get("service_name"), q.get("service"));
            if (notEmpty(service)) tr.put("service_name", service);
        } else if ("httpupgrade".equals(type) || "http".equals(type)) {
            String path = firstNonEmpty(q.get("path"), "/");
            tr.put("path", path);
            String host = firstNonEmpty(q.get("host"), q.get("httpHost"));
            if (notEmpty(host)) {
                JSONArray hosts = new JSONArray();
                for (String item : host.split(",")) {
                    String v = item.trim();
                    if (!v.isEmpty()) hosts.put(v);
                }
                if (hosts.length() > 0) tr.put("host", hosts);
            }
        } else {
            // Keep unknown transport type so sing-box can validate it. This is useful for newer cores.
            String path = q.get("path");
            if (notEmpty(path)) tr.put("path", path);
        }
        out.put("transport", tr);
    }

    private static int defaultPort(Map<String, String> q) {
        String security = lower(q.get("security"));
        return ("tls".equals(security) || "reality".equals(security)) ? 443 : 80;
    }

    private static String wrapProxyOutbound(JSONObject proxy) throws Exception {
        proxy.put("tag", "proxy");
        prepareProxyOutbound(proxy);
        JSONObject root = baseConfigSkeleton();
        JSONArray outbounds = new JSONArray();
        outbounds.put(proxy);
        outbounds.put(new JSONObject().put("type", "direct").put("tag", "direct"));
        outbounds.put(new JSONObject().put("type", "block").put("tag", "block"));
        root.put("outbounds", outbounds);
        return root.toString(2);
    }

    private static void prepareProxyOutbound(JSONObject proxy) throws Exception {
        if (proxy == null) return;

        // IMPORTANT bootstrap fix:
        // In Android libcore, using sing-box "local" domain_resolver calls LocalDNSTransport.lookup().
        // In the host app this may block after TUN starts, so the core starts correctly but no data
        // passes. Resolve ONLY the proxy endpoint before starting the core, then pass the endpoint IP
        // to sing-box. Keep SNI / WS Host as the original domain so TLS/WS nodes still behave like Xray.
        String originalServer = proxy.optString("server", "").trim();
        if (notEmpty(originalServer) && !looksLikeIpAddress(originalServer)) {
            preserveOriginalHostForTlsAndTransport(proxy, originalServer);
            String resolved = resolveIPv4WithTimeout(originalServer, 4500);
            if (notEmpty(resolved)) {
                SafeLog.i("SingBoxConfigBuilder", "Bootstrap resolved proxy endpoint " + originalServer + " -> " + resolved);
                proxy.put("server", resolved);
                proxy.remove("domain_resolver");
                proxy.remove("domain_strategy");
            } else {
                // Last-resort fallback. Do NOT set domain_resolver=local here; it is the reason the
                // current build stalls at LocalDNSTransport.lookup(). Let sing-box handle it and log it.
                SafeLog.w("SingBoxConfigBuilder", "Bootstrap resolve failed for " + originalServer + "; leaving domain as-is without local resolver");
                proxy.remove("domain_resolver");
                proxy.remove("domain_strategy");
            }
        } else {
            proxy.remove("domain_resolver");
            proxy.remove("domain_strategy");
        }

        String type = lower(proxy.optString("type", ""));
        if ("vless".equals(type)) {
            // For VLESS, xudp is only needed for UDP. It can confuse debugging of simple TCP nodes,
            // so leave it absent unless the original input explicitly provided packet_encoding.
            if (proxy.optString("packet_encoding", "").trim().isEmpty()) {
                proxy.remove("packet_encoding");
            }
        }
    }

    private static void preserveOriginalHostForTlsAndTransport(JSONObject proxy, String originalServer) throws Exception {
        JSONObject tls = proxy.optJSONObject("tls");
        if (tls != null && tls.optBoolean("enabled", false) && !notEmpty(tls.optString("server_name", ""))) {
            tls.put("server_name", originalServer);
        }

        JSONObject transport = proxy.optJSONObject("transport");
        if (transport == null) return;
        String transportType = lower(transport.optString("type", ""));
        if ("ws".equals(transportType) || "httpupgrade".equals(transportType)) {
            JSONObject headers = transport.optJSONObject("headers");
            if (headers == null) {
                headers = new JSONObject();
                transport.put("headers", headers);
            }
            if (!headers.has("Host") && !headers.has("host")) {
                headers.put("Host", originalServer);
            }
        }
        if ("http".equals(transportType) || "h2".equals(transportType)) {
            if (!transport.has("host")) {
                transport.put("host", new JSONArray().put(originalServer));
            }
        }
    }

    private static String resolveIPv4WithTimeout(String host, long timeoutMs) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    InetAddress[] addresses = InetAddress.getAllByName(host);
                    String first = null;
                    for (InetAddress address : addresses) {
                        String ip = address.getHostAddress();
                        if (ip == null || ip.isEmpty()) continue;
                        if (first == null) first = ip;
                        if (address instanceof Inet4Address && ip.indexOf(':') < 0) return ip;
                    }
                    return first;
                }
            });
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            SafeLog.w("SingBoxConfigBuilder", "resolveIPv4WithTimeout failed for " + host, t);
            return null;
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean looksLikeIpAddress(String host) {
        if (host == null) return false;
        String h = host.trim();
        if (h.isEmpty()) return false;
        if (h.indexOf(':') >= 0) return true; // IPv6-ish
        return h.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
    }

    private static JSONObject baseConfigSkeleton() throws Exception {
        JSONObject root = new JSONObject();
        root.put("log", new JSONObject().put("level", "info").put("timestamp", true));

        JSONObject dns = new JSONObject();
        JSONArray servers = new JSONArray();
        servers.put(new JSONObject().put("tag", "remote").put("address", "https://1.1.1.1/dns-query").put("detour", "proxy"));
        // Keep a local resolver available for complete user-provided sing-box JSON compatibility,
        // but the generated configs no longer use it for proxy endpoint bootstrap.
        servers.put(new JSONObject().put("tag", "local").put("address", "local"));
        dns.put("servers", servers);
        dns.put("final", "remote");
        dns.put("strategy", "ipv4_only");
        dns.put("reverse_mapping", true);
        root.put("dns", dns);

        JSONObject tun = new JSONObject();
        tun.put("type", "tun");
        tun.put("tag", "tun-in");
        tun.put("interface_name", "tun0");
        tun.put("address", new JSONArray().put("172.19.0.1/30"));
        tun.put("mtu", 1400);
        tun.put("auto_route", true);
        tun.put("strict_route", true);
        tun.put("stack", "gvisor");
        tun.put("sniff", true);
        root.put("inbounds", new JSONArray().put(tun));

        JSONArray rules = new JSONArray();
        rules.put(new JSONObject().put("port", 53).put("action", "hijack-dns"));
        rules.put(new JSONObject().put("protocol", "dns").put("action", "hijack-dns"));
        rules.put(new JSONObject().put("protocol", "quic").put("action", "reject"));
        rules.put(new JSONObject().put("ip_cidr", new JSONArray().put("224.0.0.0/3").put("ff00::/8")).put("action", "reject"));
        JSONObject route = new JSONObject();
        route.put("auto_detect_interface", true);
        // Do not set default_domain_resolver=local. In gomobile libcore it triggers
        // LocalDNSTransport.lookup() after the TUN starts and can stall traffic. Proxy endpoint
        // domains are pre-resolved in prepareProxyOutbound() instead.
        route.put("final", "proxy");
        route.put("rules", rules);
        root.put("route", route);
        return root;
    }

    private static Map<String, String> queryMap(String rawQuery) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return map;
        for (String part : rawQuery.split("&")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            String k = eq >= 0 ? part.substring(0, eq) : part;
            String v = eq >= 0 ? part.substring(eq + 1) : "";
            map.put(urlDecode(k), urlDecode(v));
        }
        return map;
    }

    private static HostPort parseHostPort(String hostPort, int defaultPort) throws Exception {
        String host;
        int port = defaultPort;
        if (hostPort.startsWith("[")) {
            int end = hostPort.indexOf(']');
            if (end < 0) throw new IllegalArgumentException("Invalid IPv6 host");
            host = hostPort.substring(1, end);
            if (end + 2 <= hostPort.length() && hostPort.charAt(end + 1) == ':') port = parseInt(hostPort.substring(end + 2), defaultPort);
        } else {
            int colon = hostPort.lastIndexOf(':');
            if (colon >= 0) {
                host = hostPort.substring(0, colon);
                port = parseInt(hostPort.substring(colon + 1), defaultPort);
            } else {
                host = hostPort;
            }
        }
        return new HostPort(urlDecode(host), port);
    }

    private static String requireHost(URI uri) throws Exception {
        String host = uri.getHost();
        if (host != null && !host.isEmpty()) return host;
        String authority = uri.getRawAuthority();
        if (authority == null) throw new IllegalArgumentException("Missing host");
        int at = authority.lastIndexOf('@');
        if (at >= 0) authority = authority.substring(at + 1);
        HostPort hp = parseHostPort(authority, -1);
        if (hp.host == null || hp.host.isEmpty()) throw new IllegalArgumentException("Missing host");
        return hp.host;
    }

    private static JSONObject deepCopy(JSONObject obj) throws Exception {
        return new JSONObject(obj.toString());
    }

    private static String base64DecodeToString(String input) throws Exception {
        String s = input.trim().replace('-', '+').replace('_', '/');
        while (s.length() % 4 != 0) s += "=";
        byte[] bytes;
        try {
            bytes = Base64.decode(s, Base64.NO_WRAP);
        } catch (Exception e) {
            bytes = Base64.decode(input, Base64.URL_SAFE | Base64.NO_WRAP);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String s) throws Exception {
        if (s == null) return "";
        return URLDecoder.decode(s, "UTF-8");
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }

    private static boolean isTrue(String s) {
        if (s == null) return false;
        String v = s.trim().toLowerCase(Locale.ROOT);
        return "1".equals(v) || "true".equals(v) || "yes".equals(v);
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) if (notEmpty(v)) return v;
        return "";
    }

    private static final class HostPort {
        final String host;
        final int port;
        HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
