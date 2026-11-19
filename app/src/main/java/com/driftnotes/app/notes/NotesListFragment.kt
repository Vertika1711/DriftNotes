package com.driftnotes.app.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.driftnotes.app.R

class NotesListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // TODO: Phase 3 - RecyclerView, Active/Done sections, edit dialog, mark done, delete
        return inflater.inflate(R.layout.fragment_notes_list, container, false)
    }
}
