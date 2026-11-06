# Cisco / OpenConnect integration (TEH VPN 1.010)

This source tree includes the OpenConnect Android client engine from `ics-openconnect`
and its GPL license notice (`OPENCONNECT-COPYING`). Cisco VPN runs through Android's
`VpnService`; it cannot run in parallel with the app's V2Ray/SSH VPN service.

## Integration points
- `CiscoAdapter.kt`: starts/stops the OpenConnect service and reuses the saved IBSng credentials.
- `AuthFormHandler.java`: auto-fills username/password fields from TEH VPN; credentials are not written to OpenConnect preferences.
- `VpnAdapterFactory.kt` and `AppModule.kt`: register the Cisco adapter.
- `VpnOrchestrator.kt`: Cisco connection attempts time out after 30 seconds and are disconnected.
- `ServersViewModel.kt` / `ServersScreen.kt`: TCP ping check on the Cisco endpoint (default TCP port 443).
- `GrantPermissionsActivity`: permission prompt is guarded by a per-profile cancellation token so a timed-out request cannot start a late VPN tunnel.

## Build
The OpenConnect JNI libraries must be built for both `armeabi-v7a` and `arm64-v8a`.
On Ubuntu 24.04 with Android SDK platform 36, Android NDK r27c, Java 17, Apache Ant,
and the native build packages installed, run:

```bash
./scripts/build-openconnect-android.sh
./gradlew :app:assembleRelease
```

The GitHub Actions workflow `.github/workflows/build-release.yml` performs these steps
and uploads the release APK as an artifact. The wrapper JARs are included under
`app/libs`; the JNI `.so` files and the architecture-specific curl helper are generated
by the native build script.

## Important verification note
This environment did not have the Android SDK/NDK or a downloadable Gradle distribution,
so a complete Android build and on-device Cisco authentication/tunnel test could not be
run here. The source integration is prepared, but the APK must be built by the workflow
or an Android build host before installing it on a phone.
