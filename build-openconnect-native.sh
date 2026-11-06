#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
OC="$ROOT/openconnect"
: "${ANDROID_NDK_HOME:=${ANDROID_NDK_ROOT:-}}"
if [[ -z "$ANDROID_NDK_HOME" || ! -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64" ]]; then
  echo "ERROR: Set ANDROID_NDK_HOME to Android NDK r27c (Linux)." >&2
  exit 2
fi
export ANDROID_NDK_HOME
command -v make >/dev/null || { echo "make is required" >&2; exit 2; }
command -v ant >/dev/null || { echo "Apache Ant is required to build the JNI wrapper JARs" >&2; exit 2; }
command -v autoconf >/dev/null || { echo "autoconf is required" >&2; exit 2; }
command -v automake >/dev/null || { echo "automake is required" >&2; exit 2; }
command -v libtoolize >/dev/null || { echo "libtool is required" >&2; exit 2; }
echo "Building OpenConnect JNI libraries and Java wrapper JARs for armeabi-v7a + arm64-v8a..."
make -C "$OC/external" install -j"$(nproc)"
echo "Native OpenConnect build completed."
