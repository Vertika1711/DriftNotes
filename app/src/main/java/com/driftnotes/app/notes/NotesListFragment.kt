package com.driftnotes.app.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.driftnotes.app.R
import com.driftnotes.app.data.Note
import com.driftnotes.app.data.NoteRepository
import com.driftnotes.app.databinding.DialogEditNoteBinding
import com.driftnotes.app.databinding.FragmentNotesListBinding
import com.driftnotes.app.databinding.ItemNoteActiveBinding
import com.driftnotes.app.databinding.ItemNoteDoneBinding
import com.driftnotes.app.databinding.ItemNoteHeaderBinding
import com.driftnotes.app.geofence.GeofenceManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// List item types
// ---------------------------------------------------------------------------

private sealed class ListItem {
    data class Header(val label: String) : ListItem()
    data class NoteRow(val note: Note) : ListItem()
}

private val DIFF = object : DiffUtil.ItemCallback<ListItem>() {
    override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem) = when {
        oldItem is ListItem.Header && newItem is ListItem.Header -> oldItem.label == newItem.label
        oldItem is ListItem.NoteRow && newItem is ListItem.NoteRow -> oldItem.note.id == newItem.note.id
        else -> false
    }
    override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem) = oldItem == newItem
}

// ---------------------------------------------------------------------------
// Fragment
// ---------------------------------------------------------------------------

class NotesListFragment : Fragment() {

    private var _binding: FragmentNotesListBinding? = null
    private val binding get() = _binding!!

    private val repo by lazy { NoteRepository.get(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotesListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = NotesAdapter(
            onDone = ::markDone,
            onTap = ::showEditDialog,
            onLongPress = ::confirmDelete
        )

        binding.recyclerNotes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerNotes.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.observeAll().collect { notes ->
                    adapter.submitList(buildItems(notes))
                    binding.recyclerNotes.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
                    binding.textEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        // TODO: remove before PR
        binding.fabAddTestNote.setOnClickListener { addTestNote() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun buildItems(notes: List<Note>): List<ListItem> {
        val active = notes.filter { it.isActive }
        val done = notes.filter { !it.isActive }
        return buildList {
            if (active.isNotEmpty()) {
                add(ListItem.Header(getString(R.string.notes_section_active)))
                active.forEach { add(ListItem.NoteRow(it)) }
            }
            if (done.isNotEmpty()) {
                add(ListItem.Header(getString(R.string.notes_section_done)))
                done.forEach { add(ListItem.NoteRow(it)) }
            }
        }
    }

    private fun markDone(note: Note) {
        viewLifecycleOwner.lifecycleScope.launch {
            repo.update(note.copy(isActive = false))
            GeofenceManager(requireContext()).remove(note.id)
        }
    }

    private fun confirmDelete(note: Note) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.notes_delete_confirm)
            .setPositiveButton(R.string.notes_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.delete(note)
                    GeofenceManager(requireContext()).remove(note.id)
                }
            }
            .setNegativeButton(R.string.notes_cancel, null)
            .show()
    }

    private fun showEditDialog(note: Note) {
        val db = DialogEditNoteBinding.inflate(layoutInflater)

        db.editNoteText.setText(note.text)

        val minRadius = 100; val step = 50
        db.seekbarRadius.max = 8  // (500 - 100) / 50 = 8 steps → 100..500 m
        db.seekbarRadius.progress = ((note.radiusM.toInt() - minRadius) / step).coerceIn(0, 8)

        fun updateLabel(progress: Int) {
            db.textRadiusLabel.text = getString(R.string.notes_radius_label, minRadius + progress * step)
        }
        updateLabel(db.seekbarRadius.progress)
        db.seekbarRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) = updateLabel(p)
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.notes_edit_title)
            .setView(db.root)
            .setPositiveButton(R.string.notes_save, null)   // null → override below to block dismiss
            .setNegativeButton(R.string.notes_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = db.editNoteText.text?.toString()?.trim().orEmpty()
                if (text.isEmpty()) {
                    db.inputLayoutText.error = getString(R.string.notes_error_empty)
                    return@setOnClickListener
                }
                db.inputLayoutText.error = null
                val radius = (minRadius + db.seekbarRadius.progress * step).toFloat()
                val updated = note.copy(text = text, radiusM = radius)
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.update(updated)
                    if (updated.isActive) GeofenceManager(requireContext()).register(updated)
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // TODO: remove before PR
    private fun addTestNote() {
        viewLifecycleOwner.lifecycleScope.launch {
            val note = Note(
                text = "Test note at Trafalgar Square",
                lat = 51.5080,
                lng = -0.1281,
                radiusM = 200f
            )
            val id = repo.add(note)
            GeofenceManager(requireContext()).register(note.copy(id = id))
        }
    }
}

// ---------------------------------------------------------------------------
// Adapter
// ---------------------------------------------------------------------------

private class NotesAdapter(
    private val onDone: (Note) -> Unit,
    private val onTap: (Note) -> Unit,
    private val onLongPress: (Note) -> Unit
) : ListAdapter<ListItem, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ACTIVE = 1
        private const val TYPE_DONE   = 2
    }

    override fun getItemViewType(position: Int) = when (val item = getItem(position)) {
        is ListItem.Header  -> TYPE_HEADER
        is ListItem.NoteRow -> if (item.note.isActive) TYPE_ACTIVE else TYPE_DONE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(ItemNoteHeaderBinding.inflate(inf, parent, false))
            TYPE_ACTIVE -> ActiveVH(ItemNoteActiveBinding.inflate(inf, parent, false))
            else        -> DoneVH(ItemNoteDoneBinding.inflate(inf, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.Header  -> (holder as HeaderVH).bind(item.label)
            is ListItem.NoteRow -> when (holder) {
                is ActiveVH -> holder.bind(item.note, onDone, onTap, onLongPress)
                is DoneVH   -> holder.bind(item.note, onTap, onLongPress)
                else -> {}
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ViewHolders
// ---------------------------------------------------------------------------

private class HeaderVH(private val b: ItemNoteHeaderBinding) : RecyclerView.ViewHolder(b.root) {
    fun bind(label: String) { b.textHeader.text = label }
}

private class ActiveVH(private val b: ItemNoteActiveBinding) : RecyclerView.ViewHolder(b.root) {
    fun bind(note: Note, onDone: (Note) -> Unit, onTap: (Note) -> Unit, onLongPress: (Note) -> Unit) {
        b.textNote.text = note.text
        b.textMeta.text = metaText(note)
        b.btnDone.setOnClickListener { onDone(note) }
        b.root.setOnClickListener { onTap(note) }
        b.root.setOnLongClickListener { onLongPress(note); true }
    }
}

private class DoneVH(private val b: ItemNoteDoneBinding) : RecyclerView.ViewHolder(b.root) {
    fun bind(note: Note, onTap: (Note) -> Unit, onLongPress: (Note) -> Unit) {
        b.textNote.text = note.text
        b.textMeta.text = metaText(note)
        b.root.setOnClickListener { onTap(note) }
        b.root.setOnLongClickListener { onLongPress(note); true }
    }
}

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private fun metaText(note: Note) = "${note.radiusM.toInt()} m · ${dateFmt.format(Date(note.createdAt))}"
