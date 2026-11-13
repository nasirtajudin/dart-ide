#!/usr/bin/env bash
# DartIDE toolchain spike, part 1 (v2 — verified end-to-end against
# Flutter 3.47.9 / Dart 3.13.7 in a sandboxed CI-equivalent environment):
#   - download pinned Flutter stable (Linux archive)
#   - read the exact Dart version it pins (version lockstep source of truth)
#   - build a reference web build with the real flutter tool
#   - compile the SAME app DIRECTLY with two-stage dart2js, replicating
#     flutter_tools/lib/src/build_system/targets/web.dart:
#       * entrypoint is a bootstrap wrapper calling ui_web.bootstrapEngine,
#         NOT lib/main.dart
#       * defines: FLUTTER_WEB_USE_SKIA / FLUTTER_WEB_USE_SKWASM /
#         FLUTTER_WEB_CANVASKIT_URL (relative -> local canvaskit, offline)
#       * -O4 --minify (release parity)
#   - validate the direct build renders in headless Chromium (playwright)
#   - assemble the trimmed web SDK artifact our Android app will bundle
#
# v2 fixes for the three bugs that broke the first CI run:
#   1. flutter.js >= 3.24 REQUIRES the _flutter.buildConfig global to be set
#      before _flutter.loader.load(); the old load({config}) API is ignored.
#      index-direct.html is now a template; make-index.py injects the exact
#      buildConfig JSON extracted from the reference flutter_bootstrap.js.
#   2. compiling lib/main.dart directly leaves out the engine embedder glue
#      (dart:ui_web.bootstrapEngine) -> runtime StateError "The app requested
#      a view, but the platform did not provide one". smoke-test/bootstrap.dart
#      mirrors the wrapper flutter_tools generates for every web build.
#   3. Flutter 3.4x canvaskit defines are FLUTTER_WEB_USE_SKIA=true and
#      FLUTTER_WEB_USE_SKWASM=false (FLUTTER_WEB_AUTO_DETECT is obsolete).
set -euo pipefail

STAGE="init"
fail() { echo "!!! fetch-flutter-web.sh FAILED at stage [$STAGE]: $1" >&2; exit 1; }

ROOT="$PWD"
FLUTTER_VERSION="${FLUTTER_VERSION:-3.47.9}"
WORK="$ROOT/toolchain/work"
OUT="$ROOT/toolchain/out"
mkdir -p "$WORK" "$OUT"

STAGE="1/6 download flutter"
echo "=== [1/6] Downloading Flutter $FLUTTER_VERSION (Linux stable) ==="
cd "$WORK"
if [ ! -d flutter ]; then
  curl -fL --retry 3 -o flutter.tar.xz \
    "https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_${FLUTTER_VERSION}-stable.tar.xz" \
    || fail "flutter archive download"
  tar xf flutter.tar.xz || fail "flutter archive extract"
  rm -f flutter.tar.xz
fi
export FLUTTER_ROOT="$WORK/flutter"
FLUTTER="$FLUTTER_ROOT/bin/flutter"
git config --global --add safe.directory "$FLUTTER_ROOT" || true
"$FLUTTER" config --no-analytics >/dev/null 2>&1 || true
"$FLUTTER" --version || fail "flutter tool first run"

STAGE="2/6 read dart version"
echo "=== [2/6] Reading pinned Dart version ==="
DART_VERSION="$(tr -d '[:space:]' < "$FLUTTER_ROOT/bin/cache/dart-sdk/version")"
[ -n "$DART_VERSION" ] || fail "could not read dart version from dart-sdk/version"
echo "Dart version: $DART_VERSION"
echo "$DART_VERSION" > "$OUT/dart_version.txt"
echo "$FLUTTER_VERSION" > "$OUT/flutter_version.txt"
DART="$FLUTTER_ROOT/bin/cache/dart-sdk/bin/dart"
FWS="$FLUTTER_ROOT/bin/cache/flutter_web_sdk"

STAGE="3/6 reference build"
echo "=== [3/6] Reference build with real flutter tool ==="
rm -rf smoke-app
cp -r "$ROOT/toolchain/smoke-test" smoke-app
cd smoke-app
cp pubspec.yaml pubspec.yaml.bak
cp lib/main.dart main.dart.bak
"$FLUTTER" create --platforms web . >/dev/null 2>&1 || true
cp pubspec.yaml.bak pubspec.yaml
cp main.dart.bak lib/main.dart
rm -f pubspec.yaml.bak main.dart.bak
"$FLUTTER" pub get || fail "flutter pub get"
"$FLUTTER" build web --release || fail "reference flutter build web"
echo "Reference build done: $(ls build/web | tr '\n' ' ')"

