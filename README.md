# Dart IDE

A fully offline Android IDE that runs real Dart code on-device (native bionic Dart VM,
no Linux/proot/Termux inside the app) and previews Flutter UI by compiling it with
dart2js + flutter_web_sdk and rendering it in a WebView with CanvasKit.

## Phases
| Phase | Deliverable |
|---|---|
| 0 | Repo scaffold, CI, toolchain spike (dart-for-android build + web sdk smoke test) |
| 1 | VS Code-style shell (Dark+ palette, 3-tab bottom nav) |
| 2 | Real editor (sora-editor + TextMate dart, dart analyze diagnostics) |
| 3 | Dart runtime + real terminal (pty, dart run, dart:io) |
| 4 | Packages (dart pub get, offline cache, needs-internet flow) |
| 5 | Flutter preview (direct dart2js + localhost server + WebView) |
| 6 | Polish + v1.0 release |

## Workflows
- `CI` — builds the debug APK on every push. Artifact: `DartIDE-debug-apk`.
- `Toolchain` — manual run. Builds the pinned Dart SDK for Android (arm64, bionic),
  assembles the trimmed Flutter web SDK, and proves the whole preview pipeline in
  headless Chromium before any app code depends on it. Artifact: `dartide-toolchain`.
