package net.openconnect_vpn.android.api;

oneway interface IOpenVPNStatusCallback {
    void newStatus(String uuid, String state, String message, String level);
}
