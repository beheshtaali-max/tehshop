package dev.dev7.lib.v2ray.model;

import java.io.Serializable;
import java.util.ArrayList;

public class V2rayConfigModel implements Serializable {
    public String applicationName;
    public int applicationIcon;
    public String remark;
    public ArrayList<String> blockedApplications = null;

    // With the old Xray library this was Xray JSON. In this sing-box version it is the final sing-box JSON.
    public String fullJsonConfig;

    // Raw input from panel: vless/vmess/trojan/ss link, xray json, sing-box json, ssh json/link.
    public String rawInputConfig;
    public String protocolMode = "auto"; // auto, v2ray, ssh

    public String currentServerAddress = "";
    public int currentServerPort = 443;

    // Kept for compatibility with old code. No local proxy mode is started anymore.
    public int localSocksPort = 10908;
    public int localHttpPort = 10809;
    public int localDNSPort = 1053;

    public boolean enableTrafficStatics = true;
    public boolean enableTrafficStaticsOnNotification = true;
    public boolean enableLocalTunneledDNS = true;
}
