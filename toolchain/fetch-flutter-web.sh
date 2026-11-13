#!/usr/bin/env bash
# DartIDE toolchain spike, part 1:
#  - download pinned Flutter stable (Linux archive)
#  - read the exact Dart version it pins (version lockstep source of truth)
#  - build a reference web build with the real flutter tool
#  - compile the SAME app DIRECTLY with two-stage dart2js, replicating
#    flutter_tools/lib/src/build_system/targets/web.dart (no flutter tool needed)
#  - validate the direct build renders in headless Chromium (playwright)
#  - assemble the trimmed web SDK artifact our Android app will bundle
set -uo pipefail

ROOT="$PWD"
FLUTTER_VERSION="${FLUTTER_VERSION:-3.47.9}"
WORK="$ROOT/toolchain/work"
OUT="$ROOT/toolchain/out"
mkdir -p "$WORK" "$OUT"

echo "=== [1/6] Downloading Flutter $FLUTTER_VERSION (Linux stable) ==="
cd "$WORK"
if [ ! -d flutter ]; then
  curl -fL --retry 3 -o flutter.tar.xz \
    "https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_${FLUTTER_VERSION}-stable.tar.xz"
  tar xf flutter.tar.xz
fi
export FLUTTER_ROOT="$WORK/flutter"
FLUTTER="$FLUTTER_ROOT/bin/flutter"
git config --global --add safe.directory "$FLUTTER_ROOT" || true
"$FLUTTER" config --no-analytics >/dev/null 2>&1 || true
"$FLUTTER" --version

echo "=== [2/6] Reading pinned Dart version ==="
DART_VERSION="$(grep -m1 '^version:' "$FLUTTER_ROOT/bin/cache/dart-sdk/VERSION" | sed 's/^version: \([0-9.]*\).*/\1/')"
echo "Dart version: $DART_VERSION"
echo "$DART_VERSION" > "$OUT/dart_version.txt"
echo "$FLUTTER_VERSION" > "$OUT/flutter_version.txt"
DART="$FLUTTER_ROOT/bin/cache/dart-sdk/bin/dart"

echo "=== [3/6] Reference build with real flutter tool ==="
rm -rf smoke-app
cp -r "$ROOT/toolchain/smoke-test" smoke-app
cp -r "$ROOT/toolchain/smoke-test/web" smoke-app/web 2>/dev/null || true
cd smoke-app
cp pubspec.yaml pubspec.yaml.bak
cp lib/main.dart main.dart.bak
"$FLUTTER" create --platforms web . >/dev/null 2>&1 || true
cp pubspec.yaml.bak pubspec.yaml
cp main.dart.bak lib/main.dart
rm -f pubspec.yaml.bak main.dart.bak
"$FLUTTER" pub get
"$FLUTTER" build web --release
echo "Reference build done: $(ls build/web | tr '\n' ' ')"

echo "=== [4/6] DIRECT two-stage dart2js compile (the on-device path) ==="
FWS="$FLUTTER_ROOT/bin/cache/flutter_web_sdk"
mkdir -p build/direct
"$DART" compile js \
  --platform-binaries="$FWS/kernel" \
  --invoker=dartide_tool \
  -Ddart.vm.product=true \
  -DFLUTTER_WEB_AUTO_DETECT=false \
  -DFLUTTER_WEB_CANVASKIT_URL=canvaskit/ \
  --packages=.dart_tool/package_config.json \
  -o build/direct/app.dill \
  --cfe-only \
  lib/main.dart
"$DART" compile js \
  --platform-binaries="$FWS/kernel" \
  --invoker=dartide_tool \
  -Ddart.vm.product=true \
  -DFLUTTER_WEB_AUTO_DETECT=false \
  -DFLUTTER_WEB_CANVASKIT_URL=canvaskit/ \
  --packages=.dart_tool/package_config.json \
  -o build/direct/main.dart.js \
  build/direct/app.dill
ls -la build/direct

echo "=== [5/6] Assemble direct web bundle + headless Chromium smoke test ==="
rm -rf "$WORK/direct"
cp -r build/web "$WORK/direct"
rm -f "$WORK/direct/main.dart.js" "$WORK/direct/flutter_bootstrap.js" "$WORK/direct/flutter_service_worker.js"
cp build/direct/main.dart.js "$WORK/direct/main.dart.js"
cp "$ROOT/toolchain/smoke-test/index-direct.html" "$WORK/direct/index.html"

# Make sure canvaskit is bundled locally (offline requirement)
if [ ! -d "$WORK/direct/canvaskit" ]; then
  CK_SRC="$(find "$FLUTTER_ROOT" -type f -name canvaskit.wasm | head -1 | xargs dirname 2>/dev/null || true)"
  if [ -n "$CK_SRC" ]; then cp -r "$CK_SRC" "$WORK/direct/canvaskit"; fi
fi
ls "$WORK/direct" || true

python3 -m http.server 8099 --directory "$WORK/direct" >/dev/null 2>&1 &
SRV=$!
sleep 2
node "$ROOT/toolchain/smoke-test/check.js" "http://127.0.0.1:8099/index.html"
SMOKE_RC=$?
kill $SRV 2>/dev/null || true
echo "$SMOKE_RC" > "$OUT/smoke_result.txt"

echo "=== [6/6] Assemble trimmed web SDK artifact ==="
ART="$OUT/dartide-web-sdk"
rm -rf "$ART" && mkdir -p "$ART/kernel" "$ART/flutter_js" "$ART/canvaskit" "$ART/fonts"
cp -r "$FWS/kernel/." "$ART/kernel/"
[ -d "$FWS/flutter_js" ] && cp -r "$FWS/flutter_js/." "$ART/flutter_js/"
cp -r "$WORK/direct/canvaskit/." "$ART/canvaskit/" 2>/dev/null || true
cp -r build/web/assets/fonts/. "$ART/fonts/" 2>/dev/null || true
cp build/web/FontManifest.json "$ART/" 2>/dev/null || true
cp build/web/version.json "$ART/" 2>/dev/null || true
tar czf "$OUT/dartide-web-sdk-$DART_VERSION.tar.gz" -C "$OUT" dartide-web-sdk
rm -rf "$ART"
du -h "$OUT"/*.tar.gz || true

echo "=== fetch-flutter-web.sh DONE (smoke_rc=$SMOKE_RC) ==="
exit $SMOKE_RC
