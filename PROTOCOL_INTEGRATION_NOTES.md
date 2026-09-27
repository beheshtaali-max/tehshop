# Protocol integration notes

Added Kotlin VPN adapters while keeping the new project architecture:

- `BaseVpnAdapter.kt`: shared state flows, VPN permission handling, config decryption, saved user credentials, split-tunnel package list.
- `OpenVpnAdapter.kt`: OpenVPN config/user/pass startup and LocalBroadcast status mapping.
- `WireGuardAdapter.kt`: WireGuard GoBackend startup from decrypted config.
- `CiscoAdapter.kt`: OpenConnect/Cisco profile creation, credentials, blocked apps, status/stats updates.
- `Ikev2Adapter.kt`: strongSwan profile creation and VpnStateService state mapping.
- `SstpAdapter.kt`: SSTP host:port parsing, credentials, VpnBus status mapping.
- `VpnAdapterFactory.kt` and `VpnOrchestrator.kt`: dispatch each `ProtocolType` to the correct adapter.

Native `.so` / `jniLibs` files were not included in the uploaded source and must be restored before building protocol modules.
The original `:v2ray` module was also not included in the uploaded NEW project and should be restored by the owner.
