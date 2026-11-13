package com.dartide.app.editor

import android.content.Context
import java.io.File

/**
 * On-device project model. Files live under filesDir/project/lib — the
 * standard Flutter layout, so the preview pipeline (Phase 4) can reuse it.
 */
object ProjectStore {

    lateinit var projectDir: File
        private set

    val files = mutableListOf<File>()
    var activeIndex = 0

    fun ensureProject(context: Context) {
        projectDir = File(context.filesDir, "project/lib")
        projectDir.mkdirs()
        val main = File(projectDir, "main.dart")
        if (!main.exists()) {
            copySample(context, main)
        }
        refresh()
        if (files.isEmpty()) {
            // The project must never end up empty.
            copySample(context, main)
            refresh()
        }
    }

    private fun copySample(context: Context, target: File) {
        context.assets.open("sample/main.dart").use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
    }

    fun refresh() {
        files.clear()
        val listed = projectDir.listFiles { f -> f.isFile && f.extension == "dart" }
        if (listed != null) files.addAll(listed.sortedBy { it.name })
        if (activeIndex >= files.size) activeIndex = files.size - 1
    }

    fun addUntitled(): File {
        var n = 0
        var f = File(projectDir, "untitled.dart")
        while (f.exists()) {
            n++
            f = File(projectDir, "untitled$n.dart")
        }
        f.writeText("// New Dart file\n")
        refresh()
        return f
    }

    fun delete(file: File): Boolean {
        val ok = file.delete()
        refresh()
        return ok
    }

    fun read(file: File): String = file.readText()

    fun write(file: File, content: String) {
        file.writeText(content)
    }
}
