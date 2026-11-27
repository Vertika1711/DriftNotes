package com.driftnotes.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.driftnotes.app.databinding.ActivityMainBinding
import com.driftnotes.app.geofence.NotificationHelper
import com.driftnotes.app.map.MapFragment
import com.driftnotes.app.notes.NotesListFragment
import com.driftnotes.app.permissions.PermissionsHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionsHelper: PermissionsHelper
    private var selectedTab = TAB_MAP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opt into edge-to-edge on API 26-34 (enforced automatically on 35+)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.createChannel(this)
        permissionsHelper = PermissionsHelper(this)

        // Apply system bar insets once, app-wide: status bar → root top padding,
        // navigation bar → BottomNavigationView bottom padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(0, bars.top, 0, 0)
            binding.bottomNav.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val tab = when (item.itemId) {
                R.id.nav_map -> TAB_MAP
                R.id.nav_notes -> TAB_NOTES
                else -> return@setOnItemSelectedListener false
            }
            showTab(tab)
            true
        }

        if (savedInstanceState == null) {
            val mapFragment = MapFragment()
            val notesFragment = NotesListFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, mapFragment, TAG_MAP)
                .add(R.id.fragment_container, notesFragment, TAG_NOTES)
                .hide(notesFragment)
                .commitNow()
        }

        selectedTab = when {
            intent.getStringExtra(EXTRA_OPEN_TAB) == TAB_NOTES -> TAB_NOTES
            savedInstanceState?.getString(STATE_SELECTED_TAB) == TAB_NOTES -> TAB_NOTES
            else -> TAB_MAP
        }
        showTab(selectedTab)
        binding.bottomNav.selectedItemId =
            if (selectedTab == TAB_NOTES) R.id.nav_notes else R.id.nav_map

        permissionsHelper.requestAll()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra(EXTRA_OPEN_TAB) == TAB_NOTES) {
            showTab(TAB_NOTES)
            binding.bottomNav.selectedItemId = R.id.nav_notes
        }
    }

    override fun onResume() {
        super.onResume()
        permissionsHelper.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED_TAB, selectedTab)
        super.onSaveInstanceState(outState)
    }

    private fun showTab(tab: String) {
        val mapFragment = supportFragmentManager.findFragmentByTag(TAG_MAP)
        val notesFragment = supportFragmentManager.findFragmentByTag(TAG_NOTES)
        if (mapFragment == null || notesFragment == null) return

        selectedTab = tab
        supportFragmentManager.beginTransaction().apply {
            if (tab == TAB_NOTES) {
                hide(mapFragment)
                show(notesFragment)
            } else {
                show(mapFragment)
                hide(notesFragment)
            }
        }.commitNow()
    }

    companion object {
        private const val EXTRA_OPEN_TAB = "open_tab"
        private const val STATE_SELECTED_TAB = "selected_tab"
        private const val TAB_MAP = "map"
        private const val TAB_NOTES = "notes"
        private const val TAG_MAP = "map_fragment"
        private const val TAG_NOTES = "notes_fragment"
    }
}
