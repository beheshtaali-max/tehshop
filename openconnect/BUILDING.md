# OpenConnect native build for TEH VPN

This module embeds the upstream ics-openconnect Android application engine under GPL-2.0-or-later.
The Java wrapper JARs and native `.so` files are generated from the included upstream `external/` sources.

On Linux, install Android SDK command-line tools, Android NDK r27c, GNU make, Apache Ant,
autoconf, automake, libtool, pkg-config, and the usual build essentials. Set `ANDROID_NDK_HOME`
to the NDK root, then run from the repository root:

```bash
./build-openconnect-native.sh
./gradlew :app:assembleDebug
```

The native build produces `openconnect/libs/*.jar`, `openconnect/src/main/jniLibs/{armeabi-v7a,arm64-v8a}/`,
and curl helper assets. The app supports the same two ABIs as its existing `abiFilters`.
Do not remove `openconnect/COPYING` or the upstream copyright headers.
