package net.openconnect_vpn.android.api;

import net.openconnect_vpn.android.api.APIVpnProfile;
import net.openconnect_vpn.android.api.IOpenVPNStatusCallback;
import android.content.Intent;

interface IOpenVPNAPIService {
    List<APIVpnProfile> getProfiles();
    void startProfile(String profileUUID);
    void startVPN(String inlineconfig);
    boolean addVPNProfile(String name, String config);
    Intent prepare(String packagename);
    Intent prepareVPNService();
    void registerStatusCallback(in IOpenVPNStatusCallback cb);
    void unregisterStatusCallback(in IOpenVPNStatusCallback cb);
    void disconnect();
}
