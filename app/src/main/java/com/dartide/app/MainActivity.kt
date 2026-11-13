package com.dartide.app

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.dartide.app.editor.RunBridge
import com.dartide.app.ui.CodeFragment
import com.dartide.app.ui.PreviewFragment
import com.dartide.app.ui.SettingsFragment

class MainActivity : AppCompatActivity() {

    private data class TabSpec(val item: Int, val icon: Int, val label: Int, val position: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Edge-to-edge: our chrome extends under the status bar and the
        // system navigation bar — same color all the way to the screen edges.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = findViewById<View>(R.id.root)
        val titleBar = findViewById<View>(R.id.title_bar)
        val bottomBar = findViewById<View>(R.id.bottom_bar)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            titleBar.updatePadding(top = bars.top)
            bottomBar.updatePadding(bottom = bars.bottom + bottomBarMargin())
            insets
        }

        // Morph-in entrance right after the splash screen dismisses
        root.alpha = 0f
        root.scaleX = 0.96f
        root.scaleY = 0.96f
        root.pivotY = 0f
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).start()

        val pager = findViewById<ViewPager2>(R.id.pager)
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> CodeFragment()
                1 -> PreviewFragment()
                else -> SettingsFragment()
            }
        }
        pager.offscreenPageLimit = 2
        // VS Code switches views by tap, not by swiping the whole surface —
        // keep horizontal gestures free for the editor and the console drawer.
        pager.isUserInputEnabled = false

        val tabs = listOf(
            TabSpec(R.id.tab_code, R.id.tab_code_icon, R.id.tab_code_label, 0),
            TabSpec(R.id.tab_preview, R.id.tab_preview_icon, R.id.tab_preview_label, 1),
            TabSpec(R.id.tab_settings, R.id.tab_settings_icon, R.id.tab_settings_label, 2)
        )

        fun select(position: Int) {
            pager.setCurrentItem(position, false)
            val accent = getColor(R.color.vs_accent)
            val dim = getColor(R.color.vs_text_dim)
            tabs.forEach { t ->
                val active = t.position == position
                findViewById<View>(t.item).setBackgroundResource(
                    if (active) R.drawable.bg_nav_active else android.R.color.transparent
                )
                findViewById<ImageView>(t.icon).imageTintList =
                    android.content.res.ColorStateList.valueOf(if (active) accent else dim)
                findViewById<TextView>(t.label).setTextColor(if (active) accent else dim)
            }
        }

        tabs.forEach { t ->
            findViewById<View>(t.item).setOnClickListener { select(t.position) }
        }
        select(0)

        // Title bar actions — RUN executes the active file on the on-device Dart VM.
        findViewById<View>(R.id.run_button).setOnClickListener {
            RunBridge.onRun?.invoke()
        }
        findViewById<View>(R.id.kebab_button).setOnClickListener {
            Toast.makeText(this, getString(R.string.toast_coming_soon), Toast.LENGTH_SHORT).show()
        }
    }

    private fun bottomBarMargin(): Int = (6 * resources.displayMetrics.density).toInt()
}
