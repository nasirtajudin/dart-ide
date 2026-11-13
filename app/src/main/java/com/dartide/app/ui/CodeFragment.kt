package com.dartide.app.ui

import android.view.Gravity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.dartide.app.R
import com.dartide.app.editor.DartLanguage
import com.dartide.app.editor.ProjectStore
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.io.File

class CodeFragment : Fragment(R.layout.fragment_code) {

    private lateinit var editor: CodeEditor
    private lateinit var tabsRow: LinearLayout
    private var currentFile: File? = null
    private var consoleOpen = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Collapsible console drawer (unchanged from Phase 1)
        val body = view.findViewById<LinearLayout>(R.id.console_body)
        val chevron = view.findViewById<ImageView>(R.id.console_chevron)
        val header = view.findViewById<View>(R.id.console_header)
        header.setOnClickListener {
            consoleOpen = !consoleOpen
            body.visibility = if (consoleOpen) View.VISIBLE else View.GONE
            chevron.animate()
                .rotation(if (consoleOpen) 180f else 0f)
                .setDuration(150)
                .start()
        }

        // Phase 2: the real editor — sora-editor + custom Dart language
        editor = CodeEditor(requireContext())
        editor.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        ResourcesCompat.getFont(requireContext(), R.font.jetbrains_mono)?.let {
            editor.setTypefaceText(it)
        }
        editor.setTextSize(13f)
        editor.setTabWidth(2)
        editor.setLineNumberEnabled(true)
        editor.isWordwrap = false
        applyDarkPlusScheme()
        editor.setEditorLanguage(DartLanguage())
        view.findViewById<FrameLayout>(R.id.editor_container).addView(editor)

        ProjectStore.ensureProject(requireContext())
        tabsRow = view.findViewById(R.id.editor_tabs)
        openTab(ProjectStore.activeIndex.coerceAtLeast(0))

        view.findViewById<TextView>(R.id.console_text).text =
            "editor ready — ${ProjectStore.files.size} dart file(s) in lib/"
    }

    override fun onPause() {
        saveCurrent()
        super.onPause()
    }

    private fun saveCurrent() {
        val file = currentFile ?: return
        ProjectStore.write(file, editor.text.toString())
    }

    private fun openTab(index: Int) {
        if (index !in ProjectStore.files.indices) {
            currentFile = null
            editor.setText("")
            tabsRow.refreshTabs()
            return
        }
        if (currentFile != null) saveCurrent()
        val file = ProjectStore.files[index]
        currentFile = file
        ProjectStore.activeIndex = index
        editor.setText(ProjectStore.read(file))
        tabsRow.refreshTabs()
    }

    private fun closeFile(index: Int) {
        val file = ProjectStore.files.getOrNull(index) ?: return
        if (file == currentFile) {
            saveCurrent()
            currentFile = null
        }
        ProjectStore.delete(file)
        val next = if (ProjectStore.files.isEmpty()) {
            0
        } else {
            ProjectStore.activeIndex.coerceIn(0, ProjectStore.files.size - 1)
        }
        openTab(next)
    }

    private fun addUntitled() {
        val file = ProjectStore.addUntitled()
        openTab(ProjectStore.files.indexOf(file))
    }

    private fun LinearLayout.refreshTabs() {
        val ctx = context
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        removeAllViews()
        ProjectStore.files.forEachIndexed { index, file ->
            val tab = LinearLayout(ctx)
            tab.orientation = LinearLayout.HORIZONTAL
            tab.gravity = Gravity.CENTER_VERTICAL
            tab.setPadding(dp(12), 0, dp(12), 0)
            tab.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            val active = index == ProjectStore.activeIndex
            tab.setBackgroundResource(
                if (active) R.drawable.bg_tab_active else android.R.color.transparent
            )

            val name = TextView(ctx)
            name.text = file.name
            name.textSize = 13f
            name.setTextColor(ctx.getColor(if (active) R.color.vs_text_bright else R.color.vs_text_dim))
            tab.addView(name)

            val close = TextView(ctx)
            close.text = "×"
            close.textSize = 14f
            close.setPadding(dp(6), 0, 0, 0)
            close.setTextColor(ctx.getColor(R.color.vs_text_dim))
            close.setOnClickListener { closeFile(index) }
            tab.addView(close)

            tab.setOnClickListener { openTab(index) }
            addView(tab)
        }

        val add = TextView(ctx)
        add.text = "+"
        add.textSize = 16f
        add.gravity = Gravity.CENTER
        add.setTextColor(ctx.getColor(R.color.vs_text_dim))
        add.layoutParams = LinearLayout.LayoutParams(
            dp(36), LinearLayout.LayoutParams.MATCH_PARENT
        )
        add.setOnClickListener { addUntitled() }
        addView(add)
    }

    /** VS Code Dark+ token palette mapped onto sora color ids. */
    private fun applyDarkPlusScheme() {
        val s = editor.colorScheme
        s.setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFF1E1E1E.toInt())
        s.setColor(EditorColorScheme.TEXT_NORMAL, 0xFFD4D4D4.toInt())
        s.setColor(EditorColorScheme.LINE_NUMBER, 0xFF858585.toInt())
        s.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, 0xFFC6C6C6.toInt())
        s.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFF1E1E1E.toInt())
        s.setColor(EditorColorScheme.LINE_NUMBER_PANEL, 0xFF333333.toInt())
        s.setColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, 0xFFCCCCCC.toInt())
        s.setColor(EditorColorScheme.LINE_DIVIDER, 0xFF2B2B2B.toInt())
        s.setColor(EditorColorScheme.CURRENT_LINE, 0xFF282828.toInt())
        s.setColor(EditorColorScheme.SELECTION_HANDLE, 0xFF007ACC.toInt())
        s.setColor(EditorColorScheme.SELECTION_INSERT, 0xFF007ACC.toInt())
        s.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, 0xFF264F78.toInt())
        s.setColor(EditorColorScheme.KEYWORD, 0xFF569CD6.toInt())
        s.setColor(EditorColorScheme.ATTRIBUTE_VALUE, 0xFFC586C0.toInt())
        s.setColor(EditorColorScheme.IDENTIFIER_NAME, 0xFF4EC9B0.toInt())
        s.setColor(EditorColorScheme.IDENTIFIER_VAR, 0xFF9CDCFE.toInt())
        s.setColor(EditorColorScheme.FUNCTION_NAME, 0xFFDCDCAA.toInt())
        s.setColor(EditorColorScheme.LITERAL, 0xFFCE9178.toInt())
        s.setColor(EditorColorScheme.ANNOTATION, 0xFFB5CEA8.toInt())
        s.setColor(EditorColorScheme.COMMENT, 0xFF6A9955.toInt())
        s.setColor(EditorColorScheme.OPERATOR, 0xFFD4D4D4.toInt())
        s.setColor(EditorColorScheme.BLOCK_LINE, 0xFF404040.toInt())
        s.setColor(EditorColorScheme.BLOCK_LINE_CURRENT, 0xFF404040.toInt())
        s.setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, 0xFF252526.toInt())
        s.setColor(EditorColorScheme.COMPLETION_WND_CORNER, 0xFF3C3C3C.toInt())
        s.setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, 0xFFD4D4D4.toInt())
        s.setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, 0xFF858585.toInt())
        s.setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, 0xFF094771.toInt())
        s.setColor(EditorColorScheme.SCROLL_BAR_THUMB, 0xFF424242.toInt())
        s.setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, 0xFF5A5A5A.toInt())
    }
}
