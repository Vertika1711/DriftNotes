package com.driftnotes.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.driftnotes.app.databinding.ActivityMainBinding
import com.driftnotes.app.map.MapFragment
import com.driftnotes.app.notes.NotesListFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