STAGE="4/6 direct dart2js"
echo "=== [4/6] DIRECT two-stage dart2js compile (the on-device path) ==="
mkdir -p build/direct
D2JS_FLAGS="--platform-binaries=$FWS/kernel --invoker=flutter_tool -Ddart.vm.product=true -DFLUTTER_WEB_USE_SKIA=true -DFLUTTER_WEB_USE_SKWASM=false -DFLUTTER_WEB_CANVASKIT_URL=canvaskit/ --packages=.dart_tool/package_config.json -O4 --minify"
"$DART" compile js $D2JS_FLAGS -o build/direct/app.dill --cfe-only bootstrap.dart \
  || fail "dart2js CFE stage (bootstrap.dart)"
"$DART" compile js $D2JS_FLAGS -o build/direct/main.dart.js build/direct/app.dill \
  || fail "dart2js JS stage"
ls -la build/direct

STAGE="5/6 assemble + render check"
echo "=== [5/6] Assemble direct web bundle + headless Chromium smoke test ==="
rm -rf "$WORK/direct"
cp -r build/web "$WORK/direct"
rm -f "$WORK/direct/main.dart.js" "$WORK/direct/flutter_bootstrap.js" "$WORK/direct/flutter_service_worker.js"
cp build/direct/main.dart.js "$WORK/direct/main.dart.js"

# canvaskit must be bundled locally (offline requirement on the device);
# the reference build already ships it in build/web/canvaskit.
[ -d "$WORK/direct/canvaskit" ] || fail "canvaskit/ missing from reference build"

# flutter.js >= 3.24 needs the _flutter.buildConfig global: generate the
# bootstrap index.html from the reference flutter_bootstrap.js buildConfig.
python3 "$ROOT/toolchain/smoke-test/make-index.py" \
  "$ROOT/toolchain/smoke-test/index-direct.html" \
  build/web/flutter_bootstrap.js \
  "$WORK/direct/index.html" \
  "$WORK/direct/build_config.json" || fail "make-index.py (buildConfig extraction)"

python3 -m http.server 8099 --directory "$WORK/direct" >/dev/null 2>&1 &
SRV=$!
sleep 2
set +e
node "$ROOT/toolchain/smoke-test/check.js" "http://127.0.0.1:8099/index.html"
SMOKE_RC=$?
set -e
kill $SRV 2>/dev/null || true
echo "$SMOKE_RC" > "$OUT/smoke_result.txt"
[ "$SMOKE_RC" -eq 0 ] \
  || fail "headless render check rc=$SMOKE_RC (for a verbose console/network/page-error log, temporarily run smoke-test/debug-check.js instead of check.js)"

STAGE="6/6 artifact"
echo "=== [6/6] Assemble trimmed web SDK artifact ==="
ART="$OUT/dartide-web-sdk"
rm -rf "$ART" && mkdir -p "$ART/kernel" "$ART/flutter_js" "$ART/canvaskit" "$ART/fonts"
cp -r "$FWS/kernel/." "$ART/kernel/"
[ -d "$FWS/flutter_js" ] && cp -r "$FWS/flutter_js/." "$ART/flutter_js/"
cp "$FWS/libraries.json" "$ART/" 2>/dev/null || true
cp "$WORK/direct/build_config.json" "$ART/"
cp -r "$WORK/direct/canvaskit/." "$ART/canvaskit/" 2>/dev/null || true
cp -r build/web/assets/fonts/. "$ART/fonts/" 2>/dev/null || true
cp build/web/FontManifest.json "$ART/" 2>/dev/null || true
cp build/web/version.json "$ART/" 2>/dev/null || true
tar czf "$OUT/dartide-web-sdk-$DART_VERSION.tar.gz" -C "$OUT" dartide-web-sdk
rm -rf "$ART"
du -h "$OUT"/*.tar.gz || true

echo "=== fetch-flutter-web.sh DONE (smoke_rc=$SMOKE_RC) ==="
exit 0
