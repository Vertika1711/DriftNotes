package com.driftnotes.app.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.driftnotes.app.R

class MapFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // TODO: Phase 3 - Member B - osmdroid map, drop pin, add-note dialog
        return inflater.inflate(R.layout.fragment_map, container, false)
    }
}
