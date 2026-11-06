#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXTERNAL="$ROOT/openconnect/external"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

if [[ -z "$NDK" || ! -d "$NDK/toolchains/llvm/prebuilt/linux-x86_64" ]]; then
  echo "ERROR: Android NDK r27c not found. Set ANDROID_NDK_HOME." >&2
  exit 2
fi

if ! command -v ant >/dev/null 2>&1; then
  echo "ERROR: Apache Ant is required." >&2
  exit 2
fi

make -C "$EXTERNAL" clean
make -C "$EXTERNAL" install NDK="$NDK"

for abi in armeabi-v7a arm64-v8a; do
  for lib in libopenconnect.so libstoken.so; do
    test -s "$ROOT/openconnect/src/main/jniLibs/$abi/$lib" || {
      echo "ERROR: Missing $abi/$lib" >&2
      exit 1
    }
  done

  test -s "$ROOT/openconnect/src/main/assets/raw/$abi/curl-bin" || {
    echo "ERROR: Missing $abi/curl-bin" >&2
    exit 1
  }
done

test -s "$ROOT/openconnect/libs/openconnect-wrapper.jar"
test -s "$ROOT/openconnect/libs/stoken-wrapper.jar"

echo "SUCCESS: OpenConnect native libraries, curl binaries and Java wrappers installed."
