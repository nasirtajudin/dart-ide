package com.dartide.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.dartide.app.R

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = requireContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var fontSize = prefs.getInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE)

        val fontValue = view.findViewById<TextView>(R.id.font_size_value)
        fun renderFontSize() {
            fontValue.text = getString(R.string.font_size_value, fontSize)
        }

        view.findViewById<View>(R.id.stepper_minus).setOnClickListener {
            if (fontSize > MIN_FONT_SIZE) {
                fontSize -= 2
                persistFontSize(prefs, fontSize)
                renderFontSize()
            }
        }
        view.findViewById<View>(R.id.stepper_plus).setOnClickListener {
            if (fontSize < MAX_FONT_SIZE) {
                fontSize += 2
                persistFontSize(prefs, fontSize)
                renderFontSize()
            }
        }
        renderFontSize()

        view.findViewById<View>(R.id.row_theme).setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.toast_phase6), Toast.LENGTH_SHORT).show()
        }
        val phase2 = View.OnClickListener {
            Toast.makeText(requireContext(), getString(R.string.toast_phase2), Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.row_new_project).setOnClickListener(phase2)
        view.findViewById<View>(R.id.row_templates).setOnClickListener(phase2)
    }

    private fun persistFontSize(prefs: SharedPreferences, size: Int) {
        prefs.edit().putInt(PREF_FONT_SIZE, size).apply()
    }

    companion object {
        private const val PREFS_NAME = "dartide_prefs"
        private const val PREF_FONT_SIZE = "editor_font_size"
        private const val DEFAULT_FONT_SIZE = 14
        private const val MIN_FONT_SIZE = 10
        private const val MAX_FONT_SIZE = 28
    }
}
