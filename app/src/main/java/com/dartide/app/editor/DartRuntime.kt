package com.dartide.app.editor

import android.content.Context
import android.system.Os
import java.io.File
import java.util.zip.ZipInputStream

/** Lets MainActivity's RUN pill reach the live CodeFragment. */
object RunBridge {
    @Volatile var onRun: (() -> Unit)? = null
}

/**
 * The on-device Dart VM (Phase 3).
 *
 * assets/dartrt.zip is produced at build time by CI from the Termux aarch64
 * Dart package (3.14.x), trimmed to what `dart run` needs (~400 MB -> ~120 MB
 * extracted, ~33 MB zipped):
 *
 *   dart-sdk/bin/dart        launcher VM (boots dartdev_aot.dart.snapshot)
 *   dart-sdk/bin/dartvm      full JIT VM that dartdev spawns for user code
 *   dart-sdk/bin/dartaotruntime + bin/utils/gen_snapshot  (future AOT phase)
 *   dart-sdk/lib/            SDK libraries + vm_platform.dill
 *   symlinks.txt             "linkPath<TAB>target" manifest — java.util.zip
 *                            cannot recreate symlinks, so CI stores them as
 *                            text and we rebuild them here (targets are
 *                            relative to the dart-sdk root)
 *
 * Extraction lands in filesDir/runtime/. With targetSdk 28, exec() of
 * app-private binaries stays allowed on Android 10+.
 */
object DartRuntime {

    @Volatile private var current: Process? = null

    fun kill() {
        val p = current
        current = null
        try {
            p?.destroy()
        } catch (_: Exception) {
        }
    }

    /**
     * Extracts the runtime on first use. Returns null on success, else a
     * short error message. Safe to call from a background thread.
     */
    fun ensure(context: Context): String? {
        val rt = File(context.filesDir, "runtime")
        val bin = File(rt, "dart-sdk/bin/dart")
        if (bin.exists()) {
            bin.setExecutable(true, false)
            return null
        }
        try {
            rt.deleteRecursively()
            rt.mkdirs()

            // Pass 1: read the symlink manifest.
            val links = mutableMapOf<String, String>()
            context.assets.open("dartrt.zip").use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (e.name.removePrefix("./") == "symlinks.txt") {
                            zip.readBytes().toString(Charsets.UTF_8).lineSequence()
                                .filter { it.isNotBlank() }
                                .forEach { l ->
                                    val parts = l.split('\t', limit = 2)
                                    if (parts.size == 2) links[parts[0].trim()] = parts[1].trim()
                                }
                        }
                    }
                }
            }

            // Pass 2: extract everything except the manifest and link entries.
            context.assets.open("dartrt.zip").use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        val name = e.name.removePrefix("./")
                        if (name.isEmpty() || name == "symlinks.txt" || links.containsKey(name)) continue
                        val out = File(rt, name)
                        if (!out.canonicalPath.startsWith(rt.canonicalPath)) continue // zip-slip guard
                        if (e.isDirectory) {
                            out.mkdirs()
                            continue
                        }
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                }
            }

            // Pass 3: recreate the symlinks the zip carried as manifest lines.
            links.forEach { (path, target) ->
                val link = File(rt, path)
                link.parentFile?.mkdirs()
                link.delete()
                val resolved = if (target.startsWith("/")) {
                    File(target)
                } else {
                    File(File(rt, "dart-sdk"), target)
                }
                try {
                    Os.symlink(resolved.absolutePath, link.absolutePath)
                } catch (_: Exception) {
                }
            }

            if (!bin.exists()) return "runtime archive is missing dart-sdk/bin/dart"
            bin.setExecutable(true, false)
            return null
        } catch (e: Exception) {
            return "runtime setup failed: ${e.message}"
        }
    }

    /**
     * Runs `dart run <main>` with merged stdout/stderr streamed line by line.
     * Blocks until the program exits — call from a background thread.
     */
    fun run(context: Context, projectRoot: File, main: File, onLine: (String) -> Unit): Int {
        val rt = File(context.filesDir, "runtime")
        val bin = File(rt, "dart-sdk/bin/dart")
        val rel = main.relativeTo(projectRoot).path

        val pb = ProcessBuilder(bin.absolutePath, "run", rel)
        pb.directory(projectRoot)
        pb.redirectErrorStream(true)
        pb.environment().apply {
            put("HOME", context.filesDir.absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
            put("PUB_CACHE", File(context.cacheDir, "pub").absolutePath)
            put("PATH", File(rt, "dart-sdk/bin").absolutePath + ":/system/bin")
        }

        return try {
            val p = pb.start()
            current = p
            p.inputStream.bufferedReader().forEachLine { onLine(it) }
            val code = p.waitFor()
            current = null
            code
        } catch (e: Exception) {
            current = null
            onLine("dart runtime error: ${e.message}")
            -1
        }
    }
}
