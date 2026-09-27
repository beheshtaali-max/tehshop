XHTTP support uses the official Xray-core Android binaries.

For a local build, run:
  ./scripts/fetch_xray.sh

This downloads Xray-core v26.9.8 for:
  arm64-v8a
  armeabi-v7a

The binaries are intentionally not committed to the source archive.
The GitHub Actions release workflow downloads them automatically.
