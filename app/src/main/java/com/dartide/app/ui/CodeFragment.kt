package com.dartide.app.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.dartide.app.R

class CodeFragment : Fragment(R.layout.fragment_code) {

    private var consoleOpen = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
    }
}
