# TEH VPN 1.010 — Cisco/OpenConnect integration

This source tree is based on the supplied TEH VPN 1.009 project. The app version is 1.010.

## Integrated changes
- Added the upstream `ics-openconnect` Android engine as an isolated `:openconnect` Android library module.
- Connected `ProtocolType.CISCO` to Koin and `VpnAdapterFactory`.
- Added the Cisco adapter using the existing IBSng credential store (`UserDataStore` / `BaseVpnAdapter.resolveCredentials`); no second username/password screen is needed for the ordinary username/password form.
- Added automatic OpenConnect form credential filling. Nonstandard extra authentication fields (OTP, group selection, etc.) remain interactive instead of being guessed.
- Added Cisco TCP ping in the server list and preserved Cisco ping for the active-server row.
- Kept Cisco in the existing protocol-aware Smart Connect flow (random available server for protocols without the V2Ray latency scan).
- Added a Cisco-specific 30-second connection timeout and cleanup of the service/profile after disconnect or timeout.
- Added a foreground notification for the OpenConnect VPN service on modern Android.
- Included the full upstream OpenConnect/stoken native source, curl source archive, GPL license, and a native build script.

## Required native build
The upstream Java API depends on generated JNI wrapper JARs and native libraries. They are not shipped as prebuilt binaries in the supplied OpenConnect archive, so they must be generated before Android Studio can compile the app.

On Linux, install Android SDK + Android NDK r27c and these build tools: `make`, `ant`, `autoconf`, `automake`, `libtool`, `pkg-config`, and build-essential. Then set `ANDROID_NDK_HOME` to the NDK r27c directory and run:

```bash
./build-openconnect-native.sh
./gradlew :app:assembleDebug
```

The script builds `armeabi-v7a` and `arm64-v8a`, matching the app's ABI filters.

## Build verification status
The source edits and XML checks were completed. An APK build could not be verified in the current build environment: the Android SDK/NDK are not installed, and Gradle 9.2.1 could not be downloaded because `services.gradle.org` did not resolve. Therefore this archive is the integrated source package, **not a claimed tested APK**.

Keep `openconnect/COPYING` and the upstream license headers when redistributing.
