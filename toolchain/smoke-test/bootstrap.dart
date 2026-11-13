// @dart=3.0
// DartIDE on-device preview bootstrap (smoke-test variant).
//
// Mirrors the entrypoint flutter_tools generates for every web build
// (.dart_tool/flutter_build/<hash>/main.dart). The engine glue lives in
// dart:ui_web.bootstrapEngine and only enters the compiled program via this
// wrapper — compiling lib/main.dart directly produces a JS build that throws
// "The app requested a view, but the platform did not provide one".
//
// The Android app's WebCompiler will generate the same file per project,
// with the import below rewritten to the project's package name.

import 'dart:async';
import 'dart:ui_web' as ui_web;

import 'package:dartide_smoke/main.dart' as entrypoint;

Future<void> main() async {
  await ui_web.bootstrapEngine(
    runApp: () {
      if (entrypoint.main is dynamic Function(List<String>)) {
        return (entrypoint.main as dynamic Function(List<String>))(<String>[]);
      }
      return (entrypoint.main as dynamic Function())();
    },
    registerPlugins: () {
      // DartIDE v1 preview: no native web plugins to register.
    },
  );
}
