#!/usr/bin/env bash
# DartIDE toolchain spike, part 2:
# Build the OFFICIAL Dart SDK for Android (bionic, arm64) at the exact Dart
# version pinned by our Flutter release. Recipe verified from
# termux-packages packages/dart/build.sh:
#   ./tools/build.py --no-rbe --arch arm64 --mode release --os android create_sdk
# Output: bin/dart (aarch64 bionic ELF) + snapshots + SDK libs, tar.gz artifact.
set -uo pipefail

ROOT="$PWD"
OUT="$ROOT/toolchain/out"
WORK="$ROOT/work"  # note: fetch-flutter-web.sh already used toolchain/work for flutter
WORK="$ROOT/toolchain/work"
mkdir -p "$OUT"

DART_TAG="$(cat "$OUT/dart_version.txt" 2>/dev/null || true)"
if [ -z "$DART_TAG" ]; then
  echo "ERROR: $OUT/dart_version.txt missing — run fetch-flutter-web.sh first"
  exit 1
fi
echo "=== [1/5] Cloning dart-lang/sdk at tag $DART_TAG ==="
cd "$WORK"
if [ ! -d sdk ]; then
  git clone --depth 1 --branch "$DART_TAG" https://dart.googlesource.com/sdk.git sdk \
    || git clone --depth 1 --branch "$DART_TAG" https://github.com/dart-lang/sdk.git sdk
fi
cd sdk

echo "=== [2/5] depot_tools + gclient sync ==="
if [ ! -d "$HOME/depot_tools" ]; then
  git clone --depth 1 https://chromium.googlesource.com/chromium/tools/depot_tools.git "$HOME/depot_tools"
fi
export PATH="$HOME/depot_tools:$PATH"
gclient sync --jobs 4 || {
  echo "in-dir gclient sync failed, trying config route"
  cd "$WORK"
  [ -f .gclient ] || gclient config --name src https://dart.googlesource.com/sdk.git
  rm -rf src && mv sdk src
  gclient sync --jobs 4
  cd src
}

echo "=== [3/5] Android NDK check ==="
if [ ! -d third_party/android_ndk ]; then
  echo "third_party/android_ndk missing after sync — downloading NDK r27c"
  curl -fL --retry 3 -o /tmp/ndk.zip https://dl.google.com/android/repository/android-ndk-r27c-linux.zip
  rm -rf /tmp/ndk && mkdir -p /tmp/ndk
  unzip -q /tmp/ndk.zip -d /tmp/ndk
  mkdir -p third_party/android_ndk
  cp -r /tmp/ndk/android-ndk-r27c/. third_party/android_ndk/
fi

echo "=== [4/5] Building SDK for android-arm64 (this takes 1-2.5h) ==="
./tools/build.py --no-rbe --arch arm64 --mode release --os android create_sdk
SDK_DIR="$(ls -d out/*/dart-sdk | head -1)"
echo "SDK built at: $SDK_DIR"

echo "=== [5/5] Verify + package ==="
file "$SDK_DIR/bin/dart" | tee "$OUT/dart_binary_info.txt"
readelf -d "$SDK_DIR/bin/dart" | grep NEEDED | tee -a "$OUT/dart_binary_info.txt" || true
# Snapshot completeness: copy any host-side .snapshot data files missing from
# the android build (snapshots are platform-independent kernel binaries).
HOST_SDK="$WORK/flutter/bin/cache/dart-sdk"
if [ -d "$HOST_SDK/bin/snapshots" ]; then
  for f in "$HOST_SDK/bin/snapshots/"*.snapshot; do
    b="$(basename "$f")"
    if [ ! -f "$SDK_DIR/bin/snapshots/$b" ]; then
      cp "$f" "$SDK_DIR/bin/snapshots/$b"
      echo "copied missing snapshot: $b"
    fi
  done
fi
tar czf "$OUT/dartide-dart-sdk-android-arm64-$DART_TAG.tar.gz" -C "$(dirname "$SDK_DIR")" dart-sdk
du -h "$OUT"/*.tar.gz || true
echo "=== fetch-dart.sh DONE ==="
