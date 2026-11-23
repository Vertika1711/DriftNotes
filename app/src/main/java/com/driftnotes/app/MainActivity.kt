package com.driftnotes.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.driftnotes.app.databinding.ActivityMainBinding
import com.driftnotes.app.map.MapFragment
import com.driftnotes.app.notes.NotesListFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opt into edge-to-edge on API 26-34 (enforced automatically on 35+)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply system bar insets once, app-wide: status bar → root top padding,
        // navigation bar → BottomNavigationView bottom padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(0, bars.top, 0, 0)
            binding.bottomNav.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map -> { replaceFragment(MapFragment()); true }
                R.id.nav_notes -> { replaceFragment(NotesListFragment()); true }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            val tab = intent.getStringExtra("open_tab")
            binding.bottomNav.selectedItemId =
                if (tab == "notes") R.id.nav_notes else R.id.nav_map
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
