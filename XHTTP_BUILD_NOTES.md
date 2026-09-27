# XHTTP integration

This build keeps the existing sing-box/libcore engine for all existing protocols.
XHTTP is detected before config generation and routed through an Xray-core sidecar.

Runtime layout:

1. Android VpnService/TUN remains owned by sing-box/libcore.
2. Xray-core receives a generated VLESS + XHTTP config and listens on localhost SOCKS5.
3. sing-box forwards TUN traffic to that local SOCKS5 endpoint.

The project expects Xray Android binaries at:
- `v2ray/src/main/assets/xray/arm64-v8a/xray`
- `v2ray/src/main/assets/xray/armeabi-v7a/xray`

The official Xray project publishes Android arm64-v8a and other Android builds and documents Android compilation in its repository.
