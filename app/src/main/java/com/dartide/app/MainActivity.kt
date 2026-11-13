package com.dartide.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.dartide.app.ui.CodeFragment
import com.dartide.app.ui.PreviewFragment
import com.dartide.app.ui.SettingsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pager = findViewById<ViewPager2>(R.id.pager)
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> CodeFragment()
                1 -> PreviewFragment()
                else -> SettingsFragment()
            }
        }
        pager.offscreenPageLimit = 2
        pager.isUserInputEnabled = true

        nav.setOnItemSelectedListener { item ->
            pager.setCurrentItem(
                when (item.itemId) {
                    R.id.nav_preview -> 1
                    R.id.nav_settings -> 2
                    else -> 0
                },
                true
            )
            true
        }

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                nav.menu.getItem(position).isChecked = true
            }
        })
    }
}
